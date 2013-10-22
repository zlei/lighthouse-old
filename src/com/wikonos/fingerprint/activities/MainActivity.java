/*
 * For the full copyright and license information, please view the LICENSE file that was distributed
 * with this source code. (c) 2011
 */

package com.wikonos.fingerprint.activities;

import com.wikonos.fingerprint.R;
import com.wikonos.logs.ErrorLog;
import com.wikonos.logs.LogWriter;
import com.wikonos.logs.LogWriterSensors;
import com.wikonos.network.AppLocationManager;
import com.wikonos.network.HttpLogSender;
import com.wikonos.utils.DataPersistence;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.Toast;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;

import org.apache.commons.lang3.ArrayUtils;

/**
 * Main application activity
 * 
 * @author Kuban Dzhakipov <kuban.dzhakipov@sibers.com>
 * @version SVN: $Id$
 */
public class MainActivity extends DefaultActivity {

	public static final String FLOOR_ID = "FLOOR_ID";

	public static final String FLOOR_NAME = "FLOOR_NAME";

	public static final String IMAGE_PATH = "IMAGE_PATH";

	/**
	 * Dialog ids
	 */

	private static final int DIALOG_ID_REVIEW = 2;

	private static final int DIALOG_ID_STATES = 1;

	private static final int DIALOG_ID_COUNTRIES = 3;

	protected String[] files;

	/**
	 * Create dialog list of logs
	 * 
	 * @return
	 */
	public AlertDialog getDialogReviewLogs() {
		/**
		 * List of logs
		 */
		File folder = new File(LogWriter.APPEND_PATH);
		final String[] files = folder.list(new FilenameFilter() {
			public boolean accept(File dir, String filename) {
				if (filename.contains(".log")
						&& !filename.equals(LogWriter.DEFAULT_NAME)
						&& !filename.equals(LogWriterSensors.DEFAULT_NAME)
						&& !filename.equals(ErrorLog.DEFAULT_NAME))
					return true;
				else
					return false;
			}
		});
		
		
		Arrays.sort(files);
		ArrayUtils.reverse(files);
		
		String[] files_with_status = new String[ files.length ];
		String[] sent_mode = { "", "(s) ", "(e) ", "(s+e) " };
		for(int i = 0; i < files.length; ++i) {
			//0 -- not sent
			//1 -- server
			//2 -- email
			files_with_status[i] = sent_mode[getSentFlags(files[i], this)] + files[i];
		}

		if (files != null && files.length > 0) {

			final boolean[] selected = new boolean[files.length];

			final AlertDialog ald = new AlertDialog.Builder(MainActivity.this)
					.setMultiChoiceItems(files_with_status, selected,
							new DialogInterface.OnMultiChoiceClickListener() {
								public void onClick(DialogInterface dialog,
										int which, boolean isChecked) {
									selected[which] = isChecked;
								}
							})
					.setOnCancelListener(new OnCancelListener() {
						@Override
						public void onCancel(DialogInterface dialog) {
							// removeDialog(DIALOG_ID_REVIEW);
						}
					})
					/**
					 * Delete log
					 */
					.setNegativeButton("Delete",
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int which) {

									//Show delete confirm
									standardConfirmDialog("Delete Logs"
											,"Are you sure you want to delete selected logs?"
											, new OnClickListener() {
												//Confrim Delete
												@Override
												public void onClick(DialogInterface dialog, int which) {
													
													int deleteCount = 0;
													boolean flagSelected = false;
													for (int i = 0; i < selected.length; i++) {
														if (selected[i]) {
															flagSelected = true;
															LogWriter.delete(files[i]);
															LogWriter.delete(files[i].replace(
																	".log", ".dev"));
															deleteCount++;
														}
													}

													reviewLogsCheckItems(flagSelected);
													
													removeDialog(DIALOG_ID_REVIEW);
													
													Toast.makeText(getApplicationContext(), deleteCount + " logs deleted.",Toast.LENGTH_SHORT).show();
												}
											}
											, new OnClickListener() {
												//Cancel Delete
												@Override
												public void onClick(DialogInterface dialog, int which) {
													//Do nothing
													dialog.dismiss();
													Toast.makeText(getApplicationContext(), "Delete cancelled.", Toast.LENGTH_SHORT).show();
												}
											}
											, false);
								}
							})
					/**
					 * Send to server functional
					 **/
					.setNeutralButton("Send to Server",
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int which) {
									if (isOnline()) {
										ArrayList<String> filesList = new ArrayList<String>();

										for (int i = 0; i < selected.length; i++)
											if (selected[i]) {
												
												filesList
														.add(LogWriter.APPEND_PATH
																+ files[i]);
												//Move to httplogsender
												//setSentFlags(files[i], 1, MainActivity.this);	//Mark file as sent
											}

										if (reviewLogsCheckItems(filesList
												.size() > 0 ? true : false)) {
											DataPersistence d = new DataPersistence(
													getApplicationContext());
											new HttpLogSender(
													MainActivity.this,
													d.getServerName()
															+ getString(R.string.submit_log_url),
													filesList).setToken(
													getToken()).execute();
										}

										// removeDialog(DIALOG_ID_REVIEW);
									} else {
										standardAlertDialog(
												getString(R.string.msg_alert),
												getString(R.string.msg_no_internet),
												null);
									}
								}
							})
					/**
					 * Email
					 **/
					.setPositiveButton("eMail",
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int which) {
									boolean flagSelected = false;
									// convert from paths to Android friendly
									// Parcelable Uri's
									ArrayList<Uri> uris = new ArrayList<Uri>();
									for (int i = 0; i < selected.length; i++)
										if (selected[i]) {
											flagSelected = true;
											/** wifi **/
											File fileIn = new File(
													LogWriter.APPEND_PATH
															+ files[i]);
											Uri u = Uri.fromFile(fileIn);
											uris.add(u);

											/** sensors **/
											File fileInSensors = new File(
													LogWriter.APPEND_PATH
															+ files[i].replace(
																	".log",
																	".dev"));
											Uri uSens = Uri
													.fromFile(fileInSensors);
											uris.add(uSens);
											
											setSentFlags(files[i], 2, MainActivity.this);	//Mark file as emailed
										}

									if (reviewLogsCheckItems(flagSelected)) {
										/**
										 * Run sending email activity
										 */
										Intent emailIntent = new Intent(
												android.content.Intent.ACTION_SEND_MULTIPLE);
										emailIntent.setType("plain/text");
										emailIntent
												.putExtra(
														android.content.Intent.EXTRA_SUBJECT,
														"Wifi Searcher Scan Log");
										emailIntent
												.putParcelableArrayListExtra(
														Intent.EXTRA_STREAM,
														uris);
										startActivity(Intent.createChooser(
												emailIntent, "Send mail..."));
									}

									// removeDialog(DIALOG_ID_REVIEW);
								}
							}).create();
							
							ald.getListView().setOnItemLongClickListener(new OnItemLongClickListener() {

								@Override
								public boolean onItemLongClick(AdapterView<?> parent, View view, final int position, long id) {

									AlertDialog segmentNameAlert = segmentNameDailog("Rename Segment", ald.getContext(), files[position], null, view, files, position);
									segmentNameAlert.setCanceledOnTouchOutside(false);
									segmentNameAlert.show();
									return false;
								}
							});
			return ald;
		} else {
			return standardAlertDialog(getString(R.string.msg_alert),
					getString(R.string.msg_log_nocount), new OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							removeDialog(DIALOG_ID_REVIEW);
						}
					});
		}
	}

	/**
	 * Create dialog for states list
	 * 
	 * @return
	 */
	public AlertDialog getDialogStates() {
		return new AlertDialog.Builder(MainActivity.this).setItems(
				getLocationManager().getStatesList(),
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {

						which *= 3;

						/**
						 * update active state
						 */
						getLocationManager().writeActiveOption(which);

						/**
						 * Update list of states with selected item
						 */
						removeDialog(DIALOG_ID_STATES);
					}
				}).create();
	}

	@Override
	public void onClickFeature(View v) {
		/**
		 * Checks SD Card mounted
		 */
		if (!Environment.getExternalStorageState().equals(
				Environment.MEDIA_MOUNTED)) {
			standardAlertDialog(getString(R.string.msg_alert),
					getString(R.string.msg_sd_card_disabled), null);
			return;
		}
		switch (v.getId()) {
		// select states
		case R.id.bSelectState:
			showDialog(DIALOG_ID_STATES);
			break;
		// review past scan log
		case R.id.bReview:
			//showDialog(DIALOG_ID_REVIEW);
			getDialogReviewLogs().show();
			break;
		default:
			super.onClickFeature(v);
		}
	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		switch (id) {
		case DIALOG_ID_STATES:
			return getDialogStates();
//		case DIALOG_ID_REVIEW:
//			return getDialogReviewLogs();
		}
		return super.onCreateDialog(id);
	}

	/*
	 * Review Logs. Validating Checking Items
	 */
	private boolean reviewLogsCheckItems(boolean flagSelected) {
		if (!flagSelected)
			standardAlertDialog(getString(R.string.msg_alert),
					getString(R.string.msg_nothing_checked), null);

		return flagSelected;
	}
	
	public static void setSentFlags(String filename, int mode, Context context) {
		SharedPreferences prefs = context.getSharedPreferences("REVIEW_SENT_PREFS", MODE_PRIVATE);
		//See if a flag is already there
		int flag = prefs.getInt(filename, 0);
		if(flag == 1 && mode == 2)mode = 3;	//Was sent, now emailed as well
		if(flag == 2 && mode == 1)mode = 3; //Was emailed, now sent as well
		prefs.edit().putInt(filename, mode).commit();
	}
	
	public static int getSentFlags(String filename, Context context) {
		SharedPreferences prefs = context.getSharedPreferences("REVIEW_SENT_PREFS", MODE_PRIVATE);
		return prefs.getInt(filename, 0);
	}
}

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
import com.wikonos.network.INetworkTaskStatusListener;
import com.wikonos.network.NetworkManager;
import com.wikonos.network.NetworkResult;
import com.wikonos.network.NetworkTask;
import com.wikonos.utils.AppActivityMediator;
import com.wikonos.utils.DataPersistence;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

/**
 * Abstract default activity
 * 
 * @author Kuban Dzhakipov <kuban.dzhakipov@sibers.com>
 * @version $Id$
 */
public class DefaultActivity extends Activity {

	/*
	 * Tag for logging application
	 */
	public static final String LOG_TAG = "FINGERPRINT";

	public static final String TAG_KEY = "TAG_KEY";

	public static final int INTENT_LOGIN_CODE = 100;

	protected AppActivityMediator mActivityMediator;

	protected SharedPreferences mPreferences;

	static protected WeakReference<AppLocationManager> mLocManager;

	public static String PREF_LOGIN_TOKEN = "login_token";

	public static String PREF_LOGIN_USERNAME = "login_username";

	public static String PREF_LOGIN_PASS = "login_password";
	
	public static String PREF_CUSTOMER_ID = "customer_id";

	public static String PREF_DEVELOPER_ID = "developer_id";	

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		DataPersistence d = new DataPersistence(this);
		NetworkTask.DEFAULT_BASE_URL = d.getServerName();

		mActivityMediator = new AppActivityMediator(this);

		mPreferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

		if (!hasToken() && !(this instanceof LoginActivity)) {
			startLoginActivity();
		}
	}

	protected void startLoginActivity() {
		startActivityForResult(new Intent(this, LoginActivity.class), INTENT_LOGIN_CODE);
	}

	/**
	 * Saves login token
	 * 
	 * @param token
	 */
	public void saveToken(String token) {
		mPreferences.edit().putString(PREF_LOGIN_TOKEN, token).commit();
	}

	/**
	 * Saves login information
	 * 
	 * @param token
	 * @param username
	 * @param pass
	 */
	public void saveToken(String token, String username, String pass) {
		mPreferences.edit().putString(PREF_LOGIN_TOKEN, token).putString(PREF_LOGIN_USERNAME, username)
		.putString(PREF_LOGIN_PASS, pass).commit();
	}

	/**
	 * Gets token
	 * 
	 * @return
	 */
	public String getToken() {
		return mPreferences.getString(PREF_LOGIN_TOKEN, "");
	}


	/**
	 * Has token
	 * 
	 * @return
	 */
	public boolean hasToken() {
		return mPreferences.contains(PREF_LOGIN_TOKEN);
	}

	public void eraseToken() {
		if (mPreferences.contains(PREF_LOGIN_TOKEN)) {
			mPreferences.edit().remove(PREF_LOGIN_TOKEN).commit();
		}
	}

	/**
	 * Gets Location Manager
	 * 
	 * @return
	 */
	public AppLocationManager getLocationManager() {
		if (mLocManager == null || mLocManager.get() == null) {
			mLocManager =
					new WeakReference<AppLocationManager>(new AppLocationManager(this, mPreferences));
		}
		return mLocManager.get();
	}

	/**
	 * Run home activity
	 * 
	 * @param v
	 */
	public void onClickHome(View v) {
		mActivityMediator.goHome();
	}

	/**
	 * Run required activity on click feature buttons
	 * 
	 * @param v
	 */
	public void onClickFeature(View v) {
		switch (v.getId()) {
		case R.id.bGetMap:
			if (isOnline()) {
				if (getLocationManager().getLongtitude() == 0 && getLocationManager().getLatitude() == 0
						&& !isGpsEnabled()) {
					standardAlertDialog(getString(R.string.msg_alert),
							getString(R.string.msg_gps_disabled), null);
					return;
				}

				mActivityMediator.goBuildings(WifiSearcherActivity.MODE.MAP);
			} else
				standardAlertDialog(getString(R.string.msg_alert), getString(R.string.msg_no_internet),
						null);
			break;
		case R.id.bScanLogs:
			if (isOnline())
				mActivityMediator.goBuildings(WifiSearcherActivity.MODE.MAP_POINTS);
			else
				standardAlertDialog(getString(R.string.msg_alert), getString(R.string.msg_no_internet),
						null);
			break;
		case R.id.bGoBack:
			onBackPressed();
			break;
		}
	}

	/**
	 * Sets title name
	 * 
	 * @param name
	 */
	public void setTitleName(String name) {
		TextView titleBarText = (TextView) findViewById(R.id.title_text);
		if (titleBarText != null) {
			titleBarText.setText(name);
		}
	}

	/**
	 * Init status to title bar progress bar
	 * 
	 * @param show
	 */
	public void initTitleProgressBar(boolean show) {
		ProgressBar bar = (ProgressBar) findViewById(R.id.loader);
		if (bar != null)
			bar.setVisibility(show ? View.VISIBLE : View.GONE);
	}

	/**
	 * Standard Alert Message Dialog
	 * 
	 * @param title
	 * @param message
	 * @param onClickListener
	 * @return
	 */
	public AlertDialog standardAlertDialog(String title, String message,
			DialogInterface.OnClickListener onClickListener) {
		try {
			AlertDialog alertDialog = new AlertDialog.Builder(this).create();
			alertDialog.setTitle(title);
			alertDialog.setButton("OK", onClickListener);
			alertDialog.setMessage(message);

			alertDialog.show();

			return alertDialog;
		} catch (Exception e) {
			// sometime it loses current window
			return null;
		}
	}

	/**
	 * Standard Confirm Dialog
	 * 
	 * @param title
	 * @param message
	 * @param onClickListener
	 * @return
	 */
	public AlertDialog standardConfirmDialog(String title, String message,
			DialogInterface.OnClickListener onClickListenerPositive, DialogInterface.OnClickListener onClickListenerNegative) {
		AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
		alertDialog.setTitle(title);
		alertDialog.setPositiveButton("YES", onClickListenerPositive);
		alertDialog.setNegativeButton("NO", onClickListenerNegative);
		alertDialog.setMessage(message);
		alertDialog.show();

		return alertDialog.create();
	}

	/**
	 * Standard Confirm Dialog 
	 * 
	 * @param title
	 * @param message
	 * @param onClickListener
	 * @return
	 */
	public AlertDialog standardConfirmDialog(String title, String message,
			DialogInterface.OnClickListener onClickListenerPositive, DialogInterface.OnClickListener onClickListenerNegative, boolean cancelable) {
		AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
		alertDialog.setTitle(title);
		alertDialog.setPositiveButton("YES", onClickListenerPositive);
		alertDialog.setNegativeButton("NO", onClickListenerNegative);
		alertDialog.setCancelable(cancelable);
		alertDialog.setMessage(message);
		alertDialog.show();

		return alertDialog.create();
	}
	
	public AlertDialog segmentNameDailog(String title, final Context context, final String existingFilename, final WifiSearcherActivity activity, final View row, final String[] files, final int files_index) {
		final AlertDialog.Builder alert = new AlertDialog.Builder(context);

		final LinearLayout view = new LinearLayout(context);
		final TextView datestamp = new TextView(context);
		final EditText segnum = new EditText(context);
		segnum.setInputType(InputType.TYPE_CLASS_NUMBER);
		final Spinner segmode = new Spinner(context);
		final EditText nameinput = new EditText(context);
		
		List<String> items = new ArrayList<String>();
		items.add("orig");
		items.add("mod");
		items.add("missing");
		items.add("new");
		items.add("a");
		items.add("b");
		items.add("c");
		items.add("d");
		items.add("e");
		ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items);
		dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		segmode.setAdapter(dataAdapter);
		segnum.setHint("#");
		nameinput.setHint("Name");
		TextView splitter = new TextView(this);
		splitter.setText("-");
		
		if(existingFilename == null){
			datestamp.setText(LogWriter.generateFilename()+"-");
			segmode.setSelection(0);
		}else{			
			String[] dat;
			if(existingFilename.contains(".")) {
				dat = existingFilename.substring(0, existingFilename.indexOf('.')).split("-");
			}else{
				dat = existingFilename.split("-");
			}
			datestamp.setText(dat[0] + "-");
			segnum.setText(dat[1].replaceAll("\\D+", ""));
			String mode = dat[1].replaceAll("[^A-Za-z]+", "");
			segmode.setSelection( (items.indexOf(mode) == -1) ? 0 : items.indexOf(mode) );
			nameinput.setText(dat[2]);
		}
		if(getResources().getConfiguration().orientation == 1) {
			LinearLayout horizontal = new LinearLayout(context);
			LinearLayout vertical = new LinearLayout(context);
			horizontal.setOrientation(LinearLayout.HORIZONTAL);
			vertical.setOrientation(LinearLayout.VERTICAL);
			
			horizontal.addView(datestamp);
			horizontal.addView(segnum);
			horizontal.addView(segmode);
			horizontal.addView(splitter);
			
			vertical.addView(horizontal);
			vertical.addView(nameinput);
			view.addView(vertical);
			

		}else{
			view.addView(datestamp);
			view.addView(segnum);
			view.addView(segmode);
			view.addView(splitter);
			view.addView(nameinput);	
		}
		
		alert.setView(view);
		alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				try {
					String value = "";
					String num = segnum.getText().toString().trim();
					String mode = segmode.getSelectedItem().toString().trim();
					String name = nameinput.getText().toString().trim().replaceAll("[^0-9a-zA-Z]", "_");
					//name = name.replace("π", "pi").replace("Π","pi");

					if(num.isEmpty()) {
						standardAlertDialog("Warning", "Please provide a segment # and save again.", null);
						num = "0";
					}
					
					if(name == "") name = "noname";
					if(mode == "orig") mode = "";
					
					value = datestamp.getText().toString().trim() 
						+ num
						+ mode
						+ "-"
						+ name;
					
					if(existingFilename == null) {
						//Update wifisearcheractivity if applicable
						if(activity != null)activity.mFilename = value;	
						LogWriter.instance().saveLog(value + ".log");
						LogWriterSensors.instance().saveLog(value + ".dev");
					}else{
						
						//Move file
						String oldfilename = existingFilename;
						if(existingFilename.contains(".")) {
							oldfilename = existingFilename.substring(0, existingFilename.indexOf("."));
						}
						File oldlogfile = new File(LogWriter.APPEND_PATH + "/" + oldfilename + ".log");
						File newlogfile = new File(LogWriter.APPEND_PATH + "/" + value + ".log");
						
						boolean logsuccess = oldlogfile.renameTo(newlogfile);
						
						File olddevfile = new File(LogWriter.APPEND_PATH + "/" + oldfilename + ".dev");
						File newdevfile = new File(LogWriter.APPEND_PATH + "/" + value + ".dev");
						
						boolean devsuccess = olddevfile.renameTo(newdevfile);
						
						if(devsuccess && logsuccess) {
							Toast.makeText(getApplicationContext(), "Renamed to " + newlogfile.getAbsolutePath(), Toast.LENGTH_SHORT).show();
						}else{
							Toast.makeText(getApplicationContext(), "Error renaming file.", Toast.LENGTH_SHORT).show();
						}

						//Update sent flags
						int currentFlags = MainActivity.getSentFlags(existingFilename, context);
						MainActivity.setSentFlags(existingFilename, 0, context);
						MainActivity.setSentFlags(value + ".log", currentFlags, context);
						
						
						if(activity != null)activity.mFilename = value;
						if(row != null){
							String[] sent_mode = { "", "(s) ", "(e) ", "(s+e) " };
							files[files_index] = value + ".log";
							((TextView)row).setText(sent_mode[currentFlags] + value + ".log");
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
					ErrorLog.e(e);
					Log.e("Error", "Unknown error");
					standardAlertDialog(getString(R.string.msg_error), getString(R.string.msg_error), null);
				}
			}
		});
		alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				dialog.cancel();
			}
		});
		return alert.create();
	}

	/**
	 * Internet connection is available or not
	 * 
	 * @return
	 */
	public boolean isOnline() {
		ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		if (cm.getActiveNetworkInfo() == null) {
			return false;
		}
		return cm.getActiveNetworkInfo().isConnectedOrConnecting();
	}

	/**
	 * Is Wi-Fi enabled
	 * 
	 * @param context
	 * @return
	 */
	public boolean isWifiAvailable() {
//		Log.d("Fingerprint", "Checking if wifi is available.");
		ConnectivityManager connectivityManager =
				(ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo networkInfo = null;
		if (connectivityManager != null) {
//			Log.d("Fingerprint", "Connectivity Manager is not null");
			networkInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
		}
//		Log.d("Fingerprint", "networkinfo null? " + (networkInfo == null));
//		Log.d("Fingerprint", "detailedstate " + (NetworkInfo.DetailedState)networkInfo.getDetailedState());
//		Log.d("Fingerprint", "state " + (NetworkInfo.State)networkInfo.getState());
//		Log.d("Fingerprint", "extrainfo " + networkInfo.getExtraInfo());
//		Log.d("Fingerprint", "isavailable" + networkInfo.isAvailable());
		return networkInfo == null ? false : networkInfo.isAvailable();
	}

	/**
	 * Is gps enabled
	 * 
	 * @return
	 */
	public boolean isGpsEnabled() {
		String provider =
				Settings.Secure.getString(getContentResolver(), Settings.Secure.LOCATION_PROVIDERS_ALLOWED);

		return provider.contains("gps");
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.system_logout:
			Hashtable<String, String> hash = new Hashtable<String, String>(3);
			hash.put("token", getToken());
			DataPersistence d = new DataPersistence(this);
			NetworkTask task = new NetworkTask(new INetworkTaskStatusListener() {

				@Override
				public void nTaskSucces(NetworkResult result) {
				}

				@Override
				public void nTaskErr(NetworkResult result) {
				}

			}, d.getServerName(), "/user/index/logout", false, hash, true);
			task.setTag(TAG_KEY, new Integer(LoginActivity.NETWORK_LOGIN));
			NetworkManager.getInstance().addTask(task);
			eraseToken();
			// required server request
			startActivity(new Intent(this, HomeActivity.class));
			finish();
			break;
		}
		return super.onOptionsItemSelected(item);
	}
	
	public SharedPreferences getLocalPreferences(){
		return mPreferences;
	}

/*	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		if (this instanceof LoginActivity) {
			menu.clear();
		}

		return super.onPrepareOptionsMenu(menu);
	}*/
}

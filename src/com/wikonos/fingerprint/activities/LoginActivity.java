/*
 * For the full copyright and license information, please view the LICENSE file that was distributed
 * with this source code. (c) 2011
 */

package com.wikonos.fingerprint.activities;

import com.wikonos.fingerprint.R;
import com.wikonos.network.INetworkTaskStatusListener;
import com.wikonos.network.NetworkManager;
import com.wikonos.network.NetworkResult;
import com.wikonos.network.NetworkTask;
import com.wikonos.utils.DataPersistence;

import android.content.Intent;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.util.Hashtable;

/**
 * LoginActivity
 * 
 * Logs user in application (uses soap service)
 * 
 * @author Kuban Dzhakipov <kuban.dzhakipov@sibers.com>
 * @version SVN: $Id$
 */
public class LoginActivity extends DefaultActivity implements
		INetworkTaskStatusListener {

	public static final int NETWORK_LOGIN = 1;

	protected EditText mLoginUsername;

	protected EditText mLoginPassword;

	protected EditText mLoginCustomerId;

	protected EditText mLoginDeveloperId;

	protected DataPersistence mData;;

	public boolean validateForm() {

		if (mData.getServerName().equals(getString(R.string.default_url))) {
			standardAlertDialog(getString(R.string.msg_alert),
					getString(R.string.msg_setup_host), null);
			return false;
		}

		if (mLoginCustomerId.getText().toString().equals("")) {
			standardAlertDialog(getString(R.string.msg_alert),
					getString(R.string.msg_customer_id_empty), null);
			return false;
		}

		if (mLoginDeveloperId.getText().toString().equals("")) {
			standardAlertDialog(getString(R.string.msg_alert),
					getString(R.string.msg_developer_id_empty), null);
			return false;
		}

		return true;
	}

	/**
	 * Makes authentification
	 * 
	 * @param v
	 */
	public void doLogin(View v) {

		if (!validateForm()) {
			return;
		}

		if (isOnline()) {

			initTitleProgressBar(true);

			Hashtable<String, String> hash = new Hashtable<String, String>(3);
			hash.put("login", mLoginUsername.getText().toString());
			hash.put("password", mLoginPassword.getText().toString());
			NetworkTask task = new NetworkTask(this, mData.getServerName(),
					"/user/index/login", false, hash, true);
			task.setTag(TAG_KEY, Integer.valueOf(NETWORK_LOGIN));
			NetworkManager.getInstance().addTask(task);

		} else {
			// need internet connection
			standardAlertDialog(getString(R.string.msg_alert),
					getString(R.string.msg_no_internet), null);
		}
	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.login);

		mLoginUsername = (EditText) findViewById(R.id.login_user);
		mLoginPassword = (EditText) findViewById(R.id.login_pass);
		mLoginCustomerId = (EditText) findViewById(R.id.login_customer_id);
		mLoginDeveloperId = (EditText) findViewById(R.id.login_developer_id);

		mData = new DataPersistence(this);

		mLoginUsername.setText(mPreferences.getString(PREF_LOGIN_USERNAME, ""));
		mLoginPassword.setText(mPreferences.getString(PREF_LOGIN_PASS, ""));

		mLoginCustomerId.setText(mPreferences.getString(PREF_CUSTOMER_ID, ""));
		mLoginDeveloperId
				.setText(mPreferences.getString(PREF_DEVELOPER_ID, ""));

	}

	@Override
	public void nTaskErr(NetworkResult result) {
		initTitleProgressBar(false);

		if (result.getResponceCode() == 401)
			standardAlertDialog(getString(R.string.msg_error),
					getString(R.string.msg_login_incorrect), null);
		else {
			standardAlertDialog(getString(R.string.msg_error),
					getString(R.string.msg_teh_error), null);
			Log.e(LOG_TAG, "error", result.getException());
		}
	}

	@Override
	public void nTaskSucces(NetworkResult result) {
		initTitleProgressBar(false);

		switch ((Integer) (result.getTask().getTag(TAG_KEY))) {
		case NETWORK_LOGIN:
			try {
				BufferedReader buff = new BufferedReader(new InputStreamReader(
						new ByteArrayInputStream(result.getData())));
				StringBuffer strBuff = new StringBuffer();

				String s;

				while ((s = buff.readLine()) != null) {
					strBuff.append(s);
				}

				String loginToken = strBuff.toString();
				saveToken(loginToken, mLoginUsername.getText().toString(),
						mLoginPassword.getText().toString());
				Editor editor = getLocalPreferences().edit();
				editor.putString(PREF_CUSTOMER_ID, mLoginCustomerId.getText()
						.toString());
				editor.putString(PREF_DEVELOPER_ID, mLoginDeveloperId.getText()
						.toString());
				editor.commit();
				finish();
			} catch (Exception e) {
				standardAlertDialog(getString(R.string.msg_error),
						getString(R.string.msg_teh_error), null);

			}
			break;
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		Log.i("MENU", "CREATER");
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.settings_menu, menu);
		Log.i("MENU", "Created");
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Log.i("MENU", "Selected");
		switch (item.getItemId()) {
		case R.id.settings_button:
			startActivity(new Intent(this, SettingsActivity.class));
			break;
		}
		return super.onOptionsItemSelected(item);
	}

	/**
	 * Back pressed off
	 */
	@Override
	public void onBackPressed() {
		moveTaskToBack(true);
	}
}
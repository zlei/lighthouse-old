package com.wikonos.fingerprint.activities;

import com.wikonos.fingerprint.R;
import com.wikonos.utils.DataPersistence;

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

public class SettingsActivity extends Activity {

	public EditText serverAddressEditText;
	private static final int TEST = 110;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.settings_layout);

		DataPersistence d = new DataPersistence(getApplicationContext());

		serverAddressEditText = (EditText)findViewById(R.id.addressEditText);
		serverAddressEditText.addTextChangedListener(addressText);
		serverAddressEditText.setText(d.getServerName());
		serverAddressEditText.onEditorAction(TEST);
	}
	
	OnEditorActionListener listener = new OnEditorActionListener() {
		
		@Override
		public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
			
			if (TEST == EditorInfo.IME_ACTION_GO) {
				//Editable newTxt=(Editable)s;
				//afterTextChanged(newTxt);
			}
			return false;
		}
	};

	TextWatcher addressText = new TextWatcher() {

		@Override
		public void onTextChanged(CharSequence s, int start, int before, int count) { }

		@Override
		public void beforeTextChanged(CharSequence s, int start, int count,
				int after) { }

		@Override
		public void afterTextChanged(Editable s) {
			DataPersistence d = new DataPersistence(getApplicationContext());
			d.setServerName(serverAddressEditText.getText().toString());
		}
	};
}

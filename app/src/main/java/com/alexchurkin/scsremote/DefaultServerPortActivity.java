package com.alexchurkin.scsremote;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;

public class DefaultServerPortActivity extends Activity implements
		OnClickListener {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.defaultserverport);
		stickOrientation();
		Button b = (Button) findViewById(R.id.defaultServerPortButton);
		b.setOnClickListener(this);
		EditText et = (EditText) findViewById(R.id.defaultServerPortET);
		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(this.getApplicationContext());
		et.setText(prefs.getString("serverPort", "18250"));
	}

	public void stickOrientation() {
		WindowManager mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
		Display mDisplay = mWindowManager.getDefaultDisplay();
		Log.d("ORIENTATION", "" + mDisplay.getOrientation());
		switch (mDisplay.getOrientation()) {
		case 0:
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
			break;
		case 1:
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
			break;
		}
	}

	public void onClick(View v) {
		try {
			EditText et = (EditText) findViewById(R.id.defaultServerPortET);
			int i = Integer.parseInt(et.getText().toString());
			if (i < 65536 && i > 0) {
				SharedPreferences prefs = PreferenceManager
						.getDefaultSharedPreferences(this
								.getApplicationContext());
				SharedPreferences.Editor editor = prefs.edit();
				editor.putString("serverPort", "" + i);
				editor.commit();
				finish();
			} else {

			}
		} catch (Exception e) {

		}
	}
}

package com.alexchurkin.scsremote;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;

public class DefaultServerIPActivity extends Activity implements
        OnClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.defaultserverip);
        stickOrientation();
        Button b = (Button) findViewById(R.id.setServerIPButton);
        b.setOnClickListener(this);
        try {
            SharedPreferences prefs = PreferenceManager
                    .getDefaultSharedPreferences(this.getApplicationContext());
            String serverIP = prefs.getString("serverIP", "");
            EditText dip1 = (EditText) findViewById(R.id.dip1);
            EditText dip2 = (EditText) findViewById(R.id.dip2);
            EditText dip3 = (EditText) findViewById(R.id.dip3);
            EditText dip4 = (EditText) findViewById(R.id.dip4);
            String ip1 = serverIP.substring(0, serverIP.indexOf('.'));
            serverIP = serverIP.substring(ip1.length() + 1);
            String ip2 = serverIP.substring(0, serverIP.indexOf('.'));
            serverIP = serverIP.substring(ip2.length() + 1);
            String ip3 = serverIP.substring(0, serverIP.indexOf('.'));
            serverIP = serverIP.substring(ip3.length() + 1);
            String ip4 = serverIP;
            dip1.setText(ip1);
            dip2.setText(ip2);
            dip3.setText(ip3);
            dip4.setText(ip4);
        } catch (Exception e) {

        }
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
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(this.getApplicationContext());
        SharedPreferences.Editor editor = prefs.edit();

        EditText ip1 = (EditText) findViewById(R.id.dip1);
        EditText ip2 = (EditText) findViewById(R.id.dip2);
        EditText ip3 = (EditText) findViewById(R.id.dip3);
        EditText ip4 = (EditText) findViewById(R.id.dip4);

        editor.putString("serverIP", ip1.getText().toString() + "."
                + ip2.getText().toString() + "." + ip3.getText().toString()
                + "." + ip4.getText().toString());
        editor.commit();
        finish();
    }
}

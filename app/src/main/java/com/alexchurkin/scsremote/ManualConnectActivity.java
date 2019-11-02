package com.alexchurkin.scsremote;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class ManualConnectActivity extends Activity implements OnClickListener {

    public static boolean configured = false;
    public static String ipAddress;
    public static int port;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.manuallyconnect);
        Button b = findViewById(R.id.connectButton);
        b.setOnClickListener(this);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.getApplicationContext());
        String serverIP = prefs.getString("serverIP", "");
        try {
            String ip1 = serverIP.substring(0, serverIP.indexOf('.'));
            serverIP = serverIP.substring(ip1.length() + 1);
            String ip2 = serverIP.substring(0, serverIP.indexOf('.'));
            serverIP = serverIP.substring(ip2.length() + 1);
            String ip3 = serverIP.substring(0, serverIP.indexOf('.'));
            serverIP = serverIP.substring(ip3.length() + 1);
            String ip4 = serverIP;
            Log.d("serverIP", ip1 + "." + ip2 + "." + ip3 + "." + ip4);
            EditText ip1ET = findViewById(R.id.ip1);
            EditText ip2ET = findViewById(R.id.ip2);
            EditText ip3ET = findViewById(R.id.ip3);
            EditText ip4ET = findViewById(R.id.ip4);
            ip1ET.setText(ip1);
            ip2ET.setText(ip2);
            ip3ET.setText(ip3);
            ip4ET.setText(ip4);
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            int port = Integer.parseInt(prefs.getString("serverPort", "18250"));
            EditText portET = findViewById(R.id.portET);
            portET.setText("" + port);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void onClick(View v) {
        EditText ip1 = findViewById(R.id.ip1);
        EditText ip2 = findViewById(R.id.ip2);
        EditText ip3 = findViewById(R.id.ip3);
        EditText ip4 = findViewById(R.id.ip4);
        try {
            ipAddress = Integer.parseInt(ip1.getText().toString()) + "."
                    + Integer.parseInt(ip2.getText().toString()) + "."
                    + Integer.parseInt(ip3.getText().toString()) + "."
                    + Integer.parseInt(ip4.getText().toString());
            port = Integer.parseInt(((EditText) findViewById(R.id.portET))
                    .getText().toString());
            if (port < 1 || port > 65535)
                throw new Exception();
            configured = true;
            this.finish();
        } catch (Exception e) {
            showToast("Error: Invalid parameters.", Toast.LENGTH_SHORT);
        }
    }

    private void showToast(String string, int duration) {
        Toast toast = Toast.makeText(this, string, duration);
        toast.setGravity(Gravity.TOP, 0, 20);
        toast.show();
    }
}

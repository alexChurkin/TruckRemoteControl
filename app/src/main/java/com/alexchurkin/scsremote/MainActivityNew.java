package com.alexchurkin.scsremote;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.constraintlayout.widget.ConstraintLayout;

public class MainActivityNew extends AppCompatActivity implements
        SensorEventListener,
        View.OnClickListener,
        View.OnTouchListener {

    private SensorManager mSensorManager;
    public WifiManager wifi;
    private Sensor mSensor;

    private AppCompatImageButton mConnectionIndicator;
    private ConstraintLayout mBreakLayout, mGasLayout;

    private AccelerometerMouseClient client;
    public static int dBm = -200;
    private final static double compression = 0.75;
    private boolean defaultServer = false, invertX = false, invertY = false, deadZone = false, tablet = false, changingOr = false, justChangedOr = false;
    private int defaultServerPort = 18250, number = 1280, zero = 22;
    private String defaultServerIp = "shit";
    public static MouseButton leftButton = new MouseButton(), rightButton = new MouseButton();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        setContentView(R.layout.activity_main);
        mConnectionIndicator = findViewById(R.id.connectionIndicator);
        mBreakLayout = findViewById(R.id.breakLayout);
        mGasLayout = findViewById(R.id.gasLayout);

        mBreakLayout.setOnTouchListener(this);
        mGasLayout.setOnClickListener(this);
        mConnectionIndicator.setOnClickListener(this);

        makeFullscreen();
        updatePrefs();

        client = new AccelerometerMouseClient("Bullshit", 18250);
        dBm = getSignalStrength();

        if (wifi.isWifiEnabled() && !AccelerometerMouseClient.running && !justChangedOr) {
            if (!defaultServer) {
                showToast("Searching for servers on the local network...");
                client.run(false);
            } else {
                if (defaultServerIp.equals("shit")) {
                    showToast("Failed to connect to default server. None defined.");
                } else {
                    showToast("Attempting to connect to " + defaultServerIp + "...");
                    client.forceUpdate(defaultServerIp, defaultServerPort);
                    client.run(true);
                }
            }
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.connectionIndicator:
                if (wifi.isWifiEnabled())
                    showToast("Wi-Fi signal strength: " + getSignalStrength() + "dBm");
                break;
            case R.id.breakLayout:
                leftButton.setPressed(true);
                break;
            case R.id.gasLayout:
                rightButton.setPressed(true);
                //TODO2
                break;
        }
    }

    @Override
    public boolean onTouch(View view, MotionEvent event) {
        switch (view.getId()) {
            case R.id.breakLayout:
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    leftButton.setPressed(true);
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    leftButton.setPressed(false);
                }
                break;
            case R.id.gasLayout:
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    rightButton.setPressed(true);
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    rightButton.setPressed(false);
                }
                break;
        }
        client.feedTouchFlags(leftButton.isPressed(), rightButton.isPressed());
        return false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        leftButton.setPressed(false);
        rightButton.setPressed(false);
        updatePrefs();
        client.pause(false);
        mSensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_FASTEST);
        if (ManualConnectActivity.configured) {
            client.stop();
            client.forceUpdate(ManualConnectActivity.ipAddress, ManualConnectActivity.port);
            showToast("Attempting to connect to " + ManualConnectActivity.ipAddress + " on port " + ManualConnectActivity.port);
            client.run(true);
            ManualConnectActivity.configured = false;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this, mSensor);
        client.pause(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        client.stop();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.getApplicationContext());
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("justChangedOr", changingOr);
        if (!changingOr)
            editor.putString("lastServer", "");
        editor.apply();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            makeFullscreen();
        }
    }

    public void onSensorChanged(SensorEvent event) {
        try {
            float x = invertX ? (-event.values[0] + 9.81f) : event.values[0];
            float y = invertY ? (-event.values[1]) : event.values[1];
            float z = event.values[2];
            // Screen flipping and whatnot
            {
                if (tablet) {
                    float dummyX = x;
                    x = y;
                    y = -dummyX;
                }

                switch (zero) {
                    case 0:
                        x += 4.9;
                        break;
                    case 22:
                        x += 4.9 / 2;
                        break;
                }
            }
            if (deadZone) {
                x = applyDeadZoneX(x);
                y = applyDeadZoneY(y);
            }
            client.feedAccelerometerValues(x, y, z);
            // Show connected toast if connected
            if (!AccelerometerMouseClient.toastShown) {
                if (AccelerometerMouseClient.connected) {
                    showToast("Connected to server at " + client.socket.getInetAddress().getHostAddress());
                } else {
                    showToast("Lost connection to server!");
                }
                AccelerometerMouseClient.toastShown = true;
            }
        } catch (Exception ignore) {
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
    }

    public float applyDeadZoneX(float x) {
        if (x < 5.8 && x > 3.2) {
            x = 4.90f;
        }
        return x;
    }

    public float applyDeadZoneY(float y) {
        if (y > -.98 && y < .98) {
            y = 0f;
        }
        return y;
    }

    private void updatePrefs() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        defaultServer = prefs.getBoolean("defaultServer", false);
        invertX = prefs.getBoolean("invertX", false);
        invertY = prefs.getBoolean("invertY", false);
        deadZone = prefs.getBoolean("deadZone", false);
        tablet = prefs.getBoolean("tablet", false);
        justChangedOr = prefs.getBoolean("justChangedOr", false);
        defaultServerIp = prefs.getString("serverIP", "shit");
        zero = Integer.parseInt(prefs.getString("zeroPosition", "22"));
        defaultServerPort = Integer.parseInt(prefs.getString("serverPort", "18250"));
    }

    private void makeFullscreen() {
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.KEEP_SCREEN_ON;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            flags = flags | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        }

        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    private void updateConnectionIndicatorState(boolean isConnected) {
        if (isConnected) {
            mConnectionIndicator.setImageResource(R.drawable.connection_indicator_green);
        } else {
            mConnectionIndicator.setImageResource(R.drawable.connection_indicator_red);
        }
    }

    public int getSignalStrength() {
        try {
            if (!wifi.isWifiEnabled()) {
                showToast("No Wi-Fi connection detected!");
            }
            return wifi.getConnectionInfo().getRssi();
        } catch (Exception ignore) {

        }
        return -50;
    }

    private void showToast(String string) {
        Toast toast = Toast.makeText(this, string, Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.TOP | Gravity.END, 0, 0);
        toast.show();
    }
}

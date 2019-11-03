package com.alexchurkin.scsremote;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.constraintlayout.widget.ConstraintLayout;

public class MainActivity extends AppCompatActivity implements
        SensorEventListener,
        View.OnClickListener,
        View.OnTouchListener,
        AccelerometerClient.ConnectionListener {

    public static WifiManager wifi;
    public static ControllerButton breakButton = new ControllerButton();
    public static ControllerButton gasButton = new ControllerButton();
    public static int dBm = -200;

    private SensorManager mSensorManager;
    private Sensor mSensor;
    private Handler mHandler;

    private ConstraintLayout rootView;
    private AppCompatImageButton mConnectionIndicator, mPauseButton, mSettingsButton;
    private AppCompatImageButton mLeftSignalButton, mRightSignalButton, mAllSignalsButton;
    private ConstraintLayout mBreakLayout, mGasLayout;

    private AccelerometerClient client;
    private boolean defaultServer = false, invertX = false, invertY = false, deadZone = false, tablet = false, changingOr = false, justChangedOr = false;
    private int defaultServerPort = 18250, zero = 22;
    private String defaultServerIp = "shit";

    private boolean isConnected;

    private boolean previousSignalGreen;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mHandler = new Handler();

        setContentView(R.layout.activity_main);
        rootView = findViewById(R.id.rootView);

        mConnectionIndicator = findViewById(R.id.connectionIndicator);
        mPauseButton = findViewById(R.id.pauseButton);
        mSettingsButton = findViewById(R.id.settingsButton);

        mLeftSignalButton = findViewById(R.id.buttonLeftSignal);
        mRightSignalButton = findViewById(R.id.buttonRightSignal);
        mAllSignalsButton = findViewById(R.id.buttonAllSignals);

        mBreakLayout = findViewById(R.id.breakLayout);
        mGasLayout = findViewById(R.id.gasLayout);

        mBreakLayout.setOnTouchListener(this);
        mGasLayout.setOnTouchListener(this);

        mConnectionIndicator.setOnClickListener(this);
        mPauseButton.setOnClickListener(this);
        mSettingsButton.setOnClickListener(this);

        mLeftSignalButton.setOnClickListener(this);
        mRightSignalButton.setOnClickListener(this);
        mAllSignalsButton.setOnClickListener(this);

        makeFullscreen();
        updatePrefs();

        client = new AccelerometerClient("Bullshit", 18250);
        client.setConnectionListener(this);
        dBm = getSignalStrength();

        if (wifi.isWifiEnabled() && !AccelerometerClient.running && !justChangedOr) {
            if (!defaultServer) {
                showToast(R.string.searching_on_local);
                client.run(false);
            } else {
                if (defaultServerIp.equals("shit")) {
                    showToast(R.string.failed_default_connect);
                } else {
                    showToast(getString(
                            R.string.attempting_to_connect_to) + " " + defaultServerIp + "...");
                    client.forceUpdate(defaultServerIp, defaultServerPort);
                    client.run(true);
                }
            }
        }
    }

    Runnable turnSignalsRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isConnected) {
                mLeftSignalButton.setImageResource(R.drawable.left_disabled);
                mRightSignalButton.setImageResource(R.drawable.right_disabled);
            } else if (client.turnSignalLeft && client.turnSignalRight) {
                if (previousSignalGreen) {
                    mLeftSignalButton.setImageResource(R.drawable.left_disabled);
                    mRightSignalButton.setImageResource(R.drawable.right_disabled);
                } else {
                    mLeftSignalButton.setImageResource(R.drawable.left_enabled);
                    mRightSignalButton.setImageResource(R.drawable.right_enabled);
                }
                previousSignalGreen = !previousSignalGreen;
                mHandler.postDelayed(this, 400);
            } else if (client.turnSignalLeft) {
                if (previousSignalGreen) {
                    mLeftSignalButton.setImageResource(R.drawable.left_disabled);
                    mRightSignalButton.setImageResource(R.drawable.right_disabled);
                } else {
                    mRightSignalButton.setImageResource(R.drawable.right_disabled);
                    mLeftSignalButton.setImageResource(R.drawable.left_enabled);
                }
                previousSignalGreen = !previousSignalGreen;
                mHandler.postDelayed(this, 400);
            } else if (client.turnSignalRight) {
                if (previousSignalGreen) {
                    mLeftSignalButton.setImageResource(R.drawable.left_disabled);
                    mRightSignalButton.setImageResource(R.drawable.right_disabled);
                    Log.d("TAG", "Green");
                } else {
                    mLeftSignalButton.setImageResource(R.drawable.left_disabled);
                    mRightSignalButton.setImageResource(R.drawable.right_enabled);
                    Log.d("TAG", "NotGreen");
                }
                previousSignalGreen = !previousSignalGreen;
                mHandler.postDelayed(this, 400);
            } else {
                mHandler.removeCallbacksAndMessages(null);
                mLeftSignalButton.setImageResource(R.drawable.left_disabled);
                mRightSignalButton.setImageResource(R.drawable.right_disabled);
                previousSignalGreen = false;
            }
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        breakButton.setPressed(false);
        gasButton.setPressed(false);
        updatePrefs();
        client.setPaused(false);
        mSensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_FASTEST);
        if (ManualConnectActivity.configured) {
            client.stop();
            client.forceUpdate(ManualConnectActivity.ipAddress, ManualConnectActivity.port);
            showToast(getString(R.string.attempting_to_connect_to)
                    + " " + ManualConnectActivity.ipAddress
                    + " " + getString(R.string.on_port) + " " + ManualConnectActivity.port);
            client.run(true);
            ManualConnectActivity.configured = false;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this, mSensor);
        client.setPaused(true);
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
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.buttonLeftSignal:
                if (client.turnSignalLeft && client.turnSignalRight) return;

                if (client.turnSignalRight || client.turnSignalLeft) {
                    mHandler.removeCallbacksAndMessages(null);
                }

                client.feedSignals(!client.turnSignalLeft, false);
                mHandler.post(turnSignalsRunnable);
                break;
            case R.id.buttonRightSignal:
                if (client.turnSignalLeft && client.turnSignalRight) return;

                if (client.turnSignalRight || client.turnSignalLeft) {
                    mHandler.removeCallbacksAndMessages(null);
                }

                client.feedSignals(false, !client.turnSignalRight);
                mHandler.post(turnSignalsRunnable);
                break;
            case R.id.buttonAllSignals:

                boolean allEnabled = client.turnSignalLeft && client.turnSignalRight;
                client.feedSignals(!allEnabled, !allEnabled);

                if (client.turnSignalLeft || client.turnSignalRight) {
                    mHandler.removeCallbacksAndMessages(null);
                }
                mHandler.post(turnSignalsRunnable);
                break;

            case R.id.connectionIndicator:
                if (wifi.isWifiEnabled()) {
                    showToast(getString(R.string.signal_strength) + " "
                            + getSignalStrength() + "dBm");
                } else {
                    getSignalStrength();
                }
                break;
            case R.id.pauseButton:
                if (AccelerometerClient.connected) {
                    boolean newState = !AccelerometerClient.paused;
                    client.setPaused(newState);
                    if (newState) {
                        mPauseButton.setImageResource(R.drawable.pause_btn_paused);
                    } else {
                        mPauseButton.setImageResource(R.drawable.pause_btn_resumed);
                    }
                }
                break;
            case R.id.settingsButton:
                showSettingsDialog();
                break;
        }
    }

    private void showSettingsDialog() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setItems(R.array.menu_items, (dialogInterface, i) -> {
                    switch (i) {
                        case 0:
                            client.stop();
                            client = null;
                            client = new AccelerometerClient("Bullshit", 18250);
                            client.setConnectionListener(MainActivity.this);
                            if (wifi.isWifiEnabled()) {
                                client.run(false);
                                showToast(R.string.searching_on_local);
                            } else {
                                showToast(R.string.no_wifi_conn_detected);
                            }
                            mPauseButton.setImageResource(R.drawable.pause_btn_resumed);
                            break;
                        case 1:
                            Intent toManual = new Intent(
                                    MainActivity.this, ManualConnectActivity.class);
                            startActivity(toManual);
                            break;
                        case 2:
                            client.stop();
                            showToast(R.string.disconnected_msg);
                            break;
                        case 3:
                            Intent toSettings = new Intent(MainActivity.this, SettingsActivity.class);
                            startActivity(toSettings);
                            break;
                    }
                })
                .create();
        dialog.show();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouch(View view, MotionEvent event) {
        switch (view.getId()) {
            case R.id.breakLayout:
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    breakButton.setPressed(true);
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    breakButton.setPressed(false);
                }
                break;
            case R.id.gasLayout:
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    gasButton.setPressed(true);
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    gasButton.setPressed(false);
                }
                break;
        }
        client.feedTouchFlags(breakButton.isPressed(), gasButton.isPressed());
        return false;
    }

    @Override
    public void onConnectionChanged(boolean isConnected) {
        this.isConnected = isConnected;
        if (isConnected) {
            mConnectionIndicator.setImageResource(R.drawable.connection_indicator_green);
        } else {
            mConnectionIndicator.setImageResource(R.drawable.connection_indicator_red);
        }
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
            if (!AccelerometerClient.toastShown) {
                if (AccelerometerClient.connected) {
                    showToast(getString(R.string.connected_to_server_at)
                            + " " + client.socket.getInetAddress().getHostAddress());
                } else {
                    showToast(R.string.connection_lost);
                }
                AccelerometerClient.toastShown = true;
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

    public int getSignalStrength() {
        try {
            if (!wifi.isWifiEnabled()) {
                showToast(R.string.no_wifi_conn_detected);
            }
            return wifi.getConnectionInfo().getRssi();
        } catch (Exception ignore) {
        }
        return -50;
    }

    private void showToast(@StringRes int resId) {
        showToast(getString(resId));
    }

    private void showToast(String string) {
        Toast toast = Toast.makeText(this, string, Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.TOP, 0, 0);
        toast.show();
    }
}

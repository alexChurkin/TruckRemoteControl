package com.alexchurkin.truckremote.activity;

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
import android.util.Patterns;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.view.ViewCompat;

import com.alexchurkin.truckremote.BuildConfig;
import com.alexchurkin.truckremote.R;
import com.alexchurkin.truckremote.TrackingClient;
import com.alexchurkin.truckremote.general.Prefs;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.InterstitialAd;
import com.google.android.gms.ads.MobileAds;

import static android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
import static com.alexchurkin.truckremote.fragment.SettingsFragment.PREF_KEY_ADDOFF;

public class MainActivity extends AppCompatActivity implements
        SensorEventListener,
        View.OnClickListener,
        View.OnTouchListener,
        TrackingClient.ConnectionListener {

    public static final String TURN_SIGNAL_RIGHT = "turnRight";
    public static final String TURN_SIGNAL_LEFT = "turnLeft";
    public static final String IS_PARKING = "isParking";
    public static final String LIGHTS_STATE = "lightsState";

    public static WifiManager wifi;
    public static int dBm = -200;

    private SensorManager mSensorManager;
    private Sensor mSensor;
    private Handler mHandler;
    SharedPreferences prefs;

    private ConstraintLayout rootView;
    private AppCompatImageButton mConnectionIndicator, mPauseButton, mSettingsButton;
    private AppCompatImageButton mLeftSignalButton, mRightSignalButton, mAllSignalsButton;
    private AppCompatImageButton mButtonParking, mButtonLights, mButtonHorn;
    private ConstraintLayout mBreakLayout, mGasLayout;
    private int toastMargin;

    private TrackingClient client;
    private boolean isConnected;
    private boolean previousSignalGreen;
    private boolean breakPressed, gasPressed;
    private int prevLightsState;

    private int activeProfileNumber = -1;

    private boolean runnableRunning;
    Runnable turnSignalsRunnable = new Runnable() {
        @Override
        public void run() {
            runnableRunning = true;
            if (!isConnected || client.isPausedByUser()) {
                runnableRunning = false;
                mLeftSignalButton.setImageResource(R.drawable.left_disabled);
                mRightSignalButton.setImageResource(R.drawable.right_disabled);
            } else if (client.isTwoTurnSignals()) {
                if (previousSignalGreen) {
                    mLeftSignalButton.setImageResource(R.drawable.left_disabled);
                    mRightSignalButton.setImageResource(R.drawable.right_disabled);
                } else {
                    mLeftSignalButton.setImageResource(R.drawable.left_enabled);
                    mRightSignalButton.setImageResource(R.drawable.right_enabled);
                }
                previousSignalGreen = !previousSignalGreen;
                mHandler.postDelayed(this, 400);
            } else if (client.isTurnSignalLeft()) {
                if (previousSignalGreen) {
                    mLeftSignalButton.setImageResource(R.drawable.left_disabled);
                    mRightSignalButton.setImageResource(R.drawable.right_disabled);
                } else {
                    mRightSignalButton.setImageResource(R.drawable.right_disabled);
                    mLeftSignalButton.setImageResource(R.drawable.left_enabled);
                }
                previousSignalGreen = !previousSignalGreen;
                mHandler.postDelayed(this, 400);
            } else if (client.isTurnSignalRight()) {
                if (previousSignalGreen) {
                    mLeftSignalButton.setImageResource(R.drawable.left_disabled);
                    mRightSignalButton.setImageResource(R.drawable.right_disabled);
                } else {
                    mLeftSignalButton.setImageResource(R.drawable.left_disabled);
                    mRightSignalButton.setImageResource(R.drawable.right_enabled);
                }
                previousSignalGreen = !previousSignalGreen;
                mHandler.postDelayed(this, 400);
            } else {
                mHandler.removeCallbacksAndMessages(null);
                runnableRunning = false;
                mLeftSignalButton.setImageResource(R.drawable.left_disabled);
                mRightSignalButton.setImageResource(R.drawable.right_disabled);
                previousSignalGreen = false;
            }
        }
    };


    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mHandler = new Handler();
        prefs = Prefs.get();

        setContentView(R.layout.activity_main);
        rootView = findViewById(R.id.rootView);

        mConnectionIndicator = findViewById(R.id.connectionIndicator);
        mPauseButton = findViewById(R.id.pauseButton);
        mSettingsButton = findViewById(R.id.settingsButton);

        mLeftSignalButton = findViewById(R.id.buttonLeftSignal);
        mRightSignalButton = findViewById(R.id.buttonRightSignal);
        mAllSignalsButton = findViewById(R.id.buttonAllSignals);

        mButtonParking = findViewById(R.id.buttonParking);
        mButtonLights = findViewById(R.id.buttonLights);
        mButtonHorn = findViewById(R.id.buttonHorn);

        mBreakLayout = findViewById(R.id.breakLayout);
        mGasLayout = findViewById(R.id.gasLayout);

        mBreakLayout.setOnTouchListener(this);
        mGasLayout.setOnTouchListener(this);
        mButtonHorn.setOnTouchListener(this);

        mConnectionIndicator.setOnClickListener(this);
        mPauseButton.setOnClickListener(this);
        mSettingsButton.setOnClickListener(this);
        mLeftSignalButton.setOnClickListener(this);
        mRightSignalButton.setOnClickListener(this);
        mAllSignalsButton.setOnClickListener(this);
        mButtonParking.setOnClickListener(this);
        mButtonLights.setOnClickListener(this);

        makeFullscreen();
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
            toastMargin = insets.getSystemWindowInsetRight();
            return insets.consumeSystemWindowInsets();
        });

        client = new TrackingClient(null, 18250, this);

        activeProfileNumber = getProfileNumber();

        if (activeProfileNumber != -1) {
            startClient();

        } else {
            showProfileChooseDialog();
        }

        if (!prefs.getBoolean("guideShowed", false)) {
            Intent toGuide = new Intent(this, GuideActivity.class);
            startActivity(toGuide);
            prefs.edit().putBoolean("guideShowed", true).apply();
        } else if (savedInstanceState == null && !prefs.getBoolean(PREF_KEY_ADDOFF, false)) {
            MobileAds.initialize(this, BuildConfig.ADMOB_APP_ID);
            showInterstitialAd();
        }
    }

    private void startClient() {
        client.provideSignalsInfo(prefs.getBoolean(TURN_SIGNAL_LEFT + activeProfileNumber, false),
                prefs.getBoolean(TURN_SIGNAL_RIGHT + activeProfileNumber, false));
        client.setParkingBreakEnabled(prefs.getBoolean(IS_PARKING + activeProfileNumber, false));

        prevLightsState = prefs.getInt(LIGHTS_STATE + activeProfileNumber, 0);
        client.setLightsState(prevLightsState);

        if (client.isParkingBreakEnabled()) {
            mButtonParking.setImageResource(R.drawable.parking_break_on);
        }
        setUiLightsByState(prevLightsState);


        dBm = getSignalStrength();

        if (wifi.isWifiEnabled()) {
            if (prefs.getBoolean("defaultServer", false)) {
                String serverIp = prefs.getString("serverIP", "");
                try {
                    int serverPort = Integer.parseInt(prefs.getString("serverPort", "18250"));
                    if (Patterns.IP_ADDRESS.matcher(serverIp).matches()) {
                        showToast(R.string.trying_to_connect);
                        client.start(serverIp, serverPort);
                    } else {
                        showToast(R.string.def_server_not_correct);
                    }
                } catch (Exception e) {
                    showToast(R.string.def_server_not_correct);
                }
            } else {
                showToast(R.string.searching_on_local);
                client.start();
            }
        }
    }

    private void showInterstitialAd() {
        InterstitialAd ad = new InterstitialAd(this);
        ad.setAdUnitId(BuildConfig.INTERSTITIAL_AD_ID);
        ad.loadAd(new AdRequest.Builder().build());
        ad.setAdListener(new AdListener() {
            @Override
            public void onAdLoaded() {
                ad.show();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        breakPressed = false;
        gasPressed = false;
        client.resume();
        mSensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_FASTEST);
        if (!runnableRunning) {
            mHandler.post(turnSignalsRunnable);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this, mSensor);
        mHandler.removeCallbacks(turnSignalsRunnable);
        runnableRunning = false;
        client.pause();
        prefs.edit()
                .putBoolean(TURN_SIGNAL_LEFT + activeProfileNumber, client.isTurnSignalLeft())
                .putBoolean(TURN_SIGNAL_RIGHT + activeProfileNumber, client.isTurnSignalRight())
                .putBoolean(IS_PARKING + activeProfileNumber, client.isParkingBreakEnabled())
                .putInt(LIGHTS_STATE + activeProfileNumber, client.getLightsState())
                .apply();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        client.stop();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.buttonLeftSignal:
                if (client.isTurnSignalLeft() && client.isTurnSignalRight()) return;

                if (client.isTurnSignalRight() || client.isTurnSignalLeft()) {
                    mHandler.removeCallbacksAndMessages(null);
                    runnableRunning = false;
                }

                client.provideSignalsInfo(!client.isTurnSignalLeft(), false);
                mHandler.post(turnSignalsRunnable);
                break;
            case R.id.buttonRightSignal:
                if (client.isTurnSignalLeft() && client.isTurnSignalRight()) return;

                if (client.isTurnSignalRight() || client.isTurnSignalLeft()) {
                    mHandler.removeCallbacksAndMessages(null);
                    runnableRunning = false;
                }

                client.provideSignalsInfo(false, !client.isTurnSignalRight());
                mHandler.post(turnSignalsRunnable);
                break;
            case R.id.buttonAllSignals:

                boolean allEnabled = client.isTurnSignalLeft() && client.isTurnSignalRight();
                client.provideSignalsInfo(!allEnabled, !allEnabled);

                if (client.isTurnSignalLeft() || client.isTurnSignalRight()) {
                    mHandler.removeCallbacksAndMessages(null);
                    runnableRunning = false;
                }
                mHandler.post(turnSignalsRunnable);
                break;
            case R.id.buttonParking:
                if (!isConnected || client.isPausedByUser()) return;

                boolean newValue = !client.isParkingBreakEnabled();
                client.setParkingBreakEnabled(newValue);
                if (newValue) {
                    mButtonParking.setImageResource(R.drawable.parking_break_on);
                } else {
                    mButtonParking.setImageResource(R.drawable.parking_break_off);
                }
                break;
            case R.id.buttonLights:
                if (!isConnected || client.isPausedByUser()) return;

                int newLightsState = ++prevLightsState;
                if (newLightsState > 3) newLightsState = 0;
                client.setLightsState(newLightsState);
                setUiLightsByState(newLightsState);
                prevLightsState = newLightsState;
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
                if (isConnected) {
                    boolean newState = !client.isPaused();
                    if (newState) {
                        client.pauseByUser();
                        mPauseButton.setImageResource(R.drawable.pause_btn_paused);
                    } else {
                        client.resumeByUser();
                        mPauseButton.setImageResource(R.drawable.pause_btn_resumed);
                    }
                }
                break;
            case R.id.settingsButton:
                showSettingsDialog();
                break;
        }
    }

    private void setUiLightsByState(int state) {
        switch (state) {
            case 0:
                mButtonLights.setImageResource(R.drawable.lights_off);
                break;
            case 1:
                mButtonLights.setImageResource(R.drawable.lights_gab);
                break;
            case 2:
                mButtonLights.setImageResource(R.drawable.lights_low);
                break;
            case 3:
                mButtonLights.setImageResource(R.drawable.lights_high);
                break;
        }
    }

    private void showProfileChooseDialog() {
        String[] variants3 = getResources().getStringArray(R.array.profile_variants);
        String[] variants = new String[2];
        variants[0] = variants3[0];
        variants[1] = variants3[1];


        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.what_profile_title)
                .setItems(variants, (dialogInterface, i) -> {
                    switch (i) {
                        case 0:
                            activeProfileNumber = 1;
                            break;
                        case 1:
                            activeProfileNumber = 2;
                            break;
                    }
                    startClient();
                }).setCancelable(false).create();
        showAlert(dialog);
    }

    private void showSettingsDialog() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setItems(R.array.menu_items, (dialogInterface, i) -> {
                    switch (i) {
                        case 0:
                            Intent toInstruction = new Intent(this, GuideActivity.class);
                            startActivity(toInstruction);
                            break;
                        case 1:
                            mPauseButton.setImageResource(R.drawable.pause_btn_resumed);
                            if (wifi.isWifiEnabled()) {
                                client.forceUpdate(null, 18250);
                                client.restart();
                                showToast(R.string.searching_on_local);
                            } else {
                                client.stop();
                                showToast(R.string.no_wifi_conn_detected);
                            }
                            mPauseButton.setImageResource(R.drawable.pause_btn_resumed);
                            break;
                        case 2:
                            mPauseButton.setImageResource(R.drawable.pause_btn_resumed);
                            if (wifi.isWifiEnabled()) {
                                String serverIp = prefs.getString("serverIP", "");
                                try {
                                    int serverPort = Integer.parseInt(prefs.getString("serverPort", "18250"));
                                    if (Patterns.IP_ADDRESS.matcher(serverIp).matches()) {
                                        showToast(R.string.trying_to_connect);
                                        client.forceUpdate(serverIp, serverPort);
                                        client.restart();
                                    } else {
                                        showToast(R.string.def_server_not_correct);
                                    }
                                } catch (Exception e) {
                                    showToast(R.string.def_server_not_correct);
                                }
                            } else {
                                client.stop();
                                showToast(R.string.no_wifi_conn_detected);
                            }
                            break;
                        case 3:
                            mPauseButton.setImageResource(R.drawable.pause_btn_resumed);
                            client.stop();
                            break;
                        case 4:
                            Intent toSettings = new Intent(MainActivity.this, SettingsActivity.class);
                            startActivity(toSettings);
                            break;
                    }
                })
                .create();
        showAlert(dialog);
    }

    private void showAlert(AlertDialog dialog) {
        dialog.getWindow().setFlags(FLAG_NOT_FOCUSABLE, FLAG_NOT_FOCUSABLE);
        dialog.show();
        dialog.getWindow().getDecorView().setSystemUiVisibility(
                getWindow().getDecorView().getSystemUiVisibility()
        );
        dialog.getWindow().clearFlags(FLAG_NOT_FOCUSABLE);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouch(View view, MotionEvent event) {
        switch (view.getId()) {
            case R.id.breakLayout:
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    breakPressed = true;
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    breakPressed = false;
                }
                break;
            case R.id.gasLayout:
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    gasPressed = true;
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    gasPressed = false;
                }
                break;
            case R.id.buttonHorn:
                if(!isConnected || client.isPausedByUser()) return false;

                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    client.setHornState(true);
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    client.setHornState(false);
                }
                break;
        }
        client.provideMotionState(breakPressed, gasPressed);
        return false;
    }

    @Override
    public void onConnectionChanged(boolean isConnected) {
        runOnUiThread(() -> {
            this.isConnected = isConnected;
            if (isConnected) {
                mConnectionIndicator.setImageResource(R.drawable.connection_indicator_green);
                showToast(getString(R.string.connected_to_server_at)
                        + " " + client.getSocketInetHostAddress());
                if (!runnableRunning) {
                    mHandler.post(turnSignalsRunnable);
                }
            } else {
                mConnectionIndicator.setImageResource(R.drawable.connection_indicator_red);
                showToast(R.string.connection_lost);
            }
        });
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
            float y = getWindowManager().getDefaultDisplay().getRotation() == Surface.ROTATION_270
                    ? (-event.values[1]) : event.values[1];
            if (prefs.getBoolean("deadZone", false)) {
                y = applyDeadZoneY(y);
            }
            client.provideAccelerometerY(y);
        } catch (Exception ignore) {
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
    }

    public float applyDeadZoneY(float y) {
        if (y > -.98 && y < .98) {
            y = 0f;
        }
        return y;
    }

    private void makeFullscreen() {
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN;

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

    private int getProfileNumber() {
        int defProfile = Integer.parseInt(prefs.getString("defaultProfile", "0"));
        switch (defProfile) {
            case 0: return 1;
            case 1: return 2;
            case 2: return -1;
        }
        return 1;
    }

    private void showToast(@StringRes int resId) {
        showToast(getString(resId));
    }

    private void showToast(String string) {
        Toast toast = Toast.makeText(this, string, Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.TOP, toastMargin / 2, 0);
        toast.show();
    }
}
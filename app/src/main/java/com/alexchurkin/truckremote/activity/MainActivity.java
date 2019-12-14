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
import android.os.Bundle;
import android.os.Handler;
import android.util.Patterns;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.view.ViewCompat;

import com.alexchurkin.truckremote.BuildConfig;
import com.alexchurkin.truckremote.R;
import com.alexchurkin.truckremote.TrackingClient;
import com.alexchurkin.truckremote.helpers.ActivityExt;
import com.alexchurkin.truckremote.helpers.FullScreenActivityExt;
import com.alexchurkin.truckremote.helpers.Prefs;
import com.alexchurkin.truckremote.helpers.Toaster;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.InterstitialAd;
import com.google.android.gms.ads.MobileAds;

import static com.alexchurkin.truckremote.PrefConsts.CALIBRATION_OFFSET;
import static com.alexchurkin.truckremote.PrefConsts.DEFAULT_PROFILE;
import static com.alexchurkin.truckremote.PrefConsts.GUIDE_SHOWED;
import static com.alexchurkin.truckremote.PrefConsts.LAST_SHOWED_VERSION_INFO;
import static com.alexchurkin.truckremote.PrefConsts.PORT;
import static com.alexchurkin.truckremote.PrefConsts.SPECIFIED_IP;
import static com.alexchurkin.truckremote.PrefConsts.USE_PNEUMATIC_SIGNAL;
import static com.alexchurkin.truckremote.PrefConsts.USE_SPECIFIED_SERVER;
import static com.alexchurkin.truckremote.fragment.SettingsFragment.PREF_KEY_ADDOFF;
import static com.alexchurkin.truckremote.helpers.ActivityExt.isReverseLandscape;
import static com.alexchurkin.truckremote.helpers.Toaster.showToastWithOffset;
import static java.lang.Math.abs;

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
    private AppCompatImageView mGasImage;

    private Animation gasCruiseAnimation;

    private TrackingClient client;
    private boolean isConnected;
    private boolean previousSignalGreen;
    private boolean breakPressed, gasPressed;
    private int prevLightsState;

    private float lastReceivedYValue, calibrationOffset;

    private int activeProfileNumber = -1;

    private boolean runnableRunning;

    Runnable turnSignalsRunnable = new Runnable() {
        @Override
        public void run() {
            runnableRunning = true;
            if (!isConnected) {
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

    private GestureDetector.SimpleOnGestureListener cruiseGestureListener = new GestureDetector
            .SimpleOnGestureListener() {

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if (!isConnected || client.isPausedByUser())
                return super.onFling(e1, e2, velocityX, velocityY);

            float absVelocityY = abs(velocityY) / 1000;

            float absMovingX = abs(e1.getX() - e2.getX());
            float absMovingY = abs(e1.getY() - e2.getY());

            if (velocityY < 0 && absVelocityY > 1.5 && absMovingX / absMovingY < 0.5) {
                client.toggleCruise();
                mGasImage.startAnimation(gasCruiseAnimation);
            }
            return super.onFling(e1, e2, velocityX, velocityY);
        }
    };

    private GestureDetector cruiseGestureDetector;


    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mHandler = new Handler();
        prefs = Prefs.get();
        cruiseGestureDetector = new GestureDetector(this, cruiseGestureListener);

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
        mGasImage = mGasLayout.findViewById(R.id.gasImage);

        gasCruiseAnimation = AnimationUtils.loadAnimation(this, R.anim.gas_cruise);

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

        ActivityExt.enterFullscreen(this);
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
            Toaster.toastOffset = isReverseLandscape(this) ?
                    insets.getSystemWindowInsetLeft() / 2 : insets.getSystemWindowInsetRight() / 2;
            return insets.consumeSystemWindowInsets();
        });

        client = new TrackingClient(null, 18250, this);

        activeProfileNumber = getProfileNumber();

        if (activeProfileNumber != -1) {
            startClient();

        } else {
            showProfileChooseDialog();
        }

        calibrationOffset = prefs.getFloat(CALIBRATION_OFFSET, 0f);

        if (!prefs.getBoolean(GUIDE_SHOWED, false)) {
            Intent toGuide = new Intent(this, GuideActivity.class);
            startActivity(toGuide);
            prefs.edit()
                    .putBoolean(GUIDE_SHOWED, true)
                    .putInt(LAST_SHOWED_VERSION_INFO, getResources().getInteger(R.integer.version))
                    .apply();
        } else if (savedInstanceState == null && !prefs.getBoolean(PREF_KEY_ADDOFF, false)) {
            MobileAds.initialize(this, BuildConfig.ADMOB_APP_ID);
            showInterstitialAd();
        }

        if (prefs.getInt(LAST_SHOWED_VERSION_INFO, 0) != getResources().getInteger(R.integer.version)) {
            showReleaseNewsDialog();
        }
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
            //Receiving port number
            int port = getServerPort();

            //Auto-connect
            if (!prefs.getBoolean(USE_SPECIFIED_SERVER, false)) {
                showToastWithOffset(R.string.searching_on_local);
                client.start(null, port);
            }
            //Connection to default ip
            else {
                String serverIp = prefs.getString(SPECIFIED_IP, "");
                try {
                    if (Patterns.IP_ADDRESS.matcher(serverIp).matches()) {
                        showToastWithOffset(R.string.trying_to_connect);
                        client.start(serverIp, port);
                    } else {
                        showToastWithOffset(R.string.def_server_ip_not_correct);
                    }
                } catch (Exception e) {
                    showToastWithOffset(R.string.def_server_ip_not_correct);
                }
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
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.buttonLeftSignal:
                if (client.isPausedByUser()) return;

                if (client.isTurnSignalLeft() && client.isTurnSignalRight()) return;

                if (client.isTurnSignalRight() || client.isTurnSignalLeft()) {
                    mHandler.removeCallbacksAndMessages(null);
                    runnableRunning = false;
                }

                client.provideSignalsInfo(!client.isTurnSignalLeft(), false);
                mHandler.post(turnSignalsRunnable);
                break;
            case R.id.buttonRightSignal:
                if (client.isPausedByUser()) return;

                if (client.isTurnSignalLeft() && client.isTurnSignalRight()) return;

                if (client.isTurnSignalRight() || client.isTurnSignalLeft()) {
                    mHandler.removeCallbacksAndMessages(null);
                    runnableRunning = false;
                }

                client.provideSignalsInfo(false, !client.isTurnSignalRight());
                mHandler.post(turnSignalsRunnable);
                break;
            case R.id.buttonAllSignals:
                if (client.isPausedByUser()) return;

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
                    showToastWithOffset(getString(R.string.signal_strength) + " "
                            + abs(getSignalStrength()) + " dBm");
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

    private void showReleaseNewsDialog() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.version_changes_title)
                .setMessage(R.string.version_changes_text)
                .setPositiveButton(R.string.close, (dialogInterface, i) ->
                        prefs.edit().putInt(LAST_SHOWED_VERSION_INFO,
                                getResources().getInteger(R.integer.version)).apply())
                .setCancelable(false)
                .create();
        FullScreenActivityExt.showAlert(this, dialog);
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
        FullScreenActivityExt.showAlert(this, dialog);
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
                                client.forceUpdate(null, getServerPort());
                                client.restart();
                                showToastWithOffset(R.string.searching_on_local);
                            } else {
                                client.stop();
                                showToastWithOffset(R.string.no_wifi_conn_detected);
                            }
                            mPauseButton.setImageResource(R.drawable.pause_btn_resumed);
                            break;
                        case 2:
                            mPauseButton.setImageResource(R.drawable.pause_btn_resumed);
                            if (wifi.isWifiEnabled()) {
                                String serverIp = prefs.getString(SPECIFIED_IP, "");
                                try {
                                    int serverPort = getServerPort();
                                    if (Patterns.IP_ADDRESS.matcher(serverIp).matches()) {
                                        showToastWithOffset(R.string.trying_to_connect);
                                        client.forceUpdate(serverIp, serverPort);
                                        client.restart();
                                    } else {
                                        showToastWithOffset(R.string.def_server_ip_not_correct);
                                    }
                                } catch (Exception e) {
                                    showToastWithOffset(R.string.def_server_ip_not_correct);
                                }
                            } else {
                                client.stop();
                                showToastWithOffset(R.string.no_wifi_conn_detected);
                            }
                            break;
                        case 3:
                            mPauseButton.setImageResource(R.drawable.pause_btn_resumed);
                            client.stop();
                            break;
                        case 4:
                            dialogInterface.dismiss();
                            showCalibrationDialog();
                            break;
                        case 5:
                            Intent toSettings = new Intent(MainActivity.this, SettingsActivity.class);
                            startActivity(toSettings);
                            break;
                    }
                })
                .create();
        FullScreenActivityExt.showAlert(this, dialog);
    }

    private void showCalibrationDialog() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setItems(R.array.calibration_items, (dialogInterface, i) -> {
                    switch (i) {
                        case 0:
                            calibrationOffset = -lastReceivedYValue;
                            prefs.edit()
                                    .putFloat(CALIBRATION_OFFSET, calibrationOffset)
                                    .apply();
                            showToastWithOffset(R.string.calibration_completed);
                            break;
                        case 1:
                            calibrationOffset = 0;
                            prefs.edit()
                                    .putFloat(CALIBRATION_OFFSET, calibrationOffset)
                                    .apply();
                            showToastWithOffset(R.string.calibration_reset);
                            break;
                    }
                })
                .create();
        FullScreenActivityExt.showAlert(this, dialog);
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
                cruiseGestureDetector.onTouchEvent(event);
                break;
            case R.id.buttonHorn:
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    if (prefs.getBoolean(USE_PNEUMATIC_SIGNAL, false)) {
                        client.setHornState(2);
                    } else {
                        client.setHornState(1);
                    }
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    client.setHornState(0);
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
                showToastWithOffset(getString(R.string.connected_to_server_at)
                        + " " + client.getSocketInetHostAddress());
                if (!runnableRunning) {
                    mHandler.post(turnSignalsRunnable);
                }
            } else {
                mConnectionIndicator.setImageResource(R.drawable.connection_indicator_red);
                showToastWithOffset(R.string.connection_lost);
            }
        });
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            ActivityExt.enterFullscreen(this);
        }
    }

    public void onSensorChanged(SensorEvent event) {
        try {
            float receivedYValue = event.values[1] + calibrationOffset;
            lastReceivedYValue = receivedYValue;
            float realY = isReverseLandscape(this) ? (-receivedYValue) : receivedYValue;
            if (prefs.getBoolean("deadZone", false)) {
                realY = applyDeadZoneY(realY);
            }
            client.provideAccelerometerY(realY);
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

    public int getSignalStrength() {
        try {
            if (!wifi.isWifiEnabled()) {
                showToastWithOffset(R.string.no_wifi_conn_detected);
            }
            return wifi.getConnectionInfo().getRssi();
        } catch (Exception ignore) {
        }
        return -50;
    }

    private int getProfileNumber() {
        int defProfile = Integer.parseInt(prefs.getString(DEFAULT_PROFILE, "0"));
        switch (defProfile) {
            case 0:
                return 1;
            case 1:
                return 2;
            case 2:
                return -1;
        }
        return 1;
    }

    private int getServerPort() {
        int port;
        try {
            port = Integer.parseInt(prefs.getString(PORT, "18250"));
            if (port < 10000 || port > 65535) {
                port = 18250;
                prefs.edit().putString(PORT, "18250").apply();
            }
        } catch (Exception e) {
            port = 18250;
            prefs.edit().putString(PORT, "18250").apply();
        }
        return port;
    }
}
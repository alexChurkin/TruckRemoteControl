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
import android.os.VibrationEffect;
import android.os.Vibrator;
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
import com.alexchurkin.truckremote.dialog.MenuDialogFragment;
import com.alexchurkin.truckremote.helpers.ActivityExt;
import com.alexchurkin.truckremote.helpers.FullScreenActivityExt;
import com.alexchurkin.truckremote.helpers.Prefs;
import com.alexchurkin.truckremote.helpers.Toaster;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.InterstitialAd;
import com.google.android.gms.ads.MobileAds;

import static com.alexchurkin.truckremote.PrefConsts.CALIBRATION_OFFSET;
import static com.alexchurkin.truckremote.PrefConsts.FORCE_FEEDBACK;
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
        TrackingClient.ConnectionListener,
        MenuDialogFragment.ItemClickListener {

    private static boolean hasMenuShowed;

    public static WifiManager wifi;
    public static int dBm = -200;

    private SensorManager mSensorManager;
    private Sensor mSensor;
    private Vibrator vibrator;
    SharedPreferences prefs;

    private ConstraintLayout rootView;
    private AppCompatImageButton mConnectionIndicator, mPauseButton, mSettingsButton;
    private AppCompatImageButton mLeftSignalButton, mRightSignalButton, mAllSignalsButton;
    private AppCompatImageButton mButtonParking, mButtonLights, mButtonHorn;
    private ConstraintLayout mBreakLayout, mGasLayout;
    private AppCompatImageView mGasImage;

    private Animation gasCruiseAnimation;

    private TrackingClient client;
    private boolean isConnectedToServer;
    private boolean breakPressed, gasPressed;

    private float lastReceivedYValue, calibrationOffset;

    private boolean useFFB;

    private GestureDetector.SimpleOnGestureListener cruiseGestureListener = new GestureDetector
            .SimpleOnGestureListener() {

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if (!isConnectedToServer || client.isPausedByUser())
                return super.onFling(e1, e2, velocityX, velocityY);

            float absVelocityY = abs(velocityY) / 1000;

            float absMovingX = abs(e1.getX() - e2.getX());
            float absMovingY = abs(e1.getY() - e2.getY());

            if (velocityY < 0 && absVelocityY > 1.5 && absMovingX / absMovingY < 0.5) {
                client.slideCruise();
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
        vibrator = (Vibrator) getApplicationContext().getSystemService(Context.VIBRATOR_SERVICE);
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
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

        startClient();

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

        useFFB = prefs.getBoolean(FORCE_FEEDBACK, false);

        if (isConnectedToServer) {
            mSensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_FASTEST);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isConnectedToServer) {
            mSensorManager.unregisterListener(this, mSensor);
        }
        client.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        client.stop();
    }


    private void startClient() {
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
                client.clickLeftBlinker();
                break;
            case R.id.buttonRightSignal:
                if (client.isPausedByUser()) return;
                client.clickRightBlinker();
                break;
            case R.id.buttonAllSignals:
                if (client.isPausedByUser()) return;
                client.clickEmergencySignal();
                break;
            case R.id.buttonParking:
                if (!isConnectedToServer || client.isPausedByUser()) return;
                client.clickParkingBreak();
                break;
            case R.id.buttonLights:
                if (!isConnectedToServer || client.isPausedByUser()) return;
                client.clickLights();
                break;
            case R.id.connectionIndicator:
                if (wifi.isWifiEnabled()) {
                    showToastWithOffset(getString(R.string.signal_strength) + " "
                            + getSignalStrength() + " dBm");
                } else {
                    getSignalStrength();
                }
                break;
            case R.id.pauseButton:
                if (isConnectedToServer) {
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
                showMenuDialog();
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

    private void showMenuDialog() {
        if (!hasMenuShowed) {
            MenuDialogFragment fr = new MenuDialogFragment();
            fr.show(getSupportFragmentManager(), "Menu");
            hasMenuShowed = true;
        }
    }

    @Override
    public void onItemClick(int position) {
        switch (position) {
            case 0:
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
            case 1:
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
            case 2:
                mPauseButton.setImageResource(R.drawable.pause_btn_resumed);
                client.stop();
                break;
            case 3:
                Intent toInstruction = new Intent(this, GuideActivity.class);
                startActivity(toInstruction);
                break;
            case 4:
                showCalibrationDialog();
                break;
            case 5:
                Intent toSettings = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(toSettings);
                break;
            case -1:
                hasMenuShowed = false;
                break;
        }
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
                        client.changeHornState(2);
                    } else {
                        client.changeHornState(1);
                    }
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    client.changeHornState(0);
                }
                break;
        }
        client.provideMotionState(breakPressed, gasPressed);
        return false;
    }


    @Override
    public void onConnectionChanged(int connectionState) {
        runOnUiThread(() -> {
            this.isConnectedToServer = connectionState != TrackingClient.ConnectionListener.NOT_CONNECTED;

            switch (connectionState) {
                case TrackingClient.ConnectionListener.NOT_CONNECTED:
                    mConnectionIndicator.setImageResource(R.drawable.connection_indicator_red);
                    showToastWithOffset(R.string.connection_lost);
                    mSensorManager.unregisterListener(this, mSensor);
                    break;
                case TrackingClient.ConnectionListener.CONNECTED:
                    mConnectionIndicator.setImageResource(R.drawable.connection_indicator_green);
                    showToastWithOffset(getString(R.string.connected_to_server_at)
                            + " " + client.getSocketInetHostAddress());
                    mSensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_FASTEST);
                    break;
            }
        });
    }

    @Override
    public void onParkingUpdate(boolean isParking) {
        runOnUiThread(() -> {
            if (isParking) {
                mButtonParking.setImageResource(R.drawable.parking_break_on);
            } else {
                mButtonParking.setImageResource(R.drawable.parking_break_off);
            }
        });
    }

    @Override
    public void onLightsUpdate(int lightsMode) {
        runOnUiThread(() -> {
            switch (lightsMode) {
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
        });
    }

    @Override
    public void onBlinkersUpdate(boolean leftBlinker, boolean rightBlinker) {
        runOnUiThread(() -> {
            if (leftBlinker) {
                mLeftSignalButton.setImageResource(R.drawable.left_enabled);
            } else {
                mLeftSignalButton.setImageResource(R.drawable.left_disabled);
            }

            if (rightBlinker) {
                mRightSignalButton.setImageResource(R.drawable.right_enabled);
            } else {
                mRightSignalButton.setImageResource(R.drawable.right_disabled);
            }

            if(leftBlinker && rightBlinker) {
                mAllSignalsButton.setImageResource(R.drawable.emergency_on);
            } else {
                mAllSignalsButton.setImageResource(R.drawable.emergency_off);
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
            float realYValue = isReverseLandscape(this) ? (-receivedYValue) : receivedYValue;
            if (prefs.getBoolean("deadZone", false)) {
                realYValue = applyDeadZoneY(realYValue);
            }
            client.provideAccelerometerY(realYValue);

            long ffbDuration = client.getFfbDuration();
            if (vibrator.hasVibrator() && useFFB && ffbDuration != 0) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(ffbDuration,
                            VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(ffbDuration);
                }
                client.resetFfbDuration();
            }
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
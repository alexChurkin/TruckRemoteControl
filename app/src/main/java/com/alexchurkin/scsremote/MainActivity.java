package com.alexchurkin.scsremote;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity implements SensorEventListener, OnTouchListener {

    private SensorManager mSensorManager;
    private Sensor mSensor;
    private GraphicsView graphicsView;
    private AccelerometerMouseClient client;
    public static MouseButton leftButton = new MouseButton(), rightButton = new MouseButton(), middleButton = new MouseButton(), scrollWheel = new MouseButton();
    public static int dBm = -200;
    private final static double compression = 0.75;
    public static WifiManager wifi;
    private boolean defaultServer = false, invertX = false, invertY = false, deadZone = false, tablet = false, changingOr = false, justChangedOr = false;
    private int defaultServerPort = 18250, number = 1280, zero = 22;
    private String defaultServerIp = "shit";
    private int displayWidth, displayHeight;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        updatePrefs();
        graphicsView = new GraphicsView(this);
        wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        setContentView(graphicsView);

        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(displayMetrics);
        displayWidth = displayMetrics.widthPixels;
        displayHeight = displayMetrics.heightPixels;

        makeFullscreen();

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        graphicsView.setOnTouchListener(this);
        client = new AccelerometerMouseClient("Bullshit", 18250);
        dBm = getSignalStrength();
        if (wifi.isWifiEnabled() && !AccelerometerMouseClient.running && !justChangedOr) {
            if (!defaultServer) {
                showToast("Searching for servers on the local network...", Toast.LENGTH_SHORT);
                client.run(false);
            } else {
                if (defaultServerIp.equals("shit")) {
                    showToast("Failed to connect to default server. None defined.", Toast.LENGTH_SHORT);
                } else {
                    showToast("Attempting to connect to " + defaultServerIp + "...", Toast.LENGTH_SHORT);
                    client.forceUpdate(defaultServerIp, defaultServerPort);
                    client.run(true);
                }
            }
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            makeFullscreen();
        }
    }

    private void makeFullscreen() {
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.KEEP_SCREEN_ON;

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            flags = flags | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        }

        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    private void updatePrefs() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        try {
            defaultServer = prefs.getBoolean("defaultServer", false);
            invertX = prefs.getBoolean("invertX", false);
            invertY = prefs.getBoolean("invertY", false);
            deadZone = prefs.getBoolean("deadZone", false);
            tablet = prefs.getBoolean("tablet", false);
            justChangedOr = prefs.getBoolean("justChangedOr", false);
            defaultServerIp = prefs.getString("serverIP", "shit");
            zero = Integer.parseInt(prefs.getString("zeroPosition", "22"));
            defaultServerPort = Integer.parseInt(prefs.getString("serverPort", "18250"));
        } catch (Exception e) {
            showToast("Error occrued while reading preferences. Please check that they are correct!", Toast.LENGTH_SHORT);
        }
    }

    public int getSignalStrength() {
        try {
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (!wifiManager.isWifiEnabled()) {
                showToast("No Wi-Fi connection detected!", Toast.LENGTH_SHORT);
            }
            return wifiManager.getConnectionInfo().getRssi();
        } catch (Exception e) {

        }
        return -50;
    }

    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Could care less
    }

    @Override
    protected void onResume() {
        super.onResume();
        leftButton.setState(false);
        rightButton.setState(false);
        middleButton.setState(false);
        scrollWheel.setState(false);
        updatePrefs();
        graphicsView.resume();
        client.pause(false);
        mSensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_FASTEST);
        if (ManualConnectActivity.configured) {
            client.stop();
            client.forceUpdate(ManualConnectActivity.ipAddress, ManualConnectActivity.port);
            showToast("Attempting to connect to " + ManualConnectActivity.ipAddress + " on port " + ManualConnectActivity.port, Toast.LENGTH_LONG);
            client.run(true);
            ManualConnectActivity.configured = false;
        }
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        openOptionsMenu();
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
        graphicsView.stop();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.getApplicationContext());
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("justChangedOr", changingOr);
        if (!changingOr)
            editor.putString("lastServer", "");
        editor.apply();
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
                    showToast("Connected to server at " + client.socket.getInetAddress().getHostAddress(), Toast.LENGTH_LONG);
                } else {
                    showToast("Lost connection to server!", Toast.LENGTH_LONG);
                }
                AccelerometerMouseClient.toastShown = true;
            }
        } catch (Exception ignore) {
        }
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

    public boolean onTouch(View v, MotionEvent event) {
        number = 1280;
        if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == 261 || event.getAction() == 5) {
            if (!middleButton.getState()) {
                if (isLeft(event.getX(getPID(event)), event.getY(getPID(event)))) {
                    leftButton.setState(true);
                    leftButton.setPID(getPID(event));
                } else if (isRight(event.getX(getPID(event)), event.getY(getPID(event)))) {
                    rightButton.setState(true);
                    rightButton.setPID(getPID(event));
                } else if (isMiddle(event)) {
                    if (event.getPointerCount() > 1) {
                        scrollWheel.setState(true);
                    } else {
                        middleButton.setState(true);
                    }
                }
            }
            if (event.getX(getPID(event)) > displayWidth - (int) (displayWidth * 88 / number + 0.5) && event.getY(getPID(event)) > displayHeight - (int) (displayWidth * 88 / number + 0.5)) {
                if (wifi.isWifiEnabled())
                    showToast("Wi-Fi signal strength: " + getSignalStrength() + "dBm", Toast.LENGTH_SHORT);
                else
                    getSignalStrength();
            } else if (event.getX(getPID(event)) < (displayWidth / 60.0) + 40 && event.getY(getPID(event)) < (displayHeight / 60.0) + 40) {
                if (AccelerometerMouseClient.connected) {
                    showToast("Connected to server at " + client.socket.getInetAddress().getHostAddress(), Toast.LENGTH_LONG);
                } else {
                    showToast("Not connected to a server. Please try searching for one by clicking \"Search for Server\" in the menu, or manually connect to the server by clicking \"Manually Connect\".", Toast.LENGTH_LONG);
                }
            } else if (event.getX(getPID(event)) > displayWidth - (int) (displayWidth * 88 / number + 0.5) && event.getY(getPID(event)) < (int) (displayWidth * 88 / number + 0.5)) {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.getApplicationContext());
                SharedPreferences.Editor editor = prefs.edit();
                try {
                    if (AccelerometerMouseClient.connected) {
                        editor.putString("lastServer", client.socket.getInetAddress().getHostAddress() + ":" + client.port);
                        client.sendJamSignal();
                        changingOr = true;
                    }
                } catch (Exception e) {
                    // You never know...
                }
                editor.apply();
            } else if (event.getX(getPID(event)) < (int) (displayWidth * 88 / number + 0.5) && event.getY(getPID(event)) > displayHeight - (int) (displayWidth * 88 / number + 0.5)) {
                if (AccelerometerMouseClient.connected) {
                    client.pause(!AccelerometerMouseClient.paused);
                }
            }
        } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == 262 || event.getAction() == 6) {
            if (event.getPointerCount() > 1) {
                if (leftButton.getState() && event.getX(getPID(event)) < displayWidth / 2.0) {
                    leftButton.setState(false);
                } else if (rightButton.getState() && event.getX(getPID(event)) > displayWidth / 2.0) {
                    rightButton.setState(false);
                } else if (scrollWheel.getState() && isMiddle(event)) {
                    scrollWheel.setState(false);
                }
            } else {
                if (leftButton.getState() || rightButton.getState() || scrollWheel.getState())
                leftButton.setState(false);
                rightButton.setState(false);
                scrollWheel.setState(false);
                middleButton.setState(false);
            }
        }
        client.feedTouchFlags(leftButton.getState(), rightButton.getState(), middleButton.getState(), scrollWheel.getState());
        Log.d("Touch", "action=" + event.getAction() + ", left flag=" + leftButton.getState() + ", right flag=" + rightButton.getState() + ", middle flag=" + scrollWheel.getState() + ", pointers=" + event.getPointerCount());
        return true;
    }

    private boolean isLeft(float x, float y) {
        return (x < (displayWidth / 2.0)-(displayWidth / 16.0)) && (y > displayHeight * 0.05) && (y < displayHeight * 0.9125);
    }

    private boolean isRight(float x, float y) {
        return (x > (displayWidth / 2.0) + (displayWidth / 16.0)) && (y > displayHeight * 0.05) && (y < displayHeight * 0.9125);
    }

    private boolean isMiddle(MotionEvent event) {
        if (event.getPointerCount() > 1) {
            boolean sentinel = true;
            int falseCounts = 0;
            for (int i = 0; i < event.getPointerCount(); i++) {
                if (event.getX(i) < (displayWidth / 2.0) + (displayWidth / 16.0) && event.getX(i) > (displayWidth / 2.0) - (displayWidth / 16.0)) {
                    falseCounts++;
                    sentinel = false;
                }
            }
            if (event.getX(getPID(event)) < (displayWidth / 2.0) + (displayWidth / 16.0) && event.getX(getPID(event)) > (displayWidth / 2.0) - (displayWidth / 16.0)) {
                sentinel = true;
            }
            if (falseCounts > 1) {
                sentinel = false;
            }
            return !(leftButton.getState() || rightButton.getState()) && sentinel;
        } else {
            if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == 261 || event.getAction() == 5) {
                return !(leftButton.getState() || rightButton.getState()) && event.getX(getPID(event)) < (displayWidth / 2.0) + (displayWidth / 16.0) && event.getX(getPID(event)) > (displayWidth / 2) - (displayWidth / 16) && event.getY(getPID(event)) > displayHeight * 0.4 && event.getY(getPID(event)) < displayHeight * 0.7;
            } else {
                return true;
            }
        }
    }

    private int getPID(MotionEvent event) {
        int action = event.getAction();
        return action >> MotionEvent.ACTION_POINTER_ID_SHIFT;
    }

    private void showToast(String string, int duration) {
        Toast toast = Toast.makeText(this, string, duration);
        toast.setGravity(Gravity.TOP | Gravity.END, 0, 0);
        toast.show();
    }

    /**
     * Menu
     */

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 0, 0, "Auto Connect"); // Index 0
        menu.add(0, 1, 0, "Manually Connect"); // Index 1
        menu.add(0, 2, 0, "Disconnect"); // Index 2
        menu.add(0, 3, 0, "Preferences"); // Index 3
        Log.i("Menu", "Menu shown");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.d("Menu", "Selected item: " + item.getItemId());
        switch (item.getItemId()) {
            case 0:
                client.stop();
                client = null;
                client = new AccelerometerMouseClient("Bullshit", 18250);
                if (wifi.isWifiEnabled()) {
                    client.run(false);
                    showToast("Searching for servers on the local network...", Toast.LENGTH_SHORT);
                } else {
                    showToast("No Wi-Fi connection detected! Search aborted.", Toast.LENGTH_SHORT);
                }
                break;
            case 1:
                Intent i = new Intent(this, ManualConnectActivity.class);
                startActivity(i);
                break;
            case 2:
                client.stop();
                showToast("Disconnected from server.", Toast.LENGTH_SHORT);
                break;
            case 3:
                Intent i2 = new Intent(this, Preferences.class);
                startActivity(i2);
                break;
        }
        return true;
    }

    /**
     * GraphicsView
     */

    static public class GraphicsView extends SurfaceView implements Runnable {

        Thread t;
        SurfaceHolder holder;
        boolean loop = false;
        Bitmap mouseNone, mouseAll, mouseLeft, mouseRight, mouseMiddle, mouseScroll, bar0, bar1, bar2, bar3, bar4, bar5, paused, playing, fluff;

        public GraphicsView(Context context) {
            super(context);
            holder = getHolder();
        }

        public void run() {
            while (loop) {
                if (!holder.getSurface().isValid()) {
                    continue;
                }
                try {
                    Canvas canvas = holder.lockCanvas();

                    Options options = new BitmapFactory.Options();
                    options.inScaled = false;
                    options.inDither = false;
                    options.inPreferredConfig = Bitmap.Config.ARGB_8888;

                    if (mouseNone == null) {
                        bar0 = BitmapFactory.decodeResource(getResources(), R.drawable.bar0);
                        bar1 = BitmapFactory.decodeResource(getResources(), R.drawable.bar1);
                        bar2 = BitmapFactory.decodeResource(getResources(), R.drawable.bar2);
                        bar3 = BitmapFactory.decodeResource(getResources(), R.drawable.bar3);
                        bar4 = BitmapFactory.decodeResource(getResources(), R.drawable.bar4);
                        bar5 = BitmapFactory.decodeResource(getResources(), R.drawable.bar5);
                        paused = BitmapFactory.decodeResource(getResources(), R.drawable.pause);
                        playing = BitmapFactory.decodeResource(getResources(), R.drawable.play);
                        fluff = BitmapFactory.decodeResource(getResources(), R.drawable.fluff);
                    }
                    Paint paint = new Paint();
                    paint.setAntiAlias(true);
                    paint.setFilterBitmap(true);
                    paint.setDither(true);

                    int bitmapId = R.drawable.none;
                    if (leftButton.getState() && !rightButton.getState()) {
                        bitmapId = R.drawable.left;
                    }
                    if (!leftButton.getState() && rightButton.getState()) {
                        bitmapId = R.drawable.right;
                    }
                    if (leftButton.getState() && rightButton.getState()) {
                        bitmapId = R.drawable.all;
                    }
                    if (middleButton.getState()) {
                        bitmapId = R.drawable.middle;
                    }

                    Bitmap bitmap = Bitmap.createScaledBitmap(BitmapFactory.decodeResource(getResources(), bitmapId, options), (int) (canvas.getWidth() * compression), (int) (canvas.getHeight() * compression), true);

                    canvas.drawBitmap(bitmap, null, new RectF(0, 0, canvas.getWidth(), canvas.getHeight()), paint);
                    // Draw a circle for connectivity
                    if (!AccelerometerMouseClient.connected) {
                        paint.setColor(Color.argb(200, 255, 0, 0));
                    } else {
                        paint.setColor(Color.argb(200, 0, 255, 0));
                    }
                    int diameter = canvas.getWidth() / 60;
                    canvas.drawOval(new RectF(10, 10, 10 + diameter, 10 + diameter), paint);
                    // Signal bars
                    if (dBm > -100) {
                        bitmap = bar1;
                    }
                    if (dBm > -80) {
                        bitmap = bar2;
                    }
                    if (dBm > -70) {
                        bitmap = bar3;
                    }
                    if (dBm > -50) {
                        bitmap = bar4;
                    }
                    if (dBm > -30) {
                        bitmap = bar5;
                    }
                    if (dBm < -100) {
                        bitmap = bar0;
                    }

                    int number = 1280;
                    int radius = (int) (canvas.getWidth() * 160 / number + 0.5);
                    Paint paintFluff = new Paint();
                    // Draw some fluff on the bottom left/right corners
                    canvas.drawBitmap(fluff, null, new RectF(canvas.getWidth() - radius, canvas.getHeight() - radius, canvas.getWidth() + radius, canvas.getHeight() + radius), paintFluff);
                    // Draw bars
                    canvas.drawBitmap(bitmap, null, new RectF(canvas.getWidth() - (int) (canvas.getWidth() * 68 / number + 0.5), canvas.getHeight() - (int) (canvas.getWidth() * 68 / number + 0.5), canvas.getWidth() - 10, canvas.getHeight() - 10), paint);
                    // Draw pause/play icon
                    if (AccelerometerMouseClient.connected) {
                        // Draw fluff
                        canvas.drawBitmap(fluff, null, new RectF(-radius, canvas.getHeight() - radius, radius, canvas.getHeight() + radius), paintFluff);
                        if (!AccelerometerMouseClient.paused) {
                            canvas.drawBitmap(paused, null, new RectF(10, canvas.getHeight() - (int) (canvas.getWidth() * 68 / number + 0.5), (int) (canvas.getWidth() * 68 / number + 0.5), canvas.getHeight() - 10), paint);
                        } else {
                            canvas.drawBitmap(playing, null, new RectF(10, canvas.getHeight() - (int) (canvas.getWidth() * 68 / number + 0.5), (int) (canvas.getWidth() * 68 / number + 0.5), canvas.getHeight() - 10), paint);
                        }
                    }
                    holder.unlockCanvasAndPost(canvas);
                } catch (Exception ignore) {
                }
            }
        }

        public void stop() {
            loop = false;
        }

        public void resume() {
            loop = true;
            t = new Thread(this);
            t.start();
        }
    }
}
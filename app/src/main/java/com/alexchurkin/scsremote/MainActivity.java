package com.alexchurkin.scsremote;

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
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.wifi.WifiManager;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/*public class MainActivity extends AppCompatActivity implements SensorEventListener, OnTouchListener {

    public boolean onTouch(View v, MotionEvent event) {
        number = 1280;
        if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == 261 || event.getAction() == 5) {
            if (!middleButton.isPressed()) {
                if (isLeft(event.getX(getPID(event)), event.getY(getPID(event)))) {
                    leftButton.setPressed(true);
                    leftButton.setPID(getPID(event));
                } else if (isRight(event.getX(getPID(event)), event.getY(getPID(event)))) {
                    rightButton.setPressed(true);
                    rightButton.setPID(getPID(event));
                } else if (isMiddle(event)) {
                    if (event.getPointerCount() > 1) {
                        scrollWheel.setPressed(true);
                    } else {
                        middleButton.setPressed(true);
                    }
                }
            }

            //Server info
            if (event.getX(getPID(event)) < (displayWidth / 60.0) + 40 && event.getY(getPID(event)) < (displayHeight / 60.0) + 40) {
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
            }
            //Pause button
            else if (event.getX(getPID(event)) < (int) (displayWidth * 88 / number + 0.5) && event.getY(getPID(event)) > displayHeight - (int) (displayWidth * 88 / number + 0.5)) {
                if (AccelerometerMouseClient.connected) {
                    client.pause(!AccelerometerMouseClient.paused);
                }
            }
        }
        //Mouse buttons
        else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == 262 || event.getAction() == 6) {
            if (event.getPointerCount() > 1) {
                if (leftButton.isPressed() && event.getX(getPID(event)) < displayWidth / 2.0) {
                    leftButton.setPressed(false);
                } else if (rightButton.isPressed() && event.getX(getPID(event)) > displayWidth / 2.0) {
                    rightButton.setPressed(false);
                } else if (scrollWheel.isPressed() && isMiddle(event)) {
                    scrollWheel.setPressed(false);
                }
            } else {
                if (leftButton.isPressed() || rightButton.isPressed() || scrollWheel.isPressed())
                leftButton.setPressed(false);
                rightButton.setPressed(false);
                scrollWheel.setPressed(false);
                middleButton.setPressed(false);
            }
        }
        client.feedTouchFlags(leftButton.isPressed(), rightButton.isPressed());
        return true;
    }

    /**
     * Menu
     */

    /*@Override
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

    /*static public class GraphicsView extends SurfaceView implements Runnable {

        Thread t;
        SurfaceHolder holder;
        boolean loop = false;
        Bitmap mouseNone, mouseAll, mouseLeft, mouseRight, bar0, bar1, bar2, bar3, bar4, bar5, paused, playing, fluff;

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
                    if (leftButton.isPressed() && !rightButton.isPressed()) {
                        bitmapId = R.drawable.left;
                    }
                    if (!leftButton.isPressed() && rightButton.isPressed()) {
                        bitmapId = R.drawable.right;
                    }
                    if (leftButton.isPressed() && rightButton.isPressed()) {
                        bitmapId = R.drawable.all;
                    }
                    if (middleButton.isPressed()) {
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
                    // Draw setPaused/play icon
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
    }
}*/
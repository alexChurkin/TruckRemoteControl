package com.alexchurkin.truckremote;

import android.os.AsyncTask;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;

public class TrackingClient {

    public interface ConnectionListener {
        void onConnectionChanged(boolean isConnected);
    }

    private TCPMessagesSender sender;
    private DatagramSocket clientSocket;
    @NonNull
    private ConnectionListener listener;

    private String ip;
    private int port;
    private boolean running;
    private boolean isPaused, isPausedByUser;

    private float y;
    private boolean breakClicked, gasClicked;
    private boolean turnSignalLeft, turnSignalRight;
    private boolean isParkingBreakEnabled;
    private int buttonLightsState;

    public TrackingClient(String ip, int port, @NonNull ConnectionListener listener) {
        this.ip = ip;
        this.port = port;
        this.listener = listener;
    }

    public boolean isPaused() {
        return isPaused || isPausedByUser;
    }

    public void provideAccelerometerY(float y) {
        this.y = y;
    }

    public void provideMotionState(boolean breakClicked, boolean gasClicked) {
        this.breakClicked = breakClicked;
        this.gasClicked = gasClicked;
    }

    public void provideSignalsInfo(boolean turnSignalLeft, boolean turnSignalRight) {
        this.turnSignalLeft = turnSignalLeft;
        this.turnSignalRight = turnSignalRight;
    }

    public void setParkingBreakEnabled(boolean isEnabled) {
        this.isParkingBreakEnabled = isEnabled;
    }

    public void setLightsState(int mode) {
        this.buttonLightsState = mode;
    }

    public boolean isTwoTurnSignals() {
        return turnSignalLeft && turnSignalRight;
    }

    public boolean isTurnSignalLeft() {
        return turnSignalLeft;
    }

    public boolean isTurnSignalRight() {
        return turnSignalRight;
    }

    public boolean isParkingBreakEnabled() {
        return isParkingBreakEnabled;
    }

    public String getSocketInetHostAddress() {
        return ip;
    }

    public void start() {
        startSender();
    }

    public void start(String ip, int port) {
        this.ip = ip;
        this.port = port;
        startSender();
    }

    public void pauseByUser() {
        this.isPausedByUser = true;
        pause();
    }

    public void resumeByUser() {
        this.isPausedByUser = false;
        resume();
    }

    public void pause() {
        this.isPaused = true;
    }

    public void resume() {
        this.isPaused = false;
    }

    public void restart() {
        stop();
        isPaused = false;
        isPausedByUser = false;
        sender = new TCPMessagesSender();
        sender.execute();
    }

    public void stop() {
        running = false;
        sender = null;
    }

    public void forceUpdate(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    private void startSender() {
        sender = new TCPMessagesSender();
        sender.execute();
    }

    public class TCPMessagesSender extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... voids) {
            Log.d("TAG", "Execution started");
            running = true;

            try {
                clientSocket = new DatagramSocket();
                clientSocket.setSoTimeout(2000);

                try {
                    if (ip == null) {
                        Log.d("TAG", "Sending BROADCAST hello");
                        sendHello();
                    } else {
                        Log.d("TAG", "Sending hello to CONCRETE server");
                        sendHello(InetAddress.getByName(ip), port, false);
                    }
                } catch (SocketTimeoutException e) {
                    running = false;
                    return null;
                }

                listener.onConnectionChanged(true);

                while (running) {
                    byte[] bytes;
                    if (!isPaused && !isPausedByUser) {
                        bytes = (y + "," + breakClicked + "," + gasClicked + ","
                                + turnSignalLeft + "," + turnSignalRight + ","
                                + isParkingBreakEnabled + "," + buttonLightsState).getBytes();
                    } else {
                        bytes = "paused".getBytes();
                    }
                    clientSocket.send(new DatagramPacket(bytes, bytes.length,
                            InetAddress.getByName(ip), port)
                    );
                    if (!isPaused || !isPausedByUser) {
                        sleep(20);
                    } else {
                        sleep(2000);
                    }
                }
                clientSocket.close();
                running = false;
                listener.onConnectionChanged(false);
            } catch (Exception e) {
                Log.d("TAG", "Exception: " + e.getMessage());
                running = false;
                listener.onConnectionChanged(false);
            }
            return null;
        }

        private void sendHello() throws SocketException, IOException {
            sendHello(InetAddress.getByName("255.255.255.255"), 18250, true);
        }

        private void sendHello(InetAddress ipAddress, int port,
                               boolean needDefineIp)
                throws SocketException, IOException {

            clientSocket.setBroadcast(true);
            // Sending HELLO
            byte[] sendData = "TruckRemoteHello".getBytes();
            clientSocket.send(
                    new DatagramPacket(sendData, sendData.length, ipAddress, port));

            // Receiving host address
            byte[] receiveData = new byte[1024];
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            clientSocket.receive(receivePacket);

            clientSocket.setBroadcast(false);

            if (needDefineIp) {
                ip = receivePacket.getAddress().getHostAddress();
            }
        }
    }

    private void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignore) {
        }
    }
}
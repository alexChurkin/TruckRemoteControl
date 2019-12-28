package com.alexchurkin.truckremote;

import android.os.AsyncTask;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;

public class TrackingClient {

    public static final int RECEIVE_TIMEOUT = 300;

    public interface ConnectionListener {
        void onConnectionChanged(boolean isConnected);
    }

    private UDPClientTask sender;
    private DatagramSocket clientSocket;
    @NonNull
    private ConnectionListener listener;

    private String ip;
    private int port;
    private volatile boolean running;
    private volatile boolean isPaused, isPausedByUser;

    private volatile long ffbDuration;

    private volatile float y;
    private volatile boolean breakClicked, gasClicked;
    private volatile boolean turnSignalLeft, turnSignalRight;
    private volatile boolean isParkingBreakEnabled;
    private volatile int lightsState;
    private volatile int hornState;
    private volatile boolean cruiseEnabled;

    public TrackingClient(String ip, int port, @NonNull ConnectionListener listener) {
        this.ip = ip;
        this.port = port;
        this.listener = listener;
    }

    public boolean isPaused() {
        return isPaused || isPausedByUser;
    }

    public boolean isPausedByUser() {
        return isPausedByUser;
    }

    public long getFfbDuration() {
        return ffbDuration;
    }

    public void resetFfbDuration() {
        ffbDuration = 0;
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

    public void setLightsState(int lightsState) {
        this.lightsState = lightsState;
    }

    public void setHornState(int hornState) {
        this.hornState = hornState;
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

    public void toggleCruise() {
        this.cruiseEnabled = !cruiseEnabled;
    }

    public int getLightsState() {
        return lightsState;
    }

    public String getSocketInetHostAddress() {
        return ip;
    }


    public void start(String ip, int port) {
        forceUpdate(ip, port);
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
        sender = new UDPClientTask();
        sender.execute();
    }

    public void stop() {
        running = false;
        sender = null;
        if(clientSocket != null && !clientSocket.isClosed()) {
            clientSocket.close();
            clientSocket = null;
        }
    }

    public void forceUpdate(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    private void startSender() {
        sender = new UDPClientTask();
        sender.execute();
    }

    public class UDPClientTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... voids) {
            Log.d("TAG", "Execution started");
            running = true;

            try {
                clientSocket = new DatagramSocket();
                clientSocket.setSoTimeout(RECEIVE_TIMEOUT);

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
                    boolean paused = isPaused || isPausedByUser;

                    String textToSend = !paused ?
                            y + "," + breakClicked + "," + gasClicked + ","
                                    + turnSignalLeft + "," + turnSignalRight + ","
                                    + isParkingBreakEnabled + "," + lightsState + ","
                                    + hornState + "," + cruiseEnabled
                            : "paused";

                    sendText(textToSend);

                    String serverMsg = receiveText();
                    String[] elements = serverMsg.split(",");
                    ffbDuration = Long.parseLong(elements[0]);

                    if (paused) {
                        sleep(500);
                    }
                }
                clientSocket.close();
                clientSocket = null;
                listener.onConnectionChanged(false);
            } catch (Exception e) {
                Log.d("TAG", "Exception: " + e.toString());
                running = false;
                listener.onConnectionChanged(false);
            }
            return null;
        }

        private void sendHello() throws IOException {
            sendHello(InetAddress.getByName("255.255.255.255"), port, true);
        }

        private void sendHello(InetAddress ipAddress, int port,
                               boolean needDefineIp)
                throws IOException {

            clientSocket.setBroadcast(true);
            // Sends HELLO
            byte[] sendData = ("TruckRemoteHello\n" + getStateInfoString()).getBytes();
            clientSocket.send(
                    new DatagramPacket(sendData, sendData.length, ipAddress, port));

            // Receives host address
            byte[] receiveData = new byte[32];
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            clientSocket.receive(receivePacket);

            clientSocket.setBroadcast(false);
            clientSocket.connect(receivePacket.getSocketAddress());

            if (needDefineIp) {
                ip = receivePacket.getAddress().getHostAddress();
            }
        }

        private void sendText(String text) throws IOException {
            byte[] bytes = text.getBytes();
            clientSocket.send(new DatagramPacket(bytes, bytes.length));
        }

        private String receiveText() throws IOException {
            byte[] receiveData = new byte[32];
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            clientSocket.receive(receivePacket);
            return new String(receiveData, 0, receivePacket.getLength());
        }
    }

    private String getStateInfoString() {
        return turnSignalLeft + "," + turnSignalRight + ","
                + isParkingBreakEnabled + "," + lightsState;
    }

    private void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignore) {
        }
    }
}
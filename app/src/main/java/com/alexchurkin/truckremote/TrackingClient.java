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
    private boolean isPaused;

    private float y;
    private boolean breakClicked, gasClicked;
    private boolean turnSignalLeft, turnSignalRight;
    private boolean isParkingBreakEnabled;

    public TrackingClient(String ip, int port, @NonNull ConnectionListener listener) {
        this.ip = ip;
        this.port = port;
        this.listener = listener;
    }

    public boolean isPaused() {
        return isPaused;
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
        return clientSocket.getInetAddress().getHostAddress();
    }

    public void start() {
        startSender();
    }

    public void start(String ip, int port) {
        this.ip = ip;
        this.port = port;
        startSender();
    }

    public void pauseSending() {
        this.isPaused = true;
    }

    public void resumeSending() {
        this.isPaused = false;
    }

    public void restart() {
        stop();
        isPaused = false;
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
        protected void onPreExecute() {
            running = true;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            Log.d("TAG", "Execution started");

            try {
                clientSocket = new DatagramSocket();
                clientSocket.setSoTimeout(2000);

                try {
                    if (ip == null) {
                        Log.d("TAG", "Sending BROADCAST hello");
                        sendHello(clientSocket);
                    } else {
                        Log.d("TAG", "Sending hello to CONCRETE server");
                        sendHello(clientSocket, InetAddress.getByName(ip), port);
                    }
                } catch (SocketTimeoutException e) {
                    running = false;
                    return null;
                }

                listener.onConnectionChanged(true);

                while (running) {
                    byte[] bytes;
                    if (!isPaused) {
                        sleep(20);
                        bytes = (y + "," + breakClicked + "," + gasClicked + ","
                                + turnSignalLeft + "," + turnSignalRight + ","
                                + isParkingBreakEnabled).getBytes();
                    } else {
                        sleep(1000);
                        bytes = "paused".getBytes();
                    }
                    clientSocket.send(new DatagramPacket(bytes, bytes.length));
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

        private void sendHello(DatagramSocket socket) throws SocketException, IOException {
            sendHello(socket, InetAddress.getByName("255.255.255.255"), 18250);
        }

        private void sendHello(DatagramSocket socket, InetAddress ipAddress, int port)
                throws SocketException, IOException {

            socket.setBroadcast(true);
            // Sending HELLO
            byte[] sendData = "TruckRemoteHello".getBytes();
            socket.send(
                    new DatagramPacket(sendData, sendData.length, ipAddress, port));

            // Receiving host address
            byte[] receiveData = new byte[1024];
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            socket.receive(receivePacket);

            socket.setBroadcast(false);

            ip = receivePacket.getAddress().getHostAddress();
        }
    }

    private void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignore) {
        }
    }
}
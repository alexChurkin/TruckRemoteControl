package com.alexchurkin.scsremote;

import android.os.AsyncTask;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class TrackingClient {

    public interface ConnectionListener {
        void onConnectionChanged(boolean isConnected);
    }

    private TCPMessagesSender sender;
    private Socket socket;
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
        return socket.getInetAddress().getHostAddress();
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
        sender.execute(ip, port + "");
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
        sender.execute(ip, port + "");
    }

    public class TCPMessagesSender extends AsyncTask<String, Void, Void> {


        @Override
        protected void onPreExecute() {
            running = true;
        }

        @Override
        protected Void doInBackground(String... strings) {
            Log.d("TAG", "Execution started");
            String ip = strings[0];
            int port = Integer.parseInt(strings[1]);

            try {
                if (ip == null) {
                    Log.d("TAG", "Autoconnect");
                    try {
                        attemptAutoConnect();
                    } catch (SocketTimeoutException e) {
                        running = false;
                        return null;
                    }
                } else {
                    Log.d("TAG", "Connect ip = " + ip + "; port = " + port);
                    socket = new Socket(ip, port);
                    socket.setTcpNoDelay(true);
                }

                listener.onConnectionChanged(true);

                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);

                while (running) {
                    if (!isPaused) {
                        writer.println(y + "," + breakClicked + "," + gasClicked + ","
                                + turnSignalLeft + "," + turnSignalRight + ","
                                + isParkingBreakEnabled);
                        writer.flush();
                        sleep(20);
                    } else {
                        writer.println("paused");
                        writer.flush();
                        sleep(1000);
                    }
                }
                writer.write("disconnected");
                writer.flush();
                writer.close();
                socket.close();
                running = false;
                listener.onConnectionChanged(false);
            } catch (Exception e) {
                Log.d("TAG", "Exception: " + e.getMessage());
                running = false;
                listener.onConnectionChanged(false);
            }
            return null;
        }

        private void attemptAutoConnect() throws Exception {
            DatagramSocket clientSocket = new DatagramSocket();
            clientSocket.setSoTimeout(2000);
            InetAddress ipAddress = InetAddress.getByName("255.255.255.255");
            // Sending HELLO
            byte[] sendData = "HELLO".getBytes();
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, ipAddress, 18250);
            clientSocket.send(sendPacket);
            // Receiving host address
            byte[] receiveData = new byte[1024];
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            clientSocket.receive(receivePacket);
            clientSocket.close();

            ip = receivePacket.getAddress().getHostAddress();
            socket = new Socket(ip, port);
            socket.setTcpNoDelay(true);
        }
    }

    private void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignore) {
        }
    }
}
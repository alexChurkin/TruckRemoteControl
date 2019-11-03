package com.alexchurkin.scsremote;

import android.os.AsyncTask;

import androidx.annotation.NonNull;

import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;

public class TrackingClient {

    public interface ConnectionListener {
        void onConnectionChanged(boolean isConnected);
    }

    private Socket socket;
    @NonNull
    private ConnectionListener listener;

    private String ip;
    private int port;
    private boolean running;
    private boolean isConnected, isPaused;
    public boolean toastShown;

    private float y;
    private boolean breakClicked, gasClicked;
    private boolean turnSignalLeft, turnSignalRight;

    public TrackingClient(String ip, int port, @NonNull ConnectionListener listener) {
        this.ip = ip;
        this.port = port;
        this.listener = listener;
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isConnected() {
        return isConnected;
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

    public boolean isTwoTurnSignals() {
        return turnSignalLeft && turnSignalRight;
    }

    public boolean isTurnSignalLeft() {
        return turnSignalLeft;
    }

    public boolean isTurnSignalRight() {
        return turnSignalRight;
    }

    public String getSocketInetHostAddress() {
        return socket.getInetAddress().getHostAddress();
    }

    public void start() {
        running = true;
        TCPSessionSender sender = new TCPSessionSender();
        sender.execute(ip, port + "");
    }

    public void startForced() {

    }

    public void pause() {
        this.isPaused = true;
    }

    public void stop() {
        this.isConnected = false;
        this.isPaused = false;
        listener.onConnectionChanged(false);
    }

    public void resume() {
        this.isPaused = false;
    }

    public void forceUpdate(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }


    public class TCPSessionSender extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... strings) {
            String ip = strings[0];
            int port = Integer.parseInt(strings[1]);

            try {
                DatagramSocket clientSocket = new DatagramSocket();
                InetAddress IPAddress = InetAddress.getByName("255.255.255.255");
                byte[] receiveData = new byte[1024];
                // Sending HELLO
                byte[] sendData = "HELLO".getBytes();
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, 18250);
                clientSocket.send(sendPacket);
                // Receiving ACK
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                clientSocket.receive(receivePacket);
                clientSocket.close();

                socket = new Socket(receivePacket.getAddress().getHostAddress(), port);
                socket.setTcpNoDelay(true);

                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                isConnected = true;
                listener.onConnectionChanged(true);

                while (isConnected) {
                    if (!isPaused) {
                        writer.println(y + "," + breakClicked + "," + gasClicked + ","
                                + turnSignalLeft + "," + turnSignalRight);
                        writer.flush();
                        sleep(20);
                    } else {
                        writer.println("paused");
                        writer.flush();
                        sleep(800);
                    }
                }
                writer.close();
                socket.close();
                listener.onConnectionChanged(false);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }
    }

    private void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignore) {
        }
    }
}
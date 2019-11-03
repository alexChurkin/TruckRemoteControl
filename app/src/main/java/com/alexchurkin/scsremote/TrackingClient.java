package com.alexchurkin.scsremote;

import android.os.AsyncTask;

import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;

public class TrackingClient {

    public interface ConnectionListener {
        void onConnectionChanged(boolean isConnected);
    }

    public Socket socket;
    private ConnectionListener listener;

    private String ip;
    private int port;
    private boolean isConnected, isPaused;

    public float y;
    private boolean breakClicked, gasClicked;
    private boolean turnSignalLeft, turnSignalRight;

    public TrackingClient(String ip, int port, ConnectionListener listener) {
        this.ip = ip;
        this.port = port;
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

    public void start() {
        TCPSessionSender sender = new TCPSessionSender();
        sender.execute(ip, port + "");
    }

    public void pause() {

    }

    public void stop() {

    }

    public void resume() {

    }


    public class TCPSessionSender extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... strings) {
            String ip = strings[1];
            int port = Integer.parseInt(strings[2]);

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

                Socket socket = new Socket(receivePacket.getAddress().getHostAddress(), port);
                socket.setTcpNoDelay(true);

                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                isConnected = true;
                listener.onConnectionChanged(true);

                while (isConnected) {
                    if (!isPaused) {
                        writer.println(y + "," + breakClicked + "," + gasClicked + ","
                                + turnSignalLeft + "," + turnSignalRight);
                        writer.flush();
                    } else {
                        writer.println("paused");
                        writer.flush();
                        sleep(500);
                    }
                    sleep(20);
                }
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
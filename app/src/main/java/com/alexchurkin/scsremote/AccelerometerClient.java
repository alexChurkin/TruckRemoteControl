package com.alexchurkin.scsremote;

import android.util.Log;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;

//Client object
public class AccelerometerClient {

    public interface ConnectionListener {
        void onConnectionChanged(boolean isConnected);
    }

    public static boolean running = false, paused = false;
    public static boolean connected = false;
    public static boolean toastShown = true;

    private String ip;
    public int port;
    public Socket socket;
    private ConnectionListener listener;


    public float x = 0, y = 0, z = 0;
    private boolean breakClickFlag, gasClickFlag;
    public boolean turnSignalLeft, turnSignalRight;


    public boolean sentJam = true;


    public AccelerometerClient(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    public void setConnectionListener(ConnectionListener listener) {
        this.listener = listener;
    }

    private void start() {
        paused = false;
        // Client Thread
        Runnable clientRunnable = () -> {
            // Connect to server
            try {
                Log.d("Client", "Connecting...");
                socket.setTcpNoDelay(true);
                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                while (running && connected) {
                    if (sentJam == false) {
                        writer = new PrintWriter(socket.getOutputStream(), true);
                        writer.println("jamjamjam");
                        writer.flush();
                        sentJam = true;
                        continue;
                    }
                    if (!paused) {
                        writer = new PrintWriter(socket.getOutputStream(), true);
                        writer.println(x + "," + y + "," + z + ","
                                + breakClickFlag + "," + gasClickFlag + ","
                                + turnSignalLeft + "," + turnSignalRight);
                        writer.flush();
                    } else {
                        writer = new PrintWriter(socket.getOutputStream(), true);
                        writer.println("paused");
                        writer.flush();
                        sleep(500);
                    }
                    sleep(20);
                }
                writer.close();
                socket.close();
                connected = false;
                listener.onConnectionChanged(false);
                toastShown = false;
            } catch (Exception e) {
                Log.d("Client IO", "Major Error: " + e.getMessage());
            }
        };
        Thread clientThread = new Thread(clientRunnable);
        clientThread.start();
        heartbeat();
    }

    private void heartbeat() {
        Runnable r = () -> {
            String ip = socket.getInetAddress().getHostAddress();
            while (running) {
                try {
                    Thread.sleep(1000);
                    Socket s = new Socket(ip, socket.getPort());
                    s.close();
                    connected = true;
                    listener.onConnectionChanged(true);
                } catch (IOException e) {
                    connected = false;
                    listener.onConnectionChanged(false);
                    toastShown = false;
                    break;
                } catch (InterruptedException e) {

                }
            }
            connected = false;
            listener.onConnectionChanged(false);
        };
        new Thread(r).start();
    }

    public void forceUpdate(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    public void run(final boolean FORCED) {
        running = true;
        signalStrengthUpdate();
        Runnable r = () -> {
            try {
                if (!FORCED) {
                    try {
                        System.out.println("Client Started");
                        DatagramSocket clientSocket = new DatagramSocket();
                        InetAddress IPAddress = InetAddress.getByName("255.255.255.255");
                        byte[] sendData = new byte[1024];
                        byte[] receiveData = new byte[1024];
                        // Send HELLO
                        sendData = "HELLO".getBytes();
                        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, 18250);
                        clientSocket.send(sendPacket);
                        // Recieve ACK
                        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                        clientSocket.receive(receivePacket);
                        socket = new Socket(receivePacket.getAddress().getHostAddress(), port);
                        clientSocket.close();
                        connected = true;
                        listener.onConnectionChanged(true);
                        toastShown = false;
                        start();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    socket = new Socket(ip, port);
                    connected = true;
                    listener.onConnectionChanged(true);
                    toastShown = false;
                    start();
                }
            } catch (Exception e) {
                Log.d("Socket", "Error " + e.toString());
            }
        };
        new Thread(r).start();
    }

    private void signalStrengthUpdate() {
        Runnable r = () -> {
            while (running) {
                if (!paused) {
                    MainActivity.dBm = MainActivity.wifi.getConnectionInfo().getRssi();
                }
                try {
                    Thread.sleep(1000);
                } catch (Exception ignore) {
                }
            }
        };
        new Thread(r).start();
    }

    public void feedAccelerometerValues(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public void feedTouchFlags(boolean breakClickFlag, boolean gasClickFlag) {
        this.breakClickFlag = breakClickFlag;
        this.gasClickFlag = gasClickFlag;
    }

    public void feedSignals(boolean turnSignalLeft, boolean turnSignalRight) {
        this.turnSignalLeft = turnSignalLeft;
        this.turnSignalRight = turnSignalRight;
    }

    private void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignore) {
        }
    }

    public void stop() {
        connected = false;
        listener.onConnectionChanged(false);
        running = false;
    }

    public void sendJamSignal() {
        if (!connected)
            return;
        Runnable timer = () -> {
            long startTime = System.currentTimeMillis();
            while (System.currentTimeMillis() - startTime < 2500) {

            }
            sentJam = true;
        };
        new Thread(timer).start();
        sentJam = false;
        while (sentJam == false) {

        }
    }

    public void setPaused(boolean b) {
        AccelerometerClient.paused = b;
        AccelerometerClient.toastShown = true;
    }

    public void overrideSocket(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }
}
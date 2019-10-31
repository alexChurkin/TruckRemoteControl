package com.alexchurkin.scsremote;

import android.util.Log;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;

//Client object
public class AccelerometerMouseClient {
    private String ip;
    public int port;
    public Socket socket;
    public static boolean running = false, paused = false;
    ;
    public float x = 0, y = 0, z = 0;
    private boolean leftClickFlag = false, rightClickFlag = false, middleFlag = false, scrollFlag = false;
    public static String keyboardData = "";
    public static boolean connected = false;
    public static boolean toastShown = true;
    public boolean sentJam = true;

    public AccelerometerMouseClient(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    private void start() {
        paused = false;
        // Client Thread
        Runnable clientRunnable = new Runnable() {
            public void run() {
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
                            writer.println(x + "," + y + "," + z + "," + leftClickFlag + "," + rightClickFlag + "," + middleFlag + "," + scrollFlag);
                            writer.flush();
                        } else {
                            writer = new PrintWriter(socket.getOutputStream(), true);
                            writer.println("paused:" + keyboardData);
                            keyboardData = "";
                            writer.flush();
                            sleep(500);
                        }
                        sleep(20);
                    }
                    writer.close();
                    socket.close();
                    connected = false;
                    toastShown = false;
                } catch (Exception e) {
                    Log.d("Client IO", "Major Error: " + e.getMessage());
                }
            }
        };
        Thread clientThread = new Thread(clientRunnable);
        clientThread.start();
        heartbeat();
    }

    private void heartbeat() {
        Runnable r = new Runnable() {
            public void run() {
                String ip = socket.getInetAddress().getHostAddress();
                while (running) {
                    try {
                        Thread.sleep(1000);
                        Socket s = new Socket(ip, socket.getPort());
                        s.close();
                        connected = true;
                    } catch (IOException e) {
                        connected = false;
                        toastShown = false;
                        break;
                    } catch (InterruptedException e) {

                    }
                }
                connected = false;
            }
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
        Runnable r = new Runnable() {
            public void run() {
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
                            toastShown = false;
                            start();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    } else {
                        socket = new Socket(ip, port);
                        connected = true;
                        toastShown = false;
                        start();
                    }
                } catch (Exception e) {
                    Log.d("Socket", "Error " + e.toString());
                }
            }
        };
        new Thread(r).start();
    }

    private void signalStrengthUpdate() {
        Runnable r = new Runnable() {

            public void run() {
                while (running) {
                    if (!paused) {
                        MainActivity.dBm = MainActivity.wifi.getConnectionInfo().getRssi();
                    }
                    try {
                        Thread.sleep(1000);
                    } catch (Exception e) {
                    }
                }
            }

        };
        new Thread(r).start();
    }

    public boolean isRunning() {
        return running;
    }

    public void feedAccelerometerValues(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public void feedTouchFlags(boolean leftClickFlag, boolean rightClickFlag, boolean middleFlag, boolean scrollFlag) {
        this.leftClickFlag = leftClickFlag;
        this.rightClickFlag = rightClickFlag;
        this.middleFlag = middleFlag;
        this.scrollFlag = scrollFlag;
    }

    private void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
        }
    }

    public void stop() {
        connected = false;
        running = false;
    }

    public void sendJamSignal() {
        if (!connected)
            return;
        Runnable timer = new Runnable() {

            public void run() {
                long startTime = System.currentTimeMillis();
                while (System.currentTimeMillis() - startTime < 2500) {

                }
                sentJam = true;
            }

        };
        new Thread(timer).start();
        sentJam = false;
        while (sentJam == false) {

        }
    }

    public void pause(boolean b) {
        AccelerometerMouseClient.paused = b;
        AccelerometerMouseClient.toastShown = true;
    }

    public void overrideSocket(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }
}

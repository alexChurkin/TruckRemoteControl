package com.alexchurkin.truckremote;

//Client object
/*public class AccelerometerClient {

    public static boolean running = false, paused = false;
    public static boolean connected = false;
    public static boolean toastShown = true;


    private boolean breakClickFlag, gasClickFlag;
    public boolean turnSignalLeft, turnSignalRight;


    public boolean sentJam = true;

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
                } catch (InterruptedException ignore) {
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
                        byte[] sendData;
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
}*/
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

    private static final int RECEIVE_TIMEOUT = 600;

    public interface ConnectionListener {
        int NOT_CONNECTED = 0;
        int CONNECTED = 2;

        void onConnectionChanged(int connectionState);

        void onParkingUpdate(boolean isParking);

        void onLightsUpdate(int lightsMode);

        void onBlinkersUpdate(boolean leftBlinker, boolean rightBlinker);
    }

    private UDPClientTask sender;
    private DatagramSocket clientSocket;
    @NonNull
    private final ConnectionListener listener;

    private String ip;
    private int port = 18250;
    private volatile boolean running;
    private volatile boolean isPaused, isPausedByUser;

    //Data from user input
    private volatile float y;
    private volatile boolean breakPressed, gasPressed;
    private volatile boolean turnLeftClick, turnRightClick, emergencySignalClick;
    private volatile boolean parkingBreakClick;
    private volatile boolean lightsClick;
    private volatile int hornState;
    private volatile boolean cruiseSlide;

    //Data received from server
    private volatile boolean telWasEngineOn;
    private volatile boolean telWasParking;
    private volatile boolean telWasRightBlinker;
    private volatile boolean telWasLeftBlinker;
    private volatile int telPrevLightsState;


    private volatile long ffbDuration;


    public TrackingClient(String ip, @NonNull ConnectionListener listener) {
        this.ip = ip;
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

    public void provideMotionState(boolean breakPressed, boolean gasPressed) {
        this.breakPressed = breakPressed;
        this.gasPressed = gasPressed;
    }

    public void changeHornState(int hornState) {
        this.hornState = hornState;
    }

    public void clickParkingBreak() {
        this.parkingBreakClick = !parkingBreakClick;
    }

    public void clickLights() {
        this.lightsClick = !lightsClick;
    }

    public void slideCruise() {
        this.cruiseSlide = !cruiseSlide;
    }

    public void clickLeftBlinker() {
        this.turnLeftClick = !turnLeftClick;
    }

    public void clickRightBlinker() {
        this.turnRightClick = !turnRightClick;
    }

    public void clickEmergencySignal() {
        this.emergencySignalClick = !emergencySignalClick;
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
        if (clientSocket != null && !clientSocket.isClosed()) {
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

    public class UDPClientTask extends AsyncTask<Void, Integer, Void> {

        public UDPClientTask() {
            super();
        }

        @Override
        protected Void doInBackground(Void... voids) {
            Log.d("TAG", "Execution started");
            running = true;

            try {
                clientSocket = new DatagramSocket();
                clientSocket.setSoTimeout(RECEIVE_TIMEOUT);

                //Hello's
                try {
                    if (ip == null) {
                        Log.d("TAG", "Sending BROADCAST hello");
                        sendHello();
                    } else {
                        Log.d("TAG", "Sending hello to the SPECIFIC server");
                        sendHello(InetAddress.getByName(ip), port, false);
                    }
                } catch (SocketTimeoutException e) {
                    running = false;
                    return null;
                }

                listener.onConnectionChanged(ConnectionListener.CONNECTED);

                //Here we know that hello from server received and we can send data
                int tries = 0;
                while (running) {
                    boolean paused = isPaused || isPausedByUser;

                    try {
                        sendText(makeStringToSend(paused));
                        if (!paused) processServerResponse(receiveText());
                        else sleep500();

                    } catch (SocketTimeoutException e) {
                        e.printStackTrace();
                        if (++tries > 2) {
                            running = false;
                        }
                        continue;
                    }
                }
                clientSocket.close();
                clientSocket = null;
                listener.onConnectionChanged(ConnectionListener.NOT_CONNECTED);
            } catch (Exception e) {
                Log.d("TAG", "Exception: " + e.toString());
                running = false;
                listener.onConnectionChanged(ConnectionListener.NOT_CONNECTED);
            }
            return null;
        }


        /* Helpful local methods */
        private String makeStringToSend(boolean paused) {
            return !paused ?
                    y + "," + breakPressed + "," + gasPressed + ","
                            + turnLeftClick + "," + turnRightClick + "," + emergencySignalClick + ","
                            + parkingBreakClick + "," + lightsClick + ","
                            + hornState + "," + cruiseSlide
                    : "paused";
        }

        private void processServerResponse(String serverResponse) {
            String[] elements = serverResponse.split(",");

            boolean newTelIsEngineOn = Boolean.parseBoolean(elements[0]);
            if (newTelIsEngineOn != telWasEngineOn) {
                telWasEngineOn = newTelIsEngineOn;
                //TODO
            }

            //Parking
            boolean newTelIsParking = Boolean.parseBoolean(elements[1]);
            if (newTelIsParking != telWasParking) {
                telWasParking = newTelIsParking;
                listener.onParkingUpdate(newTelIsParking);
            }

            //Blinkers
            boolean newTelLeftBlinker = Boolean.parseBoolean(elements[2]);
            boolean newTelRightBlinker = Boolean.parseBoolean(elements[3]);

            if (newTelLeftBlinker != telWasLeftBlinker || newTelRightBlinker != telWasRightBlinker) {
                telWasLeftBlinker = newTelLeftBlinker;
                telWasRightBlinker = newTelRightBlinker;

                listener.onBlinkersUpdate(newTelLeftBlinker, newTelRightBlinker);
            }

            //Lights
            int newTelLightsState = Integer.parseInt(elements[4]);
            if (newTelLightsState != telPrevLightsState) {
                telPrevLightsState = newTelLightsState;
                listener.onLightsUpdate(newTelLightsState);
            }

            ffbDuration = Long.parseLong(elements[5]);
        }

        /* Helpful network operations */
        private void sendHello() throws IOException {
            sendHello(InetAddress.getByName("255.255.255.255"), port, true);
        }

        private void sendHello(InetAddress ipAddress, int port,
                               boolean needDefineIp)
                throws IOException {

            clientSocket.setBroadcast(true);
            // Sends HELLO
            byte[] sendData = ("TruckRemoteHello").getBytes();
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
            byte[] receiveData = new byte[64];
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            clientSocket.receive(receivePacket);
            return new String(receiveData, 0, receivePacket.getLength());
        }
    }

    private void sleep500() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException ignore) {
        }
    }
}
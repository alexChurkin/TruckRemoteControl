package com.alexchurkin.truckremote;

import android.os.AsyncTask;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.Locale;

public class TrackingClient {

    private static final int RECEIVE_TIMEOUT = 600;

    public interface ConnectionListener {
        int NOT_CONNECTED = 0;
        int CONNECTED = 2;

        void onConnectionChanged(int connectionState);

        void onParkingUpdate(boolean isParking);

        void onLightsUpdate(int lightsMode);

        void onBlinkersUpdate(boolean leftBlinker, boolean rightBlinker);

        //Called when any of these values changes (sent by server 1.3+)
        void onVehicleStateUpdate(boolean engineOn, boolean trailerAttached,
                                  boolean wipersOn, boolean beaconOn);

        //Analog pedals require Y and Z axes enabled in vJoy on the server
        void onAnalogPedalsAvailabilityChanged(boolean available);
    }

    //Additional actions; their order must match the order on the server
    public static final int ACTION_ENGINE = 0;
    public static final int ACTION_TRAILER = 1;
    public static final int ACTION_ACTIVATE = 2;
    public static final int ACTION_WIPERS = 3;
    public static final int ACTION_DIFF_LOCK = 4;
    public static final int ACTION_LIFT_AXLE = 5;
    public static final int ACTION_BEACON = 6;
    public static final int ACTION_LIGHT_HORN = 7;
    private static final int ACTIONS_COUNT = 8;

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
    private volatile float breakLevel, gasLevel;
    //Every click increases action's counter, so the server can't miss a click
    private final int[] actionCounters = new int[ACTIONS_COUNT];
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
    private boolean telVehicleStateReceived;
    private boolean telWasTrailerAttached, telWasWipersOn, telWasBeaconOn;
    private volatile boolean telWasAnalogAvailable;


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

    //Pressed values are used by keyboard emulation, levels (0..1) by analog axes
    public void provideMotionState(boolean breakPressed, boolean gasPressed,
                                   float breakLevel, float gasLevel) {
        this.breakPressed = breakPressed;
        this.gasPressed = gasPressed;
        this.breakLevel = breakLevel;
        this.gasLevel = gasLevel;
    }

    public void clickAction(int action) {
        synchronized (actionCounters) {
            actionCounters[action]++;
        }
    }

    public boolean isAnalogPedalsAvailable() {
        return telWasAnalogAvailable;
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
            //New server may be different
            telVehicleStateReceived = false;
            telWasAnalogAvailable = false;

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
            if (paused) return "paused";

            StringBuilder builder = new StringBuilder(128)
                    .append(y).append(',').append(breakPressed).append(',').append(gasPressed).append(',')
                    .append(turnLeftClick).append(',').append(turnRightClick).append(',')
                    .append(emergencySignalClick).append(',')
                    .append(parkingBreakClick).append(',').append(lightsClick).append(',')
                    .append(hornState).append(',').append(cruiseSlide).append(',')
                    .append(String.format(Locale.ROOT, "%.3f,%.3f", gasLevel, breakLevel));
            synchronized (actionCounters) {
                for (int counter : actionCounters) builder.append(',').append(counter);
            }
            return builder.toString();
        }

        private void processServerResponse(String serverResponse) {
            String[] elements = serverResponse.split(",");

            boolean newTelIsEngineOn = Boolean.parseBoolean(elements[0]);
            boolean newEngineStateChanged = newTelIsEngineOn != telWasEngineOn;
            telWasEngineOn = newTelIsEngineOn;

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

            //Additional state from newer servers
            boolean newTrailerAttached = elements.length > 6 && "1".equals(elements[6]);
            boolean newWipersOn = elements.length > 7 && "1".equals(elements[7]);
            boolean newBeaconOn = elements.length > 8 && "1".equals(elements[8]);
            boolean newAnalogAvailable = elements.length > 9 && "1".equals(elements[9]);

            if (!telVehicleStateReceived || newEngineStateChanged
                    || newTrailerAttached != telWasTrailerAttached
                    || newWipersOn != telWasWipersOn || newBeaconOn != telWasBeaconOn) {
                telVehicleStateReceived = true;
                telWasTrailerAttached = newTrailerAttached;
                telWasWipersOn = newWipersOn;
                telWasBeaconOn = newBeaconOn;
                listener.onVehicleStateUpdate(telWasEngineOn, newTrailerAttached,
                        newWipersOn, newBeaconOn);
            }

            if (newAnalogAvailable != telWasAnalogAvailable) {
                telWasAnalogAvailable = newAnalogAvailable;
                listener.onAnalogPedalsAvailabilityChanged(newAnalogAvailable);
            }
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
            byte[] receiveData = new byte[256];
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

    private void sleep100() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException ignore) {
        }
    }
}
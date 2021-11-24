package com.alexchurkin.truckremote;

import androidx.annotation.NonNull;

import com.alexchurkin.truckremote.helpers.LogMan;
import com.alexchurkin.truckremote.helpers.TaskRunner;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.Calendar;

public class TrackingClient {

    public static final int NOT_CONNECTED = 0;
    public static final int CONNECTED = 2;

    public interface TelemetryListener {

        void onConnectionChanged(int connectionState);

        void onEngineUpdate(boolean isStarted);

        void onParkingUpdate(boolean isParking);

        void onLightsUpdate(int lightsMode);

        void onBlinkersUpdate(boolean leftBlinker, boolean rightBlinker);

        void onWipersUpdate(boolean isWipers);

        void onBeaconUpdate(boolean isBeacon);

        void onLowFuelUpdate(boolean isLowFuel);

        void onFuelUpdate(int percentage);

        void onTruckDamageUpdate(int damage);

        void onTrailerUpdate(boolean isAttached, int trailerDamage, int cargoDamage);
    }

    private final TaskRunner taskRunner = new TaskRunner();
    @NonNull
    private final TelemetryListener listener;

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
    private volatile boolean engineClick;
    private volatile boolean trailerClick;
    private volatile boolean wipersClick;
    private volatile boolean beaconClick;

    //Data received from server
    private volatile boolean telWasEngineOn;
    private volatile boolean telWasParking;
    private volatile boolean telWasRightBlinker;
    private volatile boolean telWasLeftBlinker;
    private volatile int telPrevLightsState;

    private volatile boolean telWasWipers;
    private volatile boolean telWasBeacon;
    private volatile boolean telWasLowFuel;
    private volatile int telPrevFuelLevel;

    private volatile int telPrevTruckDamage;
    private volatile boolean telWasTrailerAttached;
    private volatile int telPrevTrailerDamage;
    private volatile int telPrevCargoDamage;

    private volatile long ffbDuration;


    public TrackingClient(String ip, @NonNull TelemetryListener listener) {
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

    public synchronized void clickParkingBreak() {
        this.parkingBreakClick = !parkingBreakClick;
    }

    public synchronized void clickLights() {
        this.lightsClick = !lightsClick;
    }

    public synchronized void slideCruise() {
        this.cruiseSlide = !cruiseSlide;
    }

    public synchronized void clickLeftBlinker() {
        this.turnLeftClick = !turnLeftClick;
    }

    public synchronized void clickRightBlinker() {
        this.turnRightClick = !turnRightClick;
    }

    public synchronized void clickEmergencySignal() {
        this.emergencySignalClick = !emergencySignalClick;
    }

    public synchronized void clickEngine() {
        this.engineClick = !engineClick;
    }

    public synchronized void clickTrailer() {
        this.trailerClick = !trailerClick;
    }

    public synchronized void clickWipers() {
        this.wipersClick = !wipersClick;
    }

    public synchronized void clickBeacon() {
        this.beaconClick = !beaconClick;
    }


    public String getSocketInetHostAddress() {
        return ip;
    }


    public void start(String ip, int port) {
        forceUpdate(ip, port);
        taskRunner.executeAsync(new UDPClientTask());
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

        taskRunner.executeAsync(new UDPClientTask());
    }

    public void stop() {
        running = false;
    }

    public void forceUpdate(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }


    private class UDPClientTask implements Runnable {

        private DatagramSocket clientSocket;
        private static final int RECEIVE_TIMEOUT = 600;

        @Override
        public void run() {
            running = true;
            LogMan.logD("Execution started");

            try {
                clientSocket = new DatagramSocket();
                clientSocket.setSoTimeout(RECEIVE_TIMEOUT);

                //Hello's
                try {
                    if (ip == null) {
                        LogMan.logD("Sending BROADCAST hello");
                        sendHello();
                    } else {
                        LogMan.logD("Sending hello to the SPECIFIC server");
                        sendHello(InetAddress.getByName(ip), port, false);
                    }
                } catch (SocketTimeoutException e) {
                    running = false;
                    return;
                }

                taskRunner.postToMainThread(() ->
                        listener.onConnectionChanged(TrackingClient.CONNECTED));

                //Here we know that hello from server received and we can send data
                int tries = 0;
                while (running) {
                    boolean paused = isPaused || isPausedByUser;

                    try {
                        long tStart = Calendar.getInstance().getTimeInMillis();

                        sendText(makeStringToSend(paused));

                        long tEnd1 = Calendar.getInstance().getTimeInMillis();
                        LogMan.logD("duration 1 = " + (tEnd1 - tStart));

                        String response = receiveText();

                        long tEnd2 = Calendar.getInstance().getTimeInMillis();
                        LogMan.logD("duration 2 = " + (tEnd2 - tEnd1));

                        if (!paused) processServerResponse(response);
                        else sleep500();

                    } catch (SocketTimeoutException e) {
                        LogMan.logD("Socket timeout: " + e.getMessage());
                        e.printStackTrace();
                        if (++tries > 2) {
                            running = false;
                        }
                    }
                }
                clientSocket.close();
                clientSocket = null;

                taskRunner.postToMainThread(() ->
                        listener.onConnectionChanged(TrackingClient.NOT_CONNECTED));
            } catch (Exception e) {
                LogMan.logD("Exception: " + e.toString());
                running = false;
                taskRunner.postToMainThread(() ->
                        listener.onConnectionChanged(TrackingClient.NOT_CONNECTED));
            }
        }

        /* Helpful local methods */
        private String makeStringToSend(boolean paused) {
            //TODO Add new values to this string
            return !paused ?
                    y + "," + breakPressed + "," + gasPressed + ","
                            + turnLeftClick + "," + turnRightClick + "," + emergencySignalClick + ","
                            + parkingBreakClick + "," + lightsClick + ","
                            + hornState + "," + cruiseSlide
                    : "paused";
        }

        private void processServerResponse(String serverResponse) {
            String[] elements = serverResponse.split(",");

            boolean telemetryActive = Boolean.parseBoolean(elements[0]);
            if (!telemetryActive) return;

            boolean newTelIsEngineOn = Boolean.parseBoolean(elements[1]);
            if (newTelIsEngineOn != telWasEngineOn) {
                telWasEngineOn = newTelIsEngineOn;
                taskRunner.postToMainThread(() -> listener.onEngineUpdate(newTelIsEngineOn));
            }

            //Parking
            boolean newTelIsParking = Boolean.parseBoolean(elements[2]);
            if (newTelIsParking != telWasParking) {
                telWasParking = newTelIsParking;
                taskRunner.postToMainThread(() -> listener.onParkingUpdate(newTelIsParking));
            }

            //Blinkers
            boolean newTelLeftBlinker = Boolean.parseBoolean(elements[3]);
            boolean newTelRightBlinker = Boolean.parseBoolean(elements[4]);

            if (newTelLeftBlinker != telWasLeftBlinker || newTelRightBlinker != telWasRightBlinker) {
                telWasLeftBlinker = newTelLeftBlinker;
                telWasRightBlinker = newTelRightBlinker;

                taskRunner.postToMainThread(() ->
                        listener.onBlinkersUpdate(newTelLeftBlinker, newTelRightBlinker));
            }

            //Lights
            int newTelLightsState = Integer.parseInt(elements[5]);
            if (newTelLightsState != telPrevLightsState) {
                telPrevLightsState = newTelLightsState;
                taskRunner.postToMainThread(() -> listener.onLightsUpdate(newTelLightsState));
            }

            /* New info (23.11.21) */

            boolean newTelIsWipers = Boolean.parseBoolean(elements[6]);
            if (newTelIsWipers != telWasWipers) {
                telWasWipers = newTelIsWipers;
                taskRunner.postToMainThread(() -> listener.onWipersUpdate(newTelIsWipers));
            }

            boolean newTelIsBeacon = Boolean.parseBoolean(elements[7]);
            if (newTelIsBeacon != telWasBeacon) {
                telWasBeacon = newTelIsBeacon;
                taskRunner.postToMainThread(() -> listener.onBeaconUpdate(newTelIsBeacon));
            }


            boolean newIsLowFuel = Boolean.parseBoolean(elements[8]);
            if (newIsLowFuel != telWasLowFuel) {
                telWasLowFuel = newIsLowFuel;
                taskRunner.postToMainThread(() -> listener.onLowFuelUpdate(newIsLowFuel));
            }

            int newFuelLevel = Integer.parseInt(elements[9]);
            if (newFuelLevel != telPrevFuelLevel) {
                telPrevFuelLevel = newFuelLevel;
                taskRunner.postToMainThread(() -> listener.onFuelUpdate(newFuelLevel));
            }

            int newTelTruckDamage = Integer.parseInt(elements[10]);
            if (newTelTruckDamage != telPrevTruckDamage) {
                telPrevTruckDamage = newTelTruckDamage;
                taskRunner.postToMainThread(() -> listener.onTruckDamageUpdate(newTelTruckDamage));
            }

            boolean trailerUpdated = false;

            boolean newTelTrailerAttached = Boolean.parseBoolean(elements[11]);
            if (newTelTrailerAttached != telWasTrailerAttached) {
                telWasTrailerAttached = newTelTrailerAttached;
                trailerUpdated = true;
            }

            int newTelTrailerDamage = Integer.parseInt(elements[12]);
            if (newTelTrailerDamage != telPrevTrailerDamage) {
                telPrevTrailerDamage = newTelTrailerDamage;
                trailerUpdated = true;
            }

            int newTelCargoDamage = Integer.parseInt(elements[13]);
            if (newTelCargoDamage != telPrevCargoDamage) {
                telPrevCargoDamage = newTelCargoDamage;
                trailerUpdated = true;
            }

            if (trailerUpdated) {
                taskRunner.postToMainThread(() ->
                        listener.onTrailerUpdate(newTelTrailerAttached,
                                newTelTrailerDamage, newTelCargoDamage));
            }

            ffbDuration = Long.parseLong(elements[14]);
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

        private void sleep500() {
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignore) {
            }
        }
    }
}
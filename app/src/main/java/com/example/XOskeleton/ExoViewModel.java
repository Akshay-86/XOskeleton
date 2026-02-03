package com.example.XOskeleton;

import android.annotation.SuppressLint;
import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ExoViewModel extends AndroidViewModel {

    // --- Live Data (UI watches these) ---
    public final MutableLiveData<String> statusMessage = new MutableLiveData<>();
    public final MutableLiveData<Boolean> isConnected = new MutableLiveData<>(false);
    public final MutableLiveData<JSONObject> liveDataPacket = new MutableLiveData<>();

    // Chart Data
    public final MutableLiveData<Float> voltage = new MutableLiveData<>();
    public final MutableLiveData<Float> current = new MutableLiveData<>();
    public final MutableLiveData<Float> speed = new MutableLiveData<>();

    // --- Internals ---
    private BluetoothSocket socket;
    private volatile boolean isRunning = false;
    private Thread connectionThread;

    // Logging Tools
    private final DataLogger logger;
    private final StringBuilder csvBuilder = new StringBuilder();
    private final SimpleDateFormat csvTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
    private final Date dateObject = new Date();

    // Latency Metrics
    private double minClockOffset = Double.MAX_VALUE;
    private long pingStartTime = 0;
    private double lastCalculatedRTT = 0;

    public ExoViewModel(@NonNull Application application) {
        super(application);
        // Initialize Logger with Application Context
        logger = new DataLogger(application);
    }

    // --- Connection Logic ---

    @SuppressLint("MissingPermission")
    public void connect(String macAddress) {
        if (isRunning || macAddress == null) return;

        statusMessage.setValue("Connecting...");

        connectionThread = new Thread(() -> {
            try {
                BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(macAddress);

                // Try Port 1
                Method m = device.getClass().getMethod("createRfcommSocket", int.class);
                socket = (BluetoothSocket) m.invoke(device, 1);

                if (socket != null) {
                    socket.connect();

                    // Connected!
                    isRunning = true;
                    isConnected.postValue(true);
                    statusMessage.postValue("Connected!");

                    // Reset Metrics
                    minClockOffset = Double.MAX_VALUE;
                    pingStartTime = 0;
                    lastCalculatedRTT = 0;

                    startReadingLoop();
                }
            } catch (Exception e) {
                disconnect();
                statusMessage.postValue("Connection Failed");
            }
        });
        connectionThread.start();
    }

    public void disconnect() {
        isRunning = false;
        try {
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        isConnected.postValue(false);
        statusMessage.postValue("Disconnected");
    }

    // --- The Main Data Loop ---

    private void startReadingLoop() {
        byte[] lengthHeader = new byte[2];
        long sessionStartTime = System.currentTimeMillis();
        long totalBytesReceived = 0;
        long localPacketCount = 0;
        int loopCounter = 0;

        try {
            InputStream inputStream = socket.getInputStream();
            OutputStream outputStream = socket.getOutputStream();

            while (isRunning) {
                // 1. Send Ping (Every ~20 packets)
                loopCounter++;
                if (loopCounter >= 20) {
                    loopCounter = 0;
                    if (pingStartTime == 0) {
                        try {
                            outputStream.write(1);
                            outputStream.flush();
                            pingStartTime = System.currentTimeMillis();
                        } catch (IOException ignored) {}
                    }
                }

                // 2. Read Header
                if (readExactly(inputStream, lengthHeader, 2) == -1) break;
                int payloadSize = ((lengthHeader[0] & 0xFF) << 8) | (lengthHeader[1] & 0xFF);

                // 3. Read Payload
                byte[] payload = new byte[payloadSize];
                if (readExactly(inputStream, payload, payloadSize) == -1) break;

                // 4. Metrics & Logging
                String jsonString = new String(payload, StandardCharsets.UTF_8);
                localPacketCount++;
                totalBytesReceived += (payloadSize + 2);

                try {
                    JSONObject json = new JSONObject(jsonString);

                    // Send to UI
                    liveDataPacket.postValue(json);

                    // Update Charts
                    parseChartData(json);

                    // --- LOGGING LOGIC ---
                    long serverPacketId = json.optLong("packet_id", 0);
                    double serverTimeSec = json.optDouble("ts", 0);
                    double androidTimeSec = System.currentTimeMillis() / 1000.0;

                    // Latency Calculation
                    if (serverTimeSec > 0) {
                        double currentDiff = androidTimeSec - serverTimeSec;
                        if (currentDiff < minClockOffset) minClockOffset = currentDiff;
                    }
                    if (json.has("pong") && json.getBoolean("pong") && pingStartTime > 0) {
                        lastCalculatedRTT = (double) (System.currentTimeMillis() - pingStartTime);
                        pingStartTime = 0;
                    }

                    // Prepare CSV Data
                    double elapsedSeconds = (androidTimeSec * 1000.0 - sessionStartTime) / 1000.0;
                    double kbPerSec = (elapsedSeconds > 0) ? (totalBytesReceived / 1024.0) / elapsedSeconds : 0;
                    double pps = (elapsedSeconds > 0) ? localPacketCount / elapsedSeconds : 0;
                    double relativeLatencyMs = (serverTimeSec > 0) ? (androidTimeSec - serverTimeSec - minClockOffset) * 1000.0 : 0;

                    dateObject.setTime(System.currentTimeMillis());

                    // Build String (Efficiently)
                    synchronized (csvBuilder) {
                        csvBuilder.setLength(0);
                        csvBuilder.append(serverPacketId).append(',')
                                .append(csvTimeFormat.format(dateObject)).append(',')
                                .append(payloadSize + 2).append(',')
                                .append(String.format(Locale.US, "%.2f", kbPerSec)).append(',')
                                .append(String.format(Locale.US, "%.2f", pps)).append(',')
                                .append(String.format(Locale.US, "%.4f", relativeLatencyMs)).append(',')
                                .append(String.format(Locale.US, "%.2f", lastCalculatedRTT));

                        logger.save(csvBuilder.toString());
                    }

                } catch (Exception ignored) {}
            }
        } catch (IOException e) {
            statusMessage.postValue("Connection Lost");
        } finally {
            disconnect();
        }
    }

    private int readExactly(InputStream in, byte[] buffer, int numBytes) throws IOException {
        int bytesRead = 0;
        while (bytesRead < numBytes) {
            int result = in.read(buffer, bytesRead, numBytes - bytesRead);
            if (result == -1) return -1;
            bytesRead += result;
        }
        return bytesRead;
    }

    private void parseChartData(JSONObject json) {
        // Safe parsing for your specific nested JSON structure

        // Voltage & Current (system -> battery)
        JSONObject sys = json.optJSONObject("system");
        if (sys != null) {
            JSONObject batt = sys.optJSONObject("battery");
            if (batt != null) {
                voltage.postValue((float) batt.optDouble("voltage", 0));
                current.postValue((float) batt.optDouble("current", 0));
            }
        }

        // Speed (motors -> Left -> rpm)
        JSONObject motors = json.optJSONObject("motors");
        if (motors != null) {
            JSONObject left = motors.optJSONObject("Left");
            if (left != null) {
                speed.postValue((float) left.optDouble("rpm", 0));
            }
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        disconnect();
    }
}
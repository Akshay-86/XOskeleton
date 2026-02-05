package com.example.XOskeleton;

import android.annotation.SuppressLint;
import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class ExoViewModel extends AndroidViewModel {

    // --- 1. UI Live Data (General) ---
    public final MutableLiveData<String> statusMessage = new MutableLiveData<>();
    public final MutableLiveData<Boolean> isConnected = new MutableLiveData<>(false);
    public final MutableLiveData<JSONObject> liveDataPacket = new MutableLiveData<>();

    // --- 2. CHART DATA (Restored for StatsFragment) ---
    public final MutableLiveData<Float> voltage = new MutableLiveData<>();
    public final MutableLiveData<Float> current = new MutableLiveData<>();
    public final MutableLiveData<Float> speed = new MutableLiveData<>();

    // --- Internals ---
    private BluetoothSocket socket;
    private volatile boolean isRunning = false;
    private final DataLogger logger;

    // Dynamic Logging Variables
    private List<String> csvHeaders = null;
    private final StringBuilder csvBuilder = new StringBuilder();

    public ExoViewModel(@NonNull Application application) {
        super(application);
        logger = new DataLogger(application);
    }

    @SuppressLint("MissingPermission")
    public void connect(String macAddress) {
        if (isRunning || macAddress == null) return;
        statusMessage.setValue("Connecting...");

        new Thread(() -> {
            try {
                BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(macAddress);
                Method m = device.getClass().getMethod("createRfcommSocket", int.class);
                socket = (BluetoothSocket) m.invoke(device, 1);

                if (socket != null) {
                    socket.connect();
                    isRunning = true;
                    isConnected.postValue(true);
                    statusMessage.postValue("Connected!");

                    // Reset logging for new session
                    csvHeaders = null;
                    logger.createNewFile();

                    startReadingLoop();
                }
            } catch (Exception e) {
                disconnect();
                statusMessage.postValue("Connection Failed");
            }
        }).start();
    }

    public void disconnect() {
        isRunning = false;
        try { if (socket != null) socket.close(); } catch (IOException e) { e.printStackTrace(); }
        isConnected.postValue(false);
        statusMessage.postValue("Disconnected");
    }

    private void startReadingLoop() {
        byte[] lengthHeader = new byte[2];

        try {
            InputStream inputStream = socket.getInputStream();
            while (isRunning) {
                // Read Header
                if (readExactly(inputStream, lengthHeader, 2) == -1) break;
                int payloadSize = ((lengthHeader[0] & 0xFF) << 8) | (lengthHeader[1] & 0xFF);

                // Read Payload
                byte[] payload = new byte[payloadSize];
                if (readExactly(inputStream, payload, payloadSize) == -1) break;

                String jsonString = new String(payload, StandardCharsets.UTF_8);

                try {
                    JSONObject json = new JSONObject(jsonString);

                    // A. Update General UI
                    liveDataPacket.postValue(json);

                    // B. Update StatsFragment Charts (Restored Logic)
                    parseChartData(json);

                    // C. Log to CSV (Dynamic Logic)
                    logDynamicJson(json);

                } catch (Exception ignored) {}
            }
        } catch (IOException e) {
            statusMessage.postValue("Connection Lost");
        } finally {
            disconnect();
        }
    }

    // --- Helper: Parsing for StatsFragment ---
    private void parseChartData(JSONObject json) {
        // 1. Voltage & Current (system -> battery)
        JSONObject sys = json.optJSONObject("system");
        if (sys != null) {
            JSONObject batt = sys.optJSONObject("battery");
            if (batt != null) {
                voltage.postValue((float) batt.optDouble("voltage", 0));
                current.postValue((float) batt.optDouble("current", 0));
            }
        }

        // 2. Speed (Using Left Motor RPM as default)
        JSONObject motors = json.optJSONObject("motors");
        if (motors != null) {
            // Try left, fallback to right, fallback to 0
            JSONObject left = motors.optJSONObject("left"); // Note: keys might be lowercase depending on python
            if (left == null) left = motors.optJSONObject("Left");

            if (left != null) {
                // Note: Python sends "var1" as RPM in your simplified script
                // Adjust this key ("rpm" or "var1") based on your Python script
                // Assuming "var1" is RPM based on your last Python request:
                speed.postValue((float) left.optDouble("var1", 0));
            }
        }
    }

    // --- Helper: Dynamic Logging ---
    private void logDynamicJson(JSONObject json) throws JSONException {
        Map<String, String> flatMap = new HashMap<>();
        flatten(json, "", flatMap);

        if (csvHeaders == null) {
            csvHeaders = new ArrayList<>(flatMap.keySet());
            Collections.sort(csvHeaders);
            logger.save(String.join(",", csvHeaders));
        }

        synchronized (csvBuilder) {
            csvBuilder.setLength(0);
            for (int i = 0; i < csvHeaders.size(); i++) {
                String key = csvHeaders.get(i);
                String value = flatMap.get(key);
                csvBuilder.append(value != null ? value : "0");
                if (i < csvHeaders.size() - 1) csvBuilder.append(",");
            }
            logger.save(csvBuilder.toString());
        }
    }

    private void flatten(JSONObject json, String prefix, Map<String, String> out) throws JSONException {
        Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = json.get(key);
            String newKey = prefix.isEmpty() ? key : prefix + "." + key;

            if (value instanceof JSONObject) {
                flatten((JSONObject) value, newKey, out);
            } else {
                out.put(newKey, String.valueOf(value));
            }
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

    @Override
    protected void onCleared() {
        super.onCleared();
        disconnect();
    }
}
package com.example.XOskeleton;

import android.annotation.SuppressLint;
import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import com.github.mikephil.charting.data.Entry;
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

    public final MutableLiveData<String> statusMessage = new MutableLiveData<>();
    public final MutableLiveData<Boolean> isConnected = new MutableLiveData<>(false);
    public final MutableLiveData<JSONObject> liveDataPacket = new MutableLiveData<>();

    // Stats Fragment Data
    public final MutableLiveData<Float> voltage = new MutableLiveData<>();
    public final MutableLiveData<Float> current = new MutableLiveData<>();
    public final MutableLiveData<Float> speed = new MutableLiveData<>();

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

    public void sendCommand(String command) {
        if (socket == null || !Boolean.TRUE.equals(isConnected.getValue())) {
            statusMessage.postValue("Not Connected");
            return;
        }

        new Thread(() -> {
            try {
                // Add newline because Python's readline/decode often expects it
                byte[] bytes = (command).getBytes(StandardCharsets.UTF_8);

                // Write directly to the output stream
                socket.getOutputStream().write(bytes);
                socket.getOutputStream().flush();

            } catch (IOException e) {
                statusMessage.postValue("Send Failed");
                e.printStackTrace();
            }
        }).start();
    }
    public void disconnect() {
        isRunning = false;
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
        isConnected.postValue(false);
        statusMessage.postValue("Disconnected");
    }

    private void startReadingLoop() {
        byte[] lengthHeader = new byte[2];
        try {
            InputStream inputStream = socket.getInputStream();
            while (isRunning) {
                if (readExactly(inputStream, lengthHeader, 2) == -1) break;
                int payloadSize = ((lengthHeader[0] & 0xFF) << 8) | (lengthHeader[1] & 0xFF);
                byte[] payload = new byte[payloadSize];
                if (readExactly(inputStream, payload, payloadSize) == -1) break;

                String jsonString = new String(payload, StandardCharsets.UTF_8);
                try {
                    JSONObject json = new JSONObject(jsonString);
                    liveDataPacket.postValue(json);

                    // 1. Flatten the JSON (Dynamic Discovery)
                    Map<String, String> flatMap = new HashMap<>();
                    flatten(json, "", flatMap);

                    // 2. Parse Stats using the Flattened Keys (Dynamic)
                    parseChartDataDynamic(flatMap);

                    // 3. Log to CSV
                    logDynamicJson(flatMap);

                } catch (Exception ignored) {}
            }
        } catch (IOException e) {
            statusMessage.postValue("Connection Lost");
        } finally {
            disconnect();
        }
    }

    // --- DYNAMIC STATS PARSING ---
    private void parseChartDataDynamic(Map<String, String> flatMap) {
        List<Float> voltageValues = new ArrayList<>();
        List<Float> currentValues = new ArrayList<>();
        List<Float> speedValues = new ArrayList<>();

        for (Map.Entry<String, String> entry : flatMap.entrySet()) {
            String key = entry.getKey().toLowerCase();
            if (!key.contains(".")) continue;

            try {
                float val = Float.parseFloat(entry.getValue());
                if (key.endsWith(".voltage") || key.endsWith(".volt")) {
                    voltageValues.add(val);
                } else if (key.endsWith(".current") || key.endsWith(".curr") || key.endsWith(".amps")) {
                    currentValues.add(val);
                } else if (key.endsWith(".velocity") || key.endsWith(".speed") || key.endsWith(".rpm")) {
                    speedValues.add(val);
                }
            } catch (NumberFormatException ignored) {}
        }

        if (!voltageValues.isEmpty()) voltage.postValue(calculateAverage(voltageValues));
        if (!currentValues.isEmpty()) current.postValue(calculateAverage(currentValues));
        if (!speedValues.isEmpty()) speed.postValue(calculateAverage(speedValues));
    }

    private float calculateAverage(List<Float> values) {
        float sum = 0;
        for (float v : values) sum += v;
        return sum / values.size();
    }

    // --- LOGGING ---
    private void logDynamicJson(Map<String, String> flatMap) {
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
            if (value instanceof JSONObject) flatten((JSONObject) value, newKey, out);
            else out.put(newKey, String.valueOf(value));
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

    // --- FILE OPERATIONS (OFFLINE GRAPH FIX) ---
    public List<String> getLogFiles() { return logger.getAllFiles(); }

    public List<String> getCsvHeaders(String fileName) {
        List<String[]> lines = logger.readFile(fileName);
        if (!lines.isEmpty()) {
            List<String> headers = new ArrayList<>();
            Collections.addAll(headers, lines.get(0));
            return headers;
        }
        return new ArrayList<>();
    }

    public List<Entry> getColumnData(String fileName, String columnName) {
        List<Entry> entries = new ArrayList<>();
        List<String[]> lines = logger.readFile(fileName);
        if (lines.size() < 2) return entries;

        String[] headers = lines.get(0);
        int colIndex = -1, timeIndex = -1;
        for (int i = 0; i < headers.length; i++) {
            // Trim to avoid issues with CSV spaces
            if (headers[i].trim().equals(columnName)) colIndex = i;
            if (headers[i].toLowerCase().contains("timestamp") || headers[i].equals("ts")) timeIndex = i;
        }
        if (colIndex == -1) return entries;

        // Use DOUBLE for precision
        double startTime = 0;

        for (int i = 1; i < lines.size(); i++) {
            String[] row = lines.get(i);
            if (row.length <= colIndex) continue;
            try {
                float y = Float.parseFloat(row[colIndex]);
                float x = i; // Default to row index

                if (timeIndex != -1 && row.length > timeIndex) {
                    // FIX: Parse as Double to keep the precision!
                    double ts = Double.parseDouble(row[timeIndex]);

                    // Convert Python Seconds (1.7e9) to MS logic if needed
                    // But usually, we just need the difference.
                    if (ts < 10000000000.0) ts *= 1000.0;

                    if (startTime == 0) startTime = ts;

                    // Calculate difference using Doubles, THEN cast to float for chart
                    x = (float) ((ts - startTime) / 1000.0);
                }
                entries.add(new Entry(x, y));
            } catch (Exception ignored) {}
        }
        return entries;
    }

    @Override
    protected void onCleared() { super.onCleared(); disconnect(); }

    public long getFileStartTime(String fileName) {
        List<String[]> lines = logger.readFile(fileName);
        if (lines.size() < 2) return 0;

        String[] headers = lines.get(0);
        int timeIndex = -1;
        for (int i = 0; i < headers.length; i++) {
            if (headers[i].toLowerCase().contains("timestamp") || headers[i].equals("ts")) {
                timeIndex = i;
                break;
            }
        }

        if (timeIndex != -1) {
            try {
                String val = lines.get(1)[timeIndex]; // First data row
                double ts = Double.parseDouble(val);
                // Convert Python seconds to Java Milliseconds if needed
                if (ts < 10000000000.0) ts *= 1000.0;
                return (long) ts;
            } catch (Exception e) { return 0; }
        }
        return 0;
    }
}
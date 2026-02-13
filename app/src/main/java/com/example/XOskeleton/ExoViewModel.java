package com.example.XOskeleton;

import android.annotation.SuppressLint;
import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import com.github.mikephil.charting.data.BarEntry; // Required for Stats Chart
import com.github.mikephil.charting.data.Entry;    // Required for Dev Chart

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

public class ExoViewModel extends AndroidViewModel {

    // --- Live Connection Data ---
    public final MutableLiveData<String> statusMessage = new MutableLiveData<>();
    public final MutableLiveData<Boolean> isConnected = new MutableLiveData<>(false);
    public final MutableLiveData<JSONObject> liveDataPacket = new MutableLiveData<>();

    // --- NEW: STATS (Uptime & History) ---
    public final MutableLiveData<String> liveUptime = new MutableLiveData<>("00:00:00");
    public final MutableLiveData<List<BarEntry>> historyEntries = new MutableLiveData<>();
    public final MutableLiveData<List<String>> historyLabels = new MutableLiveData<>();

    private BluetoothSocket socket;
    private volatile boolean isRunning = false;
    private final DataLogger logger;

    // Timer for Live Uptime
    private Timer uptimeTimer;
    private long connectionStartTime = 0;

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

                    // 1. START UPTIME TIMER
                    startUptimeTimer();

                    // 2. Start Logging
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
        stopUptimeTimer(); // Stop the timer
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
        isConnected.postValue(false);
        statusMessage.postValue("Disconnected");
    }

    // ==========================================
    //           1. LIVE UPTIME LOGIC
    // ==========================================
    private void startUptimeTimer() {
        connectionStartTime = System.currentTimeMillis();
        uptimeTimer = new Timer();
        uptimeTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                long millis = System.currentTimeMillis() - connectionStartTime;
                long seconds = millis / 1000;
                long h = seconds / 3600;
                long m = (seconds % 3600) / 60;
                long s = seconds % 60;
                liveUptime.postValue(String.format(Locale.US, "%02d:%02d:%02d", h, m, s));
            }
        }, 0, 1000);
    }

    private void stopUptimeTimer() {
        if (uptimeTimer != null) {
            uptimeTimer.cancel();
            uptimeTimer = null;
        }
    }

    // ==========================================
    //           2. HISTORY LOGIC (FIXED)
    // ==========================================
    // Mode: 0 = Daily (Last 7 Days), 1 = Hourly (Strictly Today)
    public void calculateUsageHistory(int mode) {
        new Thread(() -> {
            File dir = getApplication().getExternalFilesDir(null);
            if (dir == null || dir.listFiles() == null) return;

            Map<String, Float> groupedData = new HashMap<>();
            SimpleDateFormat fileFormat = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);

            SimpleDateFormat dayLabel = new SimpleDateFormat("MMM dd", Locale.US); // "Feb 10"
            SimpleDateFormat hourLabel = new SimpleDateFormat("HH:00", Locale.US); // "14:00"

            // --- NEW: USE CALENDAR FOR PRECISE CUTOFFS ---
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);

            long startOfToday = cal.getTimeInMillis();
            long cutoff;

            if (mode == 1) {
                // HOURLY MODE: Strictly ignore anything before 00:00 today
                cutoff = startOfToday;
            } else {
                // DAILY MODE: Today + Previous 6 Days
                cal.add(Calendar.DAY_OF_YEAR, -6);
                cutoff = cal.getTimeInMillis();
            }

            for (File f : Objects.requireNonNull(dir.listFiles())) {
                if (!f.getName().startsWith("Log_")) continue;

                try {
                    String datePart = f.getName().substring(4, 19);
                    Date startDate = fileFormat.parse(datePart);
                    if (startDate == null) continue;

                    long start = startDate.getTime();

                    // FILTER: If the file started before our cutoff, SKIP IT.
                    if (start < cutoff) continue;

                    long end = f.lastModified();
                    float durationMinutes = (end - start) / 60000f;
                    if (durationMinutes < 0.1) durationMinutes = 0.1f;

                    String key;
                    if (mode == 0) key = dayLabel.format(startDate);
                    else key = hourLabel.format(startDate);

                    float currentTotal = groupedData.getOrDefault(key, 0f);
                    groupedData.put(key, currentTotal + durationMinutes);

                } catch (Exception ignored) {}
            }

            // Sort and Post
            List<String> sortedKeys = new ArrayList<>(groupedData.keySet());
            Collections.sort(sortedKeys);

            List<BarEntry> entries = new ArrayList<>();
            List<String> labels = new ArrayList<>();

            for (int i = 0; i < sortedKeys.size(); i++) {
                String key = sortedKeys.get(i);
                entries.add(new BarEntry(i, groupedData.get(key)));
                labels.add(key);
            }

            historyEntries.postValue(entries);
            historyLabels.postValue(labels);

        }).start();
    }

    // ==========================================
    //           3. COMMANDS & READING
    // ==========================================
    public void sendCommand(String command) {
        if (socket == null || !Boolean.TRUE.equals(isConnected.getValue())) {
            statusMessage.postValue("Not Connected");
            return;
        }
        new Thread(() -> {
            try {
                byte[] bytes = (command).getBytes(StandardCharsets.UTF_8);
                socket.getOutputStream().write(bytes);
                socket.getOutputStream().flush();
            } catch (IOException e) {
                statusMessage.postValue("Send Failed");
            }
        }).start();
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

                    Map<String, String> flatMap = new HashMap<>();
                    flatten(json, "", flatMap);

                    // We don't parse voltage/current here anymore (Stats are Uptime only)
                    // Just log it
                    logDynamicJson(flatMap);

                } catch (Exception ignored) {}
            }
        } catch (IOException e) {
            statusMessage.postValue("Connection Lost");
        } finally {
            disconnect();
        }
    }

    // ==========================================
    //           4. LOGGING & HELPERS
    // ==========================================
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

    // ==========================================
    //           5. FILE HELPERS (DevFragment)
    // ==========================================
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
            if (headers[i].trim().equals(columnName)) colIndex = i;
            if (headers[i].toLowerCase().contains("timestamp") || headers[i].equals("ts")) timeIndex = i;
        }
        if (colIndex == -1) return entries;

        double startTime = 0;
        for (int i = 1; i < lines.size(); i++) {
            String[] row = lines.get(i);
            if (row.length <= colIndex) continue;
            try {
                float y = Float.parseFloat(row[colIndex]);
                float x = i;
                if (timeIndex != -1 && row.length > timeIndex) {
                    double ts = Double.parseDouble(row[timeIndex]);
                    if (ts < 10000000000.0) ts *= 1000.0;
                    if (startTime == 0) startTime = ts;
                    x = (float) ((ts - startTime) / 1000.0);
                }
                entries.add(new Entry(x, y));
            } catch (Exception ignored) {}
        }
        return entries;
    }

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
                String val = lines.get(1)[timeIndex];
                double ts = Double.parseDouble(val);
                if (ts < 10000000000.0) ts *= 1000.0;
                return (long) ts;
            } catch (Exception e) { return 0; }
        }
        return 0;
    }

    @Override
    protected void onCleared() { super.onCleared(); disconnect(); }
}
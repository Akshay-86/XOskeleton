package com.example.XOskeleton;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ExoskeletonFragment extends Fragment {

    private static final String TAG = "ExoskeletonFragment";

    // --- UI Elements ---
    private LinearLayout container;
    private TextView statusText;
    private Button btnChangeDevice;

    // --- Bluetooth Logic ---
    private static BluetoothSocket socket;
    private boolean isRunning = false;          // True if data loop is running
    private boolean isConnecting = false;       // True if a connection attempt is currently happening
    private boolean isAutoRetryEnabled = false; // True if we want to keep trying
    private String savedMacAddress = null;

    // --- Auto-Retry Handler ---
    private final Handler connectionHandler = new Handler(Looper.getMainLooper());
    private final Runnable retryRunnable = this::attemptConnection;

    // --- Metrics & Logging ---
    private DataLogger logger;
    private long lastUiUpdate = 0;

    // --- Latency Optimization ---
    private double minClockOffset = Double.MAX_VALUE;
    private long pingStartTime = 0;
    private double lastCalculatedRTT = 0;


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_exoskeleton, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        statusText = view.findViewById(R.id.statusText);
        container = view.findViewById(R.id.container);
        btnChangeDevice = view.findViewById(R.id.btnChangeDevice);

        btnChangeDevice.setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), ScanActivity.class));
        });
    }

    // =========================================================================
    //                    LIFECYCLE MANAGEMENT (The Brain)
    // =========================================================================

    @Override
    public void onResume() {
        super.onResume();
        updateButtonText(); // Update the "Change Device (Name)" text
        if (!isHidden()) {
            startAutoConnect();
        }
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) {
            updateButtonText();
            startAutoConnect(); // Visible -> Start Trying
        } else {
            stopAutoConnect();  // Hidden -> Stop Trying
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        stopAutoConnect(); // App Backgrounded -> Stop Trying
    }

    // =========================================================================
    //                    CONNECTION LOGIC (Auto-Retry)
    // =========================================================================

    private void updateButtonText() {
        String savedName = BluetoothPrefs.getLastName(requireContext());
        btnChangeDevice.setText("Change Device (" + savedName + ")");
    }

    private void startAutoConnect() {
        // Safety: If already connected or busy, ignore
        if (isRunning || isConnecting) return;

        savedMacAddress = BluetoothPrefs.getLastAddress(requireContext());
        if (savedMacAddress == null) {
            updateStatus("No device saved. Tap to add.");
            btnChangeDevice.setText("Add Device");
            return;
        }

        isAutoRetryEnabled = true;
        attemptConnection(); // Start immediately
    }

    private void stopAutoConnect() {
        isAutoRetryEnabled = false;
        connectionHandler.removeCallbacks(retryRunnable);
    }

    public void retryConnection() {
        // Called by MainActivity "Refresh" button
        if (isRunning) {
            updateStatus("Already connected!");
            return;
        }
        stopAutoConnect(); // Reset state
        startAutoConnect(); // Start fresh
    }

    private void attemptConnection() {
        // 1. Basic Checks
        if (!isAutoRetryEnabled || isRunning || isConnecting || savedMacAddress == null) return;

        isConnecting = true;
        updateStatus("Connecting...");

        // 2. Start Background Thread
        new Thread(() -> {
            if (getActivity() == null) { isConnecting = false; return; }

            // Permission Checks
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                    && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                updateStatus("Permission Denied");
                isConnecting = false;
                return;
            }
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
            }

            // 3. Attempt Connection (Blocking Call)
            BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(savedMacAddress);
            boolean success = connectToPort(device, 1);

            // 4. Handle Result
            if (success) {
                // SUCCESS!
                // Step A: Update UI Flag (Quickly)
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        isConnecting = false;
                        updateStatus("Connected! Starting Stream...");
                    });
                }

                // Step B: RUN THE DATA LOOP HERE (ON BACKGROUND THREAD)
                // Do NOT wrap this in runOnUiThread, or the app will freeze!
                startReadingData();

            } else {
                // FAILURE!
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        isConnecting = false;
                        // Schedule Retry
                        if (isAutoRetryEnabled) {
                            updateStatus("Connecting... (Retrying in 3s)");
                            connectionHandler.postDelayed(retryRunnable, 3000);
                        } else {
                            updateStatus("Paused.");
                        }
                    });
                }
            }
        }).start();
    }

    private boolean connectToPort(BluetoothDevice device, int port) {
        try {
            Method m = device.getClass().getMethod("createRfcommSocket", int.class);
            socket = (BluetoothSocket) m.invoke(device, port);
            if (socket != null) {
                socket.connect();
                return true;
            }
        } catch (Exception e) {
            try { if (socket != null) socket.close(); } catch (IOException ignored) {}
        }
        return false;
    }

    private void closeConnection() {
        isRunning = false;
        isConnecting = false;
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
    }

    private void startReadingData() {
        updateStatus("Connected! High Performance Mode");
        isRunning = true;

        // Metric Variables
        long sessionStartTime = System.currentTimeMillis();
        long totalBytesReceived = 0;
        long localPacketCount = 0;
        int loopCounter = 0;

        // Latency Variables
        minClockOffset = Double.MAX_VALUE;
        pingStartTime = 0;
        lastCalculatedRTT = 0;

        // Buffers
        logger = new DataLogger(requireContext());
        byte[] lengthHeader = new byte[2];
        SimpleDateFormat csvTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
        StringBuilder csvBuilder = new StringBuilder();
        Date dateObject = new Date();

        try {
            InputStream inputStream = socket.getInputStream();
            OutputStream outputStream = socket.getOutputStream();

            while (isRunning) {
                // --- A. SEND PING (Every ~20 packets) ---
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

                // --- B. READ HEADER ---
                // If this blocks forever, your Server is NOT sending the 2-byte header correctly.
                readExactly(inputStream, lengthHeader, 2);
                int payloadSize = ((lengthHeader[0] & 0xFF) << 8) | (lengthHeader[1] & 0xFF);

                // --- C. READ PAYLOAD ---
                byte[] payload = new byte[payloadSize];
                readExactly(inputStream, payload, payloadSize);

                // --- D. PARSING ---
                String jsonString = new String(payload, StandardCharsets.UTF_8);

                // Metrics
                localPacketCount++;
                totalBytesReceived += (payloadSize + 2);
                double androidTimeSec = System.currentTimeMillis() / 1000.0;
                double elapsedSeconds = (androidTimeSec * 1000.0 - sessionStartTime) / 1000.0;
                double kbPerSec = (elapsedSeconds > 0) ? (totalBytesReceived / 1024.0) / elapsedSeconds : 0;
                double pps = (elapsedSeconds > 0) ? localPacketCount / elapsedSeconds : 0;

                JSONObject json = null;
                String errorMsg = null;

                try {
                    json = new JSONObject(jsonString);

                    // Extract Server Info
                    long serverPacketId = 0;
                    double serverTimeSec = 0;
                    if (json.has("packet_id")) serverPacketId = json.getLong("packet_id");
                    if (json.has("ts")) serverTimeSec = json.getDouble("ts");

                    // Latency Logic
                    if (serverTimeSec > 0) {
                        double currentDiff = androidTimeSec - serverTimeSec;
                        if (currentDiff < minClockOffset) minClockOffset = currentDiff;
                    }

                    // RTT Logic
                    if (json.has("pong") && json.getBoolean("pong") && pingStartTime > 0) {
                        lastCalculatedRTT = (double) (System.currentTimeMillis() - pingStartTime);
                        pingStartTime = 0;
                    }

                    // CSV Logging (Only log valid packets)
                    double relativeLatencyMs = (serverTimeSec > 0) ? (androidTimeSec - serverTimeSec - minClockOffset) * 1000.0 : 0;
                    dateObject.setTime(System.currentTimeMillis());
                    csvBuilder.setLength(0);
                    csvBuilder.append(serverPacketId).append(',')
                            .append(csvTimeFormat.format(dateObject)).append(',')
                            .append(payloadSize + 2).append(',')
                            .append(String.format(Locale.US, "%.2f", kbPerSec)).append(',')
                            .append(String.format(Locale.US, "%.2f", pps)).append(',')
                            .append(String.format(Locale.US, "%.4f", relativeLatencyMs)).append(',')
                            .append(String.format(Locale.US, "%.2f", lastCalculatedRTT)).append(',')
                            .append(String.format(Locale.US, "%.2f", lastCalculatedRTT / 2.0));
                    logger.save(csvBuilder.toString());

                } catch (JSONException e) {
                    // CAPTURE THE ERROR
                    errorMsg = "JSON Error: " + e.getMessage();
                    Log.e(TAG, "Parsing Failed: " + jsonString); // Check Logcat for this!
                }

                // --- E. UI UPDATE (ALWAYS RUNS) ---
                long currentTimeMs = System.currentTimeMillis();
                if (currentTimeMs - lastUiUpdate > 100) {
                    lastUiUpdate = currentTimeMs;
                    if (getActivity() != null) {
                        final JSONObject finalJson = json;
                        final double finalPps = pps;
                        final double finalKb = kbPerSec;
                        final String finalError = errorMsg;
                        final String finalRawData = jsonString; // Pass raw string for debugging

                        getActivity().runOnUiThread(() ->
                                updateUi(finalJson, finalPps, finalKb, finalError, finalRawData)
                        );
                    }
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Connection Lost", e);
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    updateStatus("Disconnected: " + e.getMessage());
                    closeConnection();
                    if (isAutoRetryEnabled) connectionHandler.postDelayed(retryRunnable, 3000);
                });
            }
        }
    }

    private void readExactly(InputStream in, byte[] buffer, int numBytes) throws IOException {
        int bytesRead = 0;
        while (bytesRead < numBytes) {
            int result = in.read(buffer, bytesRead, numBytes - bytesRead);
            if (result == -1) throw new IOException("End of stream");
            bytesRead += result;
        }
    }

    // UPDATE THE UI METHOD TO HANDLE ERRORS
    private void updateUi(JSONObject json, double pps, double kbps, String errorMsg, String rawData) {
        try {
            String timestamp = java.text.DateFormat.getTimeInstance().format(new Date());
            StringBuilder statusMsg = new StringBuilder();

            // 1. Build Status Message
            statusMsg.append(String.format(Locale.US, "Status: Active\nTime: %s\n⚡ %.0f PPS | %.1f KB/s", timestamp, pps, kbps));

            // 2. If there was an error, append it to the status text
            if (errorMsg != null) {
                statusMsg.append("\n⚠️ ").append(errorMsg);
                // Optional: Show the first 50 chars of raw data to see what's broken
                if (rawData.length() > 50) rawData = rawData.substring(0, 50) + "...";
                statusMsg.append("\nRaw: ").append(rawData);
            }

            statusText.setText(statusMsg.toString());

            // 3. Render JSON only if valid
            if (json != null && container != null) {
                JsonUiRenderer.render(requireContext(), json, container);
            }
        } catch (Exception e) {
            statusText.setText("UI Error: " + e.getMessage());
        }
    }

    private void updateStatus(String msg) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> statusText.setText(msg));
        }
    }
}
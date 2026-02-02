package com.example.XOskeleton;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
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

    // UI Elements
    private LinearLayout container;
    private TextView statusText;
    private Button btnChangeDevice;

    // Bluetooth Logic
    private static BluetoothSocket socket;
    private static boolean isConnecting = false;
    private static String currentConnectedMac = null;
    private boolean isRunning = false;

    // Logging & Metrics
    private DataLogger logger;
    private long lastUiUpdate = 0;

    // Latency & RTT Optimization Variables
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

    @Override
    public void onResume() {
        super.onResume();
        if (!isHidden()) checkAndConnect();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) checkAndConnect();
    }

    private void checkAndConnect() {
        String savedMac = BluetoothPrefs.getLastAddress(requireContext());
        String savedName = BluetoothPrefs.getLastName(requireContext());

        if (savedMac == null) {
            statusText.setText("No device saved.");
            btnChangeDevice.setText("Add Device");
            return;
        }

        btnChangeDevice.setText("Change Device (" + savedName + ")");
        if (isRunning) statusText.setText("Status: Active (Connected)");

        if (isConnecting) return;

        if (!isRunning || (currentConnectedMac != null && !currentConnectedMac.equals(savedMac))) {
            if (isRunning) closeConnection();
            BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(savedMac);
            startConnectionProcess(device);
        }
    }

    private void startConnectionProcess(BluetoothDevice device) {
        isConnecting = true;
        currentConnectedMac = device.getAddress();

        new Thread(() -> {
            if (getActivity() == null) { isConnecting = false; return; }

            // Permission Check (Android 12+)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    updateStatus("Permission Denied");
                    isConnecting = false;
                    return;
                }
            }
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
            }

            // Connection Loop (Try Ports 1-3)
            boolean connected = false;
            for (int port = 1; port <= 3; port++) {
                if (getActivity() == null) break;
                updateStatus("Connecting to Port " + port + "...");
                if (connectToPort(device, port)) {
                    connected = true;
                    break;
                }
            }

            if (connected) {
                isConnecting = false;
                startReadingData(); // Start the optimized loop
            } else {
                updateStatus("Connection Failed. Is server running?");
                isConnecting = false;
                currentConnectedMac = null;
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

    // =========================================================================
    //                    THE OPTIMIZED DATA LOOP
    // =========================================================================
    private void startReadingData() {
        updateStatus("Connected! High Performance Mode");
        isRunning = true;

        // 1. Reset Metrics
        long sessionStartTime = System.currentTimeMillis();
        long totalBytesReceived = 0;
        long localPacketCount = 0;
        int loopCounter = 0;

        // 2. Reset Latency
        minClockOffset = Double.MAX_VALUE;
        pingStartTime = 0;
        lastCalculatedRTT = 0;

        // 3. OPTIMIZATION: Create Logger & Buffers once (Outside Loop)
        logger = new DataLogger(requireContext());
        byte[] lengthHeader = new byte[2]; // Reuse this buffer
        StringBuilder csvBuilder = new StringBuilder(); // Reuse this for string building
        Date dateObject = new Date(); // Reuse this object
        SimpleDateFormat csvTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);

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
                // Optimized: read directly into the reusable buffer
                readExactly(inputStream, lengthHeader, 2);
                int payloadSize = ((lengthHeader[0] & 0xFF) << 8) | (lengthHeader[1] & 0xFF);

                // --- C. READ PAYLOAD ---
                // Note: We allocate here because payload size varies.
                // If max size is constant, we could optimize this too.
                byte[] payload = new byte[payloadSize];
                readExactly(inputStream, payload, payloadSize);

                // --- D. PROCESSING (Background Thread) ---
                String jsonString = new String(payload, StandardCharsets.UTF_8);
                double androidTimeSec = System.currentTimeMillis() / 1000.0;

                double serverTimeSec = 0;
                long serverPacketId = 0;
                double relativeLatencyMs = 0;

                // OPTIMIZATION: Parse JSON *ONCE* here
                JSONObject json = null;
                try {
                    json = new JSONObject(jsonString);
                    if (json.has("packet_id")) serverPacketId = json.getLong("packet_id");
                    if (json.has("ts")) serverTimeSec = json.getDouble("ts");

                    // Check Pong
                    if (json.has("pong") && json.getBoolean("pong")) {
                        if (pingStartTime > 0) {
                            lastCalculatedRTT = (double) (System.currentTimeMillis() - pingStartTime);
                            pingStartTime = 0;
                        }
                    }
                } catch (JSONException ignored) {
                    // Packet likely corrupt or partial, skip metrics update but keep running
                }

                // Calculate Latency
                if (serverTimeSec > 0) {
                    double currentDiff = androidTimeSec - serverTimeSec;
                    if (currentDiff < minClockOffset) minClockOffset = currentDiff;
                    relativeLatencyMs = (currentDiff - minClockOffset) * 1000.0;
                }

                // Metrics
                localPacketCount++;
                int totalPacketSize = payloadSize + 2;
                totalBytesReceived += totalPacketSize;
                double elapsedSeconds = (androidTimeSec * 1000.0 - sessionStartTime) / 1000.0;

                double kbPerSec = 0;
                double pps = 0;
                if (elapsedSeconds > 0) {
                    kbPerSec = (totalBytesReceived / 1024.0) / elapsedSeconds;
                    pps = localPacketCount / elapsedSeconds;
                }

                // --- E. FAST LOGGING (StringBuilder) ---
                // Re-using StringBuilder is much faster than String.format
                dateObject.setTime(System.currentTimeMillis());

                csvBuilder.setLength(0); // Clear buffer
                csvBuilder.append(serverPacketId).append(',')
                        .append(csvTimeFormat.format(dateObject)).append(',')
                        .append(totalPacketSize).append(',')
                        .append(String.format(Locale.US, "%.2f", kbPerSec)).append(',')
                        .append(String.format(Locale.US, "%.2f", pps)).append(',')
                        .append(String.format(Locale.US, "%.4f", relativeLatencyMs)).append(',')
                        .append(String.format(Locale.US, "%.2f", lastCalculatedRTT)).append(',')
                        .append(String.format(Locale.US, "%.2f", lastCalculatedRTT / 2.0));

                logger.save(csvBuilder.toString());

                // --- F. UI UPDATE (De-Jittered) ---
                long currentTimeMs = System.currentTimeMillis();
                if (currentTimeMs - lastUiUpdate > 100) {
                    lastUiUpdate = currentTimeMs;
                    if (getActivity() != null && json != null) {
                        // OPTIMIZATION: Pass the ALREADY PARSED json to the UI
                        final JSONObject finalJson = json;
                        getActivity().runOnUiThread(() -> updateUi(finalJson));
                    }
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Connection Lost", e);
            updateStatus("Disconnected: " + e.getMessage());
            closeConnection();
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

    private void updateUi(JSONObject json) {
        try {
            String timestamp = java.text.DateFormat.getTimeInstance().format(new Date());
            statusText.setText("Status: Active\nLast Update: " + timestamp);

            // Render without re-parsing!
            JsonUiRenderer.render(requireContext(), json, container);
        } catch (Exception e) {
            statusText.setText("UI Error");
        }
    }

    private void updateStatus(String msg) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> statusText.setText(msg));
        }
    }

    // Add this method anywhere in ExoskeletonFragment class
    public void retryConnection() {
        if (isRunning) {
            updateStatus("Already connected!");
            return;
        }

        // Reset flags to ensure we can try again
        isConnecting = false;

        updateStatus("Retrying connection...");
        checkAndConnect(); // Call the existing connection logic
    }

    private void closeConnection() {
        isRunning = false;
        isConnecting = false;
        currentConnectedMac = null;
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
    }
}
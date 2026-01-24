package com.example.basicbluetoothapp;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

public class ClientActivity extends AppCompatActivity {

    private static final String TAG = "ClientActivity";
    private LinearLayout container;
    private TextView statusText;
    private BluetoothDevice device;
    private BluetoothSocket socket;
    private boolean isRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 32, 32, 32);

        statusText = new TextView(this);
        statusText.setText("Initializing...");
        statusText.setTextSize(18);
        root.addView(statusText);

        container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        root.addView(container);

        setContentView(root);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            device = getIntent().getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
        } else {
            device = getIntent().getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        }

        if (device != null) {
            startConnectionProcess();
        } else {
            statusText.setText("Error: Device not found in Intent.");
        }
    }

    private void startConnectionProcess() {
        new Thread(() -> {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                runOnUiThread(() -> statusText.setText("Permission Denied"));
                return;
            }

            boolean connected = false;
            for (int port = 1; port <= 3; port++) {
                int currentPort = port;
                runOnUiThread(() -> {
                    statusText.setText("Trying Port " + currentPort + "...");
                    Toast.makeText(this, "Trying Port " + currentPort, Toast.LENGTH_SHORT).show();
                });

                if (connectToPort(port)) {
                    connected = true;
                    break;
                }
            }

            if (connected) {
                startReadingData();
            } else {
                runOnUiThread(() -> statusText.setText("Failed to connect on ports 1, 2, or 3."));
            }
        }).start();
    }

    private boolean connectToPort(int port) {
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

    // =================================================================
    // NEW: "FRAMING" PROTOCOL READER
    // Matches the logic in framing.py: [2 Bytes Length][Payload]
    // =================================================================
    private void startReadingData() {
        runOnUiThread(() -> statusText.setText("Connected! Waiting for frames..."));
        isRunning = true;

        try {
            InputStream inputStream = socket.getInputStream();

            while (isRunning) {
                // 1. Read the Length Header (2 Bytes)
                byte[] lengthHeader = new byte[2];
                // Helper method to ensure we get exactly 2 bytes
                readExactly(inputStream, lengthHeader, 2);

                // 2. Decode Length (Big Endian: High Byte + Low Byte)
                // Matches Python's: length.to_bytes(2, byteorder="big")
                int payloadSize = ((lengthHeader[0] & 0xFF) << 8) | (lengthHeader[1] & 0xFF);

                // 3. Read the Payload (Exactly 'payloadSize' bytes)
                byte[] payload = new byte[payloadSize];
                readExactly(inputStream, payload, payloadSize);

                // 4. Convert Payload to String and Process
                String jsonString = new String(payload, StandardCharsets.UTF_8);

                runOnUiThread(() -> processReceivedData(jsonString));
            }
        } catch (IOException e) {
            Log.e(TAG, "Connection Lost", e);
            runOnUiThread(() -> statusText.setText("Disconnected: " + e.getMessage()));
        } finally {
            closeSocket();
        }
    }

    /**
     * Helper to read EXACTLY numBytes.
     * Prevents partial reads (which crash standard 'read' calls).
     */
    private void readExactly(InputStream in, byte[] buffer, int numBytes) throws IOException {
        int bytesRead = 0;
        while (bytesRead < numBytes) {
            int result = in.read(buffer, bytesRead, numBytes - bytesRead);
            if (result == -1) {
                throw new IOException("End of stream reached unexpectedly");
            }
            bytesRead += result;
        }
    }

    private void processReceivedData(String rawData) {
        if (rawData == null || rawData.isEmpty()) return;
        try {
            statusText.setText("Live Data Received:");
            // Basic cleanup just in case
            String cleanJson = rawData.replace("'", "\"");
            JSONObject json = new JSONObject(cleanJson);
            JsonUiRenderer.render(this, json, container);
        } catch (JSONException e) {
            Log.e(TAG, "Parsing Error: " + rawData, e);
            statusText.setText("Parsing Failed. Raw: " + rawData);
        }
    }

    private void closeSocket() {
        isRunning = false;
        try {
            if (socket != null) socket.close();
        } catch (IOException e) {
            Log.e(TAG, "Error closing socket", e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        closeSocket();
    }
}
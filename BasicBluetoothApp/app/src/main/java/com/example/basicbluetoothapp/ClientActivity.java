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

        // UI Setup
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

        // Get Device from Intent
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
            // Try ports 1 to 3
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

    private void startReadingData() {
        runOnUiThread(() -> statusText.setText("Connected! Receiving Data..."));
        isRunning = true;

        try {
            InputStream inputStream = socket.getInputStream();
            StringBuilder buffer = new StringBuilder();
            int openBraces = 0;
            boolean insideObject = false;

            while (isRunning) {
                int byteRead = inputStream.read();
                if (byteRead == -1) break;

                char c = (char) byteRead;

                // 1. Detect start of JSON object
                if (c == '{') {
                    if (!insideObject) {
                        insideObject = true;
                        buffer.setLength(0); // Clear any garbage before the '{'
                    }
                    openBraces++;
                }

                // 2. Only append to buffer if we are inside a JSON object
                if (insideObject) {
                    buffer.append(c);
                }

                // 3. Detect end of JSON object
                if (c == '}' && insideObject) {
                    openBraces--;
                    if (openBraces == 0) {
                        final String completeJson = buffer.toString().trim();
                        runOnUiThread(() -> processReceivedData(completeJson));
                        buffer.setLength(0);
                        insideObject = false;
                    }
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Connection Lost", e);
            runOnUiThread(() -> statusText.setText("Connection Lost: " + e.getMessage()));
        } finally {
            closeSocket();
        }
    }

    private void processReceivedData(String rawData) {
        if (rawData == null || rawData.isEmpty()) return;

        try {
            statusText.setText("Live Data Received:");
            // Replace single quotes with double quotes for valid JSON
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

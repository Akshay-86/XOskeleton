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

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
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

        // Simple UI Setup
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

        // Retrieve the device from the Intent
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
                int currentPort = port; // Needed for lambda

                // FIX: Move Toast inside runOnUiThread
                runOnUiThread(() -> {
                    statusText.setText("Trying Port " + currentPort + "...");
                    Toast.makeText(this, "Trying " + currentPort + "...", Toast.LENGTH_SHORT).show();
                });

                if (connectToPort(port)) {
                    connected = true;
                    break;
                }
            }

            if (connected) {
                startReadingData();
            } else {
                runOnUiThread(() -> statusText.setText("Failed to connect on channels 1, 2, or 3."));
            }
        }).start();
    }

    // Helper to try a specific port
    private boolean connectToPort(int port) {
        try {
            // Use Reflection to force a connection to a specific channel/port
            // This bypasses the need for SDP UUID lookup
            Method m = device.getClass().getMethod("createRfcommSocket", int.class);
            socket = (BluetoothSocket) m.invoke(device, port);

            if (socket != null) {
                socket.connect();
                return true; // Success!
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to connect to port " + port, e);
            try {
                if (socket != null) socket.close();
            } catch (IOException ignored) {}
        }
        return false;
    }

    private void startReadingData() {
        runOnUiThread(() -> statusText.setText("Connected! Receiving Data..."));
        isRunning = true;

        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            while (isRunning) {
                String line = reader.readLine(); // Blocks until data comes
                if (line != null) {
                    runOnUiThread(() -> {
                        try {
                            statusText.setText("Live Data:");
                            JSONObject json = new JSONObject(line);
                            JsonUiRenderer.render(this, json, container);
                        } catch (Exception e) {
                            Log.e(TAG, "JSON Parsing Error", e);
                        }
                    });
                } else {
                    break;
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Connection Lost", e);
            runOnUiThread(() -> statusText.setText("Connection Lost: " + e.getMessage()));
        } finally {
            closeSocket();
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

    // Helper class for UI updates from background thread
    class UpdateStatusTask implements Runnable {
        String msg;
        UpdateStatusTask(String m) { msg = m; }
        public void run() { statusText.setText(msg); }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        closeSocket();
    }
}
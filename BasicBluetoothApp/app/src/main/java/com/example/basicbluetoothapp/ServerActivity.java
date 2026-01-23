package com.example.basicbluetoothapp;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ServerActivity extends AppCompatActivity {

    private static final String TAG = "ServerActivity";
    private static final int REQ_SERVER_PERMISSIONS = 102;

    private LinearLayout container;
    private TextView statusText;
    private BluetoothServer bluetoothServer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Programmatic layout to save you creating another XML file
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 32, 32, 32);

        statusText = new TextView(this);
        statusText.setText("Waiting for connection...");
        statusText.setTextSize(18);
        root.addView(statusText);

        container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        root.addView(container);

        setContentView(root);

        checkAndRequestServerPermissions();
    }

    private void checkAndRequestServerPermissions() {
        List<String> permissionsToRequest = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_ADVERTISE);
            }
        }

        if (!permissionsToRequest.isEmpty()) {
            Log.d(TAG, "Requesting server runtime permissions: " + permissionsToRequest);
            ActivityCompat.requestPermissions(
                    this,
                    permissionsToRequest.toArray(new String[0]),
                    REQ_SERVER_PERMISSIONS
            );
        } else {
            Log.d(TAG, "All server permissions granted. Starting server.");
            startBluetoothServer();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_SERVER_PERMISSIONS) {
            boolean allPermissionsGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }

            if (allPermissionsGranted) {
                Log.d(TAG, "All requested server permissions GRANTED. Starting server.");
                startBluetoothServer();
            } else {
                Log.e(TAG, "One or more required server permissions DENIED.");
                Toast.makeText(this, "Bluetooth server permissions denied.", Toast.LENGTH_LONG).show();
                finish(); // Close activity if essential permissions are denied
            }
        }
    }

    private void startBluetoothServer() {
        bluetoothServer = new BluetoothServer(jsonString -> {
            runOnUiThread(() -> {
                try {
                    statusText.setText("Data Received:");
                    JSONObject json = new JSONObject(jsonString);
                    JsonUiRenderer.render(this, json, container);
                } catch (Exception e) {
                    statusText.setText("Error parsing JSON: " + e.getMessage());
                    Log.e(TAG, "Error rendering JSON: ", e);
                }
            });
        });

        new Thread(bluetoothServer).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bluetoothServer != null) {
            Log.d(TAG, "Stopping Bluetooth server.");
            bluetoothServer.stopServer(); // Assuming a stopServer method will be added to BluetoothServer
        }
    }
}

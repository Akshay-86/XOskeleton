package com.example.basicbluetoothapp;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "BT_SERVER";
    private static final String APP_NAME = "RFCOMM_Server";
    private static final UUID MY_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private static final int REQ_BT = 1001;

    TextView[] valueViews = new TextView[6];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.e("BT_TEST", "onCreate() reached");
        Toast.makeText(this, "App started", Toast.LENGTH_SHORT).show();

        valueViews[0] = findViewById(R.id.value1);
        valueViews[1] = findViewById(R.id.value2);
        valueViews[2] = findViewById(R.id.value3);
        valueViews[3] = findViewById(R.id.value4);
        valueViews[4] = findViewById(R.id.value5);
        valueViews[5] = findViewById(R.id.value6);

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();

        if (adapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_LONG).show();
            return;
        }

        if (!adapter.isEnabled()) {
            Toast.makeText(this, "Bluetooth is OFF", Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(this, "Bluetooth is ON", Toast.LENGTH_SHORT).show();

        // Android 12+ runtime permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ActivityCompat.checkSelfPermission(
                        this, Manifest.permission.BLUETOOTH_CONNECT)
                        != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.BLUETOOTH_CONNECT},
                    REQ_BT
            );
        } else {
            startBluetoothThread();
        }
    }

    private void startBluetoothThread() {
        Log.e("BT_TEST", "Starting BT server thread");
        Toast.makeText(this, "Starting BT server", Toast.LENGTH_SHORT).show();

        new Thread(this::startServer).start();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQ_BT &&
                grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {

            startBluetoothThread();
        } else {
            Toast.makeText(this, "Bluetooth permission denied", Toast.LENGTH_LONG).show();
        }
    }

    private void startServer() {
        Log.e("BT_TEST", "startServer() entered");

        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();

            // REVERTED TO STANDARD: Let Android pick the channel automatically
            BluetoothServerSocket serverSocket =
                    adapter.listenUsingRfcommWithServiceRecord(APP_NAME, MY_UUID);

            Log.e("BT_TEST", "RFCOMM server started (Channel assigned by OS)");

            while (true) {
                Log.e("BT_TEST", "Waiting for client...");

                // This blocks until Python connects
                BluetoothSocket socket = serverSocket.accept();

                Log.e("BT_TEST", "CLIENT CONNECTED");
                runOnUiThread(() ->
                        Toast.makeText(this, "PC CONNECTED ✅", Toast.LENGTH_LONG).show()
                );

                handleClient(socket);
            }

        } catch (Exception e) {
            Log.e(TAG, "Server error", e);
            runOnUiThread(() ->
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show()
            );
        }
    }

    private void handleClient(BluetoothSocket socket) {
        try {
            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(socket.getInputStream()));

            String line;
            while ((line = reader.readLine()) != null) {
                Log.e("BT_TEST", "Received: " + line);

                String[] values = line.split(",");

                runOnUiThread(() -> {
                    for (int i = 0; i < values.length && i < valueViews.length; i++) {
                        valueViews[i].setText(values[i]);
                    }
                });
            }

        } catch (Exception e) {
            Log.e("BT_TEST", "Client disconnected");
        }

    }
}

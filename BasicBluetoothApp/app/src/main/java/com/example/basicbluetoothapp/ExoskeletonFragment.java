package com.example.basicbluetoothapp;

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
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

public class ExoskeletonFragment extends Fragment {

    private static final String TAG = "ExoskeletonFragment";
    private LinearLayout container;
    private TextView statusText;
    private Button btnChangeDevice;

    private static BluetoothSocket socket;

    // FLAGS to control state
    private static boolean isRunning = false;    // True only when data is flowing
    private static boolean isConnecting = false; // True while we are trying to connect

    private static String currentConnectedMac = null;

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
            Intent intent = new Intent(requireContext(), ScanActivity.class);
            startActivity(intent);
        });
        // We don't call checkAndConnect() here because onResume() will run immediately after this anyway.
    }

    @Override
    public void onResume() {
        super.onResume();
        checkAndConnect();
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

        // CRITICAL FIX: Don't start a new connection if one is already in progress!
        if (isConnecting) return;

        // If we are already running on the correct device, do nothing.
        if (isRunning && currentConnectedMac != null && currentConnectedMac.equals(savedMac)) {
            return;
        }

        // If we need to change devices, close the old one
        if (isRunning || (currentConnectedMac != null && !currentConnectedMac.equals(savedMac))) {
            closeConnection();
        }

        // Start fresh connection
        BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(savedMac);
        startConnectionProcess(device);
    }

    private void startConnectionProcess(BluetoothDevice device) {
        isConnecting = true; // Lock: Prevent double threads
        currentConnectedMac = device.getAddress();

        new Thread(() -> {
            // Safety Check
            if (getActivity() == null) {
                isConnecting = false;
                return;
            }

            // 1. Permissions Check
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                updateStatus("Permission Denied");
                isConnecting = false;
                return;
            }

            // 2. CRITICAL FIX: Stop Scanning & Wait
            // Scanning interferes with Connection. We force it to stop.
            BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
            try {
                updateStatus("Initializing...");
                Thread.sleep(600); // Give Bluetooth stack time to settle
            } catch (InterruptedException ignored) {}

            boolean connected = false;

            // 3. Try Ports
            for (int port = 1; port <= 3; port++) {
                if (getActivity() == null) break;
                updateStatus("Connecting to " + device.getName() + " (Port " + port + ")...");

                if (connectToPort(device, port)) {
                    connected = true;
                    break;
                }

                // Small pause between port attempts
                try { Thread.sleep(200); } catch (Exception e) {}
            }

            if (connected) {
                isConnecting = false; // Unlock
                startReadingData();
            } else {
                updateStatus("Connection Failed. Is the server running?");
                isConnecting = false; // Unlock
                currentConnectedMac = null; // Reset so we can try again
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

    private void startReadingData() {
        updateStatus("Connected! Waiting for data...");
        isRunning = true;

        try {
            InputStream inputStream = socket.getInputStream();
            while (isRunning) {
                if (getActivity() == null) break;

                byte[] lengthHeader = new byte[2];
                readExactly(inputStream, lengthHeader, 2);
                int payloadSize = ((lengthHeader[0] & 0xFF) << 8) | (lengthHeader[1] & 0xFF);

                byte[] payload = new byte[payloadSize];
                readExactly(inputStream, payload, payloadSize);

                String jsonString = new String(payload, StandardCharsets.UTF_8);

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> processReceivedData(jsonString));
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Connection Lost", e);
            updateStatus("Disconnected: " + e.getMessage());
            closeConnection();
        }
    }

    private void closeConnection() {
        isRunning = false;
        isConnecting = false; // Reset lock just in case
        currentConnectedMac = null;
        try {
            if (socket != null) socket.close();
        } catch (Exception e) {
            Log.e(TAG, "Error closing", e);
        }
    }

    private void updateStatus(String msg) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> statusText.setText(msg));
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

    private void processReceivedData(String rawData) {
        try {
            String timestamp = java.text.DateFormat.getTimeInstance().format(new java.util.Date());
            statusText.setText("Status: Active\nLast Update: " + timestamp);

            JSONObject json = new JSONObject(rawData.replace("'", "\""));
            JsonUiRenderer.render(requireContext(), json, container);
        } catch (JSONException e) {
            statusText.setText("Parsing Error:\n" + rawData);
        }
    }
}
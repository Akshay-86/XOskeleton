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
    private long lastUiUpdate = 0;

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

    // This runs when the App comes from background (e.g. User was on YouTube)
    @Override
    public void onResume() {
        super.onResume();
        if (!isHidden()) {
            checkAndConnect();
        }
    }
    
    // NEW: This runs when switching TABS (Exo -> Profile -> Exo)
    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) {
            // We are visible again! Refresh the UI.
            checkAndConnect();
        }
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

        // RESTORE UI STATE: If already connected, update the text immediately
        if (isRunning) {
            statusText.setText("Status: Active (Connected)");
        }

        if (isConnecting) return;

        // Only start a new connection if we aren't already running on this device
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
            if (getActivity() == null) {
                isConnecting = false;
                return;
            }

            // 1. FIXED PERMISSIONS CHECK
            // On Android 12 (S) and newer, we MUST check BLUETOOTH_CONNECT.
            // On Android 11 and older, we skip this check (it's implicit).
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    updateStatus("Permission Denied");
                    isConnecting = false;
                    return;
                }
            }

            // 2. Stop Scanning & Wait
            // (Note: cancelDiscovery also needs a permission check on A12, but usually works fine without it on A11 if location is granted)
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                    || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) {
                BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
            }

            try {
                updateStatus("Initializing...");
                Thread.sleep(600);
            } catch (InterruptedException ignored) {}

            boolean connected = false;

            // 3. Try Ports
            for (int port = 1; port <= 3; port++) {
                if (getActivity() == null) break;

                // Note: device.getName() also requires permission on A12, but we can usually skip the check here
                // since we already checked above, or use a try-catch for safety if you want to be super strict.
                String deviceName = "Device";
                if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S || ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    deviceName = device.getName();
                }

                updateStatus("Connecting to " + deviceName + " (Port " + port + ")...");

                if (connectToPort(device, port)) {
                    connected = true;
                    break;
                }

                try { Thread.sleep(200); } catch (Exception e) {}
            }

            if (connected) {
                isConnecting = false;
                startReadingData();
            } else {
                updateStatus("Connection Failed. Is the server running?");
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


    private void startReadingData() {
        updateStatus("Connected! Waiting for data...");
        isRunning = true;

        try {
            InputStream inputStream = socket.getInputStream();
            while (isRunning) {
                // 1. READ DATA
                byte[] lengthHeader = new byte[2];
                readExactly(inputStream, lengthHeader, 2);
                int payloadSize = ((lengthHeader[0] & 0xFF) << 8) | (lengthHeader[1] & 0xFF);

                byte[] payload = new byte[payloadSize];
                readExactly(inputStream, payload, payloadSize);

                String jsonString = new String(payload, StandardCharsets.UTF_8);

                // 2. LOG EVERYTHING (Fast)
                // This will print every single packet, even if it's 1000 per second.
                Log.d("DataStream", "Received: " + jsonString);

                // 3. THROTTLE UI ONLY (Slow)
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastUiUpdate > 100) { // 100ms delay
                    lastUiUpdate = currentTime;

                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> processReceivedData(jsonString));
                    }
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
            Log.e("JSON", rawData);
        } catch (JSONException e) {
            statusText.setText("Parsing Error:\n" + rawData);
        }
    }
}
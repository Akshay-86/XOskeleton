package com.example.XOskeleton;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.List;


public class DeviceDiscoveryManager { // Changed from 'public class DeviceDiscoveryManager extends Context {'

    private static final String TAG = "DeviceDiscoveryManager";

    public interface Callback {
        void onDeviceFound(BluetoothDevice device);
    }

    private final BluetoothAdapter adapter;
    private final Callback callback;
    private final Context context; // This is good, you pass the context in the constructor
    private boolean isReceiverRegistered = false;

    public DeviceDiscoveryManager(Context ctx, Callback callback) {
        this.context = ctx;
        this.callback = callback;
        adapter = BluetoothAdapter.getDefaultAdapter();
    }

    public void start() {
        if (adapter == null) {
            Log.e(TAG, "BluetoothAdapter is null. Bluetooth not supported.");
            return;
        }

        if (!isReceiverRegistered) {
            IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
            filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
            filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
            context.registerReceiver(receiver, filter); // Correctly uses the 'context' member
            isReceiverRegistered = true;
            Log.d(TAG, "BroadcastReceiver registered.");
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED) {

                Log.w(TAG, "BLUETOOTH_SCAN permission not granted");
                return;
            }
        }
        if (adapter.isDiscovering()) {
            adapter.cancelDiscovery();
            Log.d(TAG, "Cancelling ongoing discovery.");
        }

        // Requires BLUETOOTH_SCAN permission for Android 12+
        // This permission check should be handled in MainActivity before calling start()
        try {
            adapter.startDiscovery();
            Log.d(TAG, "Starting Bluetooth device discovery.");
        } catch (SecurityException e) {
            Log.e(TAG, "BLUETOOTH_SCAN permission not granted for startDiscovery()", e);
            // Handle the case where permission is not granted (e.g., inform user)
        }
    }

    public void stop() {
        if (adapter != null && adapter.isDiscovering()) {
            try {
                adapter.cancelDiscovery();
                Log.d(TAG, "Cancelling Bluetooth device discovery.");
            } catch (SecurityException e) {
                Log.e(TAG, "BLUETOOTH_SCAN permission not granted for cancelDiscovery()", e);
            }
        }
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(receiver); // Correctly uses the 'context' member
                isReceiverRegistered = false;
                Log.d(TAG, "BroadcastReceiver unregistered.");
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Receiver not registered, or already unregistered.", e);
            }
        }
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {

            if (intent == null || intent.getAction() == null) return;

            String action = intent.getAction();

            if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                Log.d(TAG, "Bluetooth discovery started");
                return;
            }

            if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                Log.d(TAG, "Bluetooth discovery finished");
                return;
            }

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {

                BluetoothDevice device;

                // ✅ Handle API 33+ safely
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    device = intent.getParcelableExtra(
                            BluetoothDevice.EXTRA_DEVICE,
                            BluetoothDevice.class
                    );
                } else {
                    device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                }

                if (device == null) return;

                // 🔐 Guard BLUETOOTH_CONNECT access
                try {
                    String name = device.getName();     // may throw
                    String address = device.getAddress();

                    Log.d(TAG, "Found device: " + name + " (" + address + ")");
                    callback.onDeviceFound(device);

                } catch (SecurityException se) {
                    Log.e(TAG,
                            "Missing BLUETOOTH_CONNECT permission to read device info",
                            se
                    );
                }
            }
        }
    };

}
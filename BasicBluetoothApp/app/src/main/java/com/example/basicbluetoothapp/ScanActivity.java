package com.example.basicbluetoothapp;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ScanActivity extends AppCompatActivity {

    private static final int REQ_BT_PERMISSIONS = 101;
    private List<BluetoothDevice> devices = new ArrayList<>();
    private DeviceListAdapter adapter;
    private DeviceDiscoveryManager discoveryManager;
    private BluetoothAdapter bluetoothAdapter;

    private final ActivityResultLauncher<Intent> enableBtLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) startScanning();
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan); // Make sure you create this XML below

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        RecyclerView recycler = findViewById(R.id.deviceRecycler);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DeviceListAdapter(devices, this::handleDeviceClick);
        recycler.setAdapter(adapter);

        Button btnScan = findViewById(R.id.btnScan);
        btnScan.setOnClickListener(v -> startDiscovery());

        startScanning();
    }

    private void startScanning() {
        if (!hasPermissions()) {
            requestBluetoothPermissions();
            return;
        }
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            enableBtLauncher.launch(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
            return;
        }

        loadPairedDevices();
        startDiscovery();
    }

    private void handleDeviceClick(BluetoothDevice device) {
        if (discoveryManager != null) discoveryManager.stop();

        // 1. SAVE THE CHOSEN DEVICE
        String name = "Unknown";
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            name = device.getName();
        }
        BluetoothPrefs.saveDevice(this, name, device.getAddress());

        // 2. CLOSE THIS SCREEN
        Toast.makeText(this, "Saved " + name, Toast.LENGTH_SHORT).show();
        finish(); // Go back to Exoskeleton Fragment
    }

    // --- Standard Boilerplate Below (Same as before) ---

    private void loadPairedDevices() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return;
        devices.clear();
        Set<BluetoothDevice> paired = bluetoothAdapter.getBondedDevices();
        if (paired != null) devices.addAll(paired);
        adapter.notifyDataSetChanged();
    }

    private void startDiscovery() {
        if (discoveryManager != null) discoveryManager.stop();
        if (devices.isEmpty()) loadPairedDevices();
        discoveryManager = new DeviceDiscoveryManager(this, device -> {
            if (!devices.contains(device)) {
                devices.add(device);
                adapter.notifyItemInserted(devices.size() - 1);
            }
        });
        discoveryManager.start();
    }

    private boolean hasPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        } else {
            return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestBluetoothPermissions() {
        List<String> permissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN);
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
        } else {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        ActivityCompat.requestPermissions(this, permissions.toArray(new String[0]), REQ_BT_PERMISSIONS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_BT_PERMISSIONS && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startScanning();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (discoveryManager != null) discoveryManager.stop();
    }
}
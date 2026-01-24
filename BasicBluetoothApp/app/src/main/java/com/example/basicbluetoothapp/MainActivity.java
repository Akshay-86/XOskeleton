package com.example.basicbluetoothapp;

import android.Manifest;
import android.annotation.SuppressLint;
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

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int REQ_BT_PERMISSIONS = 101;

    // TODO: CHANGE THIS TO YOUR PC's BLUETOOTH NAME!
    // Example: "ubuntu-0", "Akshay-PC", "raspberrypi"
    private static final String TARGET_DEVICE_NAME = "BR-CB-02";

    List<BluetoothDevice> devices = new ArrayList<>();
    DeviceListAdapter adapter;
    DeviceDiscoveryManager discoveryManager;
    BluetoothAdapter bluetoothAdapter;

    private final ActivityResultLauncher<Intent> enableBtLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    startScanningProcess();
                } else {
                    Toast.makeText(this, "Bluetooth is required", Toast.LENGTH_SHORT).show();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        RecyclerView recycler = findViewById(R.id.deviceRecycler);
        recycler.setLayoutManager(new LinearLayoutManager(this));

        adapter = new DeviceListAdapter(devices, this::handleDeviceClick);
        recycler.setAdapter(adapter);

        Button btnScan = findViewById(R.id.btnScan);
        btnScan.setOnClickListener(v -> {
            // Manual Scan: Force discovery even if paired
            startDiscovery();
        });

        startScanningProcess();
    }

    private void startScanningProcess() {
        // 1. Check Permissions
        if (!hasPermissions()) {
            requestBluetoothPermissions();
            return;
        }

        // 2. Check if Bluetooth is On
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            enableBtLauncher.launch(enableBtIntent);
            return;
        }

        // 3. AUTO-CONNECT: Check if PC is already paired
        if (tryAutoConnectToPairedDevice()) {
            return; // Skip discovery screen!
        }

        // 4. If not found, show list and start discovery
        loadPairedDevices();
        startDiscovery();
    }

    private boolean tryAutoConnectToPairedDevice() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }

        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        if (pairedDevices != null) {
            for (BluetoothDevice device : pairedDevices) {
                String name = device.getName();

                // Check if this paired device matches your target PC name
                if (name != null && name.equals(TARGET_DEVICE_NAME)) {
                    Toast.makeText(this, "Auto-connecting to " + name, Toast.LENGTH_SHORT).show();
                    openClientActivity(device);
                    return true; // Found it!
                }
            }
        }
        return false;
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

    @SuppressLint("NotifyDataSetChanged")
    private void loadPairedDevices() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        devices.clear();
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        if (pairedDevices != null) {
            devices.addAll(pairedDevices);
            adapter.notifyDataSetChanged();
        }
    }

    private void startDiscovery() {
        if (discoveryManager != null) discoveryManager.stop();

        // Ensure we keep the paired devices in the list when scanning starts
        if (devices.isEmpty()) loadPairedDevices();

        discoveryManager = new DeviceDiscoveryManager(this, device -> {
            if (!devices.contains(device)) {
                devices.add(device);
                adapter.notifyItemInserted(devices.size() - 1);
            }
        });
        discoveryManager.start();
    }

    private void handleDeviceClick(BluetoothDevice device) {
        if (discoveryManager != null) discoveryManager.stop();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestBluetoothPermissions();
            return;
        }

        if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
            openClientActivity(device);
        } else {
            Toast.makeText(this, "Pairing...", Toast.LENGTH_SHORT).show();
            device.createBond();

            IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
            registerReceiver(bondReceiver, filter);
        }
    }

    private final BroadcastReceiver bondReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(intent.getAction())) {
                BluetoothDevice device;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
                } else {
                    device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                }

                if (device != null && device.getBondState() == BluetoothDevice.BOND_BONDED) {
                    Toast.makeText(context, "Paired!", Toast.LENGTH_SHORT).show();
                    try { unregisterReceiver(this); } catch (Exception e) {}
                    openClientActivity(device);
                }
            }
        }
    };

    private void openClientActivity(BluetoothDevice device) {
        Intent intent = new Intent(this, ClientActivity.class);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        startActivity(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_BT_PERMISSIONS && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startScanningProcess();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (discoveryManager != null) discoveryManager.stop();
        try { unregisterReceiver(bondReceiver); } catch (Exception e) {}
    }
}
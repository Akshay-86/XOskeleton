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
import android.util.Log;
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

    List<BluetoothDevice> devices = new ArrayList<>();
    DeviceListAdapter adapter;
    DeviceDiscoveryManager discoveryManager;
    BluetoothAdapter bluetoothAdapter;

    // Launcher to handle the "Turn On Bluetooth" dialog result
    private final ActivityResultLauncher<Intent> enableBtLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    Toast.makeText(this, "Bluetooth Enabled", Toast.LENGTH_SHORT).show();
                    loadPairedDevices(); // Load immediately after enabling
                    startDiscovery();
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
        btnScan.setOnClickListener(v -> startScanningProcess());

        // Initial Start
        startScanningProcess();
    }

    /**
     * The main entry point for scanning.
     * 1. Checks Permissions
     * 2. Checks if BT is Enabled
     * 3. Loads Paired Devices
     * 4. Starts Discovery
     */
    private void startScanningProcess() {
        // 1. Check Runtime Permissions First
        if (!hasPermissions()) {
            requestBluetoothPermissions();
            return;
        }

        // 2. Check if Bluetooth is Enabled
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            enableBtLauncher.launch(enableBtIntent);
            return;
        }

        // 3. Load already paired devices (So list isn't blank!)
        loadPairedDevices();

        // 4. Scan for new ones
        startDiscovery();
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

        // Add paired devices to the list immediately
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        if (pairedDevices != null && !pairedDevices.isEmpty()) {
            for (BluetoothDevice device : pairedDevices) {
                if (!devices.contains(device)) {
                    devices.add(device);
                }
            }
            adapter.notifyDataSetChanged();
        }
    }

    private void startDiscovery() {
        if (discoveryManager != null) {
            discoveryManager.stop();
        }

        // NOTE: We do NOT clear 'devices' here, because we want to keep the Paired devices visible
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
            // Already paired -> Go to Server Activity
            openServerActivity();
        } else {
            // Not paired -> Pair it
            Toast.makeText(this, "Pairing...", Toast.LENGTH_SHORT).show();
            device.createBond();
            // Register receiver to listen for the result
            IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
            registerReceiver(bondReceiver, filter);
        }
    }

    private final BroadcastReceiver bondReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(intent.getAction())) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null && device.getBondState() == BluetoothDevice.BOND_BONDED) {
                    Toast.makeText(context, "Paired Successfully!", Toast.LENGTH_SHORT).show();

                    // SAFE UNREGISTER (Prevents Crash)
                    try { unregisterReceiver(this); } catch (Exception e) { e.printStackTrace(); }

                    openServerActivity();
                }
            }
        }
    };

    private void openServerActivity() {
        Intent intent = new Intent(this, ServerActivity.class);
        startActivity(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_BT_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startScanningProcess();
            } else {
                Toast.makeText(this, "Permissions Denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (discoveryManager != null) discoveryManager.stop();
        // Prevent crash if receiver was never registered or already unregistered
        try { unregisterReceiver(bondReceiver); } catch (Exception e) {}
    }
}
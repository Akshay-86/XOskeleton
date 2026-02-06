package com.example.XOskeleton;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
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
        setContentView(R.layout.activity_scan);

        BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = manager.getAdapter();


        RecyclerView recycler = findViewById(R.id.deviceRecycler);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DeviceListAdapter(devices, this::handleDeviceClick);
        recycler.setAdapter(adapter);

        Button btnScan = findViewById(R.id.btnScan);
        btnScan.setOnClickListener(v -> startScanning());

        startScanning();
    }

    private void handleDeviceClick(BluetoothDevice device) {
        if (discoveryManager != null) discoveryManager.stop();

        // Stop discovery (Safe check)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
            bluetoothAdapter.cancelDiscovery();
        } else {
            bluetoothAdapter.cancelDiscovery();
        }

        // FIX: Use the new helper instead of checking BLUETOOTH_CONNECT directly
        if (!hasConnectPermission()) {
            requestBluetoothPermissions(); // Ask for permissions if missing
            return; // Don't finish here yet, wait for user response in onRequestPermissionsResult
        }

        if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
            saveAndFinish(device);
        } else {
            Toast.makeText(this, "Pairing with " + device.getName() + "...", Toast.LENGTH_SHORT).show();
            device.createBond();

            IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
            registerReceiver(bondReceiver, filter);
        }
    }
    private final BroadcastReceiver bondReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(intent.getAction())) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE);

                // SUCCESS:
                if (bondState == BluetoothDevice.BOND_BONDED) {
                    Toast.makeText(context, "Pairing Successful!", Toast.LENGTH_SHORT).show();
                    try { unregisterReceiver(this); } catch (Exception e) {}

                    // Now we are allowed to save and leave
                    saveAndFinish(device);
                }
                // FAILED:
                else if (bondState == BluetoothDevice.BOND_NONE) {
                    Toast.makeText(context, "Pairing Rejected or Failed.", Toast.LENGTH_SHORT).show();
                    // We DO NOT finish. User stays here to try again.
                }
            }
        }
    };

    private void saveAndFinish(BluetoothDevice device) {
        // FIX: Use the helper here too
        if (!hasConnectPermission()) return;
        String name;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED) {
                return; // permission missing — safely exit
            }
        }

        name = device.getName();
        if (name == null) name = "Unknown Device";


        BluetoothPrefs.saveDevice(this, name, device.getAddress());
        finish();
    }

    // --- Standard Scanning Code ---

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
            // Android 12+: Check ONLY for Bluetooth keys.
            // We DO NOT check for Location because we added 'neverForLocation' in Manifest.
            return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        } else {
            // Android 11 and below: WE HAVE NO CHOICE. We must ask for Location.
            return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestBluetoothPermissions() {
        List<String> permissions = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+: Ask for Bluetooth ONLY. System will NOT show "Location" popup.
            permissions.add(Manifest.permission.BLUETOOTH_SCAN);
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
        } else {
            // Android 11-: Must ask for Location.
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

    private boolean hasConnectPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        }
        // On Android 11 and below, BLUETOOTH_CONNECT doesn't exist.
        // We assume permission is granted because it's in the Manifest.
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (discoveryManager != null) discoveryManager.stop();
        try { unregisterReceiver(bondReceiver); } catch (Exception e) {}
    }
}

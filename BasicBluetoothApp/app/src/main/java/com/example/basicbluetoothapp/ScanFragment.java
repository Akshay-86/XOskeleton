package com.example.basicbluetoothapp;

import android.Manifest;
import android.annotation.SuppressLint;
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
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ScanFragment extends Fragment {

    private static final String TAG = "ScanFragment";
    private static final int REQ_BT_PERMISSIONS = 101;

    // TODO: CHANGE THIS TO YOUR PC's BLUETOOTH NAME!
    private static final String TARGET_DEVICE_NAME = "BR-CB-02";

    private List<BluetoothDevice> devices = new ArrayList<>();
    private DeviceListAdapter adapter;
    private DeviceDiscoveryManager discoveryManager;
    private BluetoothAdapter bluetoothAdapter;

    private final ActivityResultLauncher<Intent> enableBtLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    startScanningProcess();
                } else {
                    Log.e(TAG, "Bluetooth permission is denied");
                    Toast.makeText(requireContext(), "Bluetooth is required", Toast.LENGTH_SHORT).show();
                    // We don't finish() here because it would close the whole app.
                    // Instead, just stop the process.
                }
            }
    );

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Inflate the layout for this fragment (fragment_scan.xml)
        return inflater.inflate(R.layout.fragment_scan, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // 1. Setup RecyclerView using 'view.findViewById'
        RecyclerView recycler = view.findViewById(R.id.deviceRecycler);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));

        adapter = new DeviceListAdapter(devices, this::handleDeviceClick);
        recycler.setAdapter(adapter);

        // 2. Setup Scan Button
        Button btnScan = view.findViewById(R.id.btnScan);
        btnScan.setOnClickListener(v -> {
            startDiscovery();
            Log.d(TAG, "Manual Scan Started");
        });

        // 3. Start Logic
        startScanningProcess();
    }

    private void startScanningProcess() {
        if (!isAdded()) return; // Safety check: ensure fragment is attached

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
            Log.d(TAG, "Auto-connect successful");
            return;
        }

        // 4. If not found, show list and start discovery
        loadPairedDevices();
        startDiscovery();
    }

    private boolean tryAutoConnectToPairedDevice() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }

        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        if (pairedDevices != null) {
            for (BluetoothDevice device : pairedDevices) {
                String name = device.getName();
                if (name != null && name.equals(TARGET_DEVICE_NAME)) {
                    Toast.makeText(requireContext(), "Auto-connecting to " + name, Toast.LENGTH_SHORT).show();
                    openClientActivity(device);
                    return true;
                }
            }
        }
        Log.d(TAG, "No paired device found");
        return false;
    }

    private boolean hasPermissions() {
        Context ctx = requireContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ActivityCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ActivityCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
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
        // Use requestPermissions (Fragment method) or ActivityCompat via requireActivity()
        requestPermissions(permissions.toArray(new String[0]), REQ_BT_PERMISSIONS);
    }

    @SuppressLint("NotifyDataSetChanged")
    private void loadPairedDevices() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
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

        if (devices.isEmpty()) loadPairedDevices();

        discoveryManager = new DeviceDiscoveryManager(requireContext(), device -> {
            if (!devices.contains(device)) {
                devices.add(device);
                adapter.notifyItemInserted(devices.size() - 1);
            }
        });
        discoveryManager.start();
    }

    private void handleDeviceClick(BluetoothDevice device) {
        if (discoveryManager != null) discoveryManager.stop();

        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestBluetoothPermissions();
            return;
        }

        if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
            openClientActivity(device);
        } else {
            Toast.makeText(requireContext(), "Pairing...", Toast.LENGTH_SHORT).show();
            device.createBond();

            IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
            requireContext().registerReceiver(bondReceiver, filter);
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
                    try { context.unregisterReceiver(this); } catch (Exception e) {}
                    openClientActivity(device);
                }
            }
        }
    };

    private void openClientActivity(BluetoothDevice device) {
        Intent intent = new Intent(requireContext(), ClientActivity.class);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        startActivity(intent);

        // Optional: Finish MainActivity if you don't want to go back to the tabs
        // requireActivity().finish();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_BT_PERMISSIONS && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startScanningProcess();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (discoveryManager != null) discoveryManager.stop();
        try { requireContext().unregisterReceiver(bondReceiver); } catch (Exception e) {}
    }
}
package com.example.XOskeleton;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private Fragment fragment1; // Exo
    private Fragment fragment2; // Stats
    private Fragment fragment3; // Profile
    private Fragment fragment4; // Dev

    private final FragmentManager fm = getSupportFragmentManager();
    private Fragment active = fragment1;

    private TextView deviceNameText;
    private TextView deviceMacText;
    private ImageButton btnStop;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // --- 1. ASK FOR PERMISSIONS IMMEDIATELY ---
        checkBluetoothPermissions();

        Toolbar toolbar = findViewById(R.id.my_toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayShowTitleEnabled(false);

        deviceNameText = findViewById(R.id.device_name);
        deviceMacText = findViewById(R.id.device_mac);
        btnStop = findViewById(R.id.reconnect); // This seems to be the disconnect button based on logic

        // --- Fragment Logic ---
        if (savedInstanceState == null) {
            fragment1 = new ExoskeletonFragment();
            fragment2 = new StatsFragment();
            fragment3 = new ProfileFragment();
            fragment4 = new DevFragment();

            fm.beginTransaction().add(R.id.fragment_container, fragment4, "4").hide(fragment4).commit();
            fm.beginTransaction().add(R.id.fragment_container, fragment3, "3").hide(fragment3).commit();
            fm.beginTransaction().add(R.id.fragment_container, fragment2, "2").hide(fragment2).commit();
            fm.beginTransaction().add(R.id.fragment_container, fragment1, "1").commit();
            active = fragment1;
        } else {
            fragment1 = fm.findFragmentByTag("1");
            fragment2 = fm.findFragmentByTag("2");
            fragment3 = fm.findFragmentByTag("3");
            fragment4 = fm.findFragmentByTag("4");

            // Restore active state logic could be improved, but relying on "active" var is tricky across restarts
            // For now, defaulting to fragment1 if lost is safe
            if (active == null) active = fragment1;
        }

        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            Fragment selected = null;

            if (itemId == R.id.nav_exo) selected = fragment1;
            else if (itemId == R.id.nav_stats) selected = fragment2;
            else if (itemId == R.id.nav_profile) selected = fragment3;
            else if (itemId == R.id.nav_dev) selected = fragment4;

            if (selected != null) {
                fm.beginTransaction().hide(active).show(selected).commit();
                active = selected;
                return true;
            }
            return false;
        });

        setupHeaderActions();
    }

    // --- PERMISSION LOGIC ---
    private void checkBluetoothPermissions() {
        List<String> requiredPermissions = new ArrayList<>();

        // Android 12+ (API 31+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                requiredPermissions.add(Manifest.permission.BLUETOOTH_SCAN);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                requiredPermissions.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
        }
        // Android 11 and below (needs Location for scanning)
        else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requiredPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
            }
        }

        if (!requiredPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(this, requiredPermissions.toArray(new String[0]), 100);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateHeaderInfo();
    }

    private void updateHeaderInfo() {
        String name = BluetoothPrefs.getLastName(this);
        String mac = BluetoothPrefs.getLastAddress(this);

        if (mac != null) {
            deviceNameText.setText(name != null ? name : "Unknown");
            deviceMacText.setText(mac);
            findViewById(R.id.device_details).setVisibility(View.VISIBLE);
        } else {
            deviceNameText.setText("No Device");
            deviceMacText.setText("Tap Scan to add");
        }
    }

    @SuppressLint("DetachAndAttachSameFragment")
    private void setupHeaderActions() {
        btnStop.setOnClickListener(v -> {
            BluetoothPrefs.clearDevice(this);

            ExoViewModel viewModel = new ViewModelProvider(this).get(ExoViewModel.class);
            viewModel.disconnect();

            updateHeaderInfo();

            if (fragment1 != null && active == fragment1) {
                fm.beginTransaction().detach(fragment1).attach(fragment1).commit();
            }

            Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show();
        });
    }
}
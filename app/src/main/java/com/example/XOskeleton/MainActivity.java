package com.example.XOskeleton;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;


import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    // --- 1. Fragment Management (Keeps Bluetooth Alive) ---
    private final Fragment fragment1 = new ExoskeletonFragment(); // Home/Exo
    private final Fragment fragment2 = new StatsFragment();       // Stats
    private final Fragment fragment3 = new ProfileFragment();     // Profile
    private final FragmentManager fm = getSupportFragmentManager();
    private Fragment active = fragment1; // Track active fragment

    // --- 2. Toolbar UI Elements ---
    private TextView deviceNameText;
    private TextView deviceMacText;
    private ImageButton btnDisconnect;
    private ImageButton btnChangeDevice;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // --- A. Setup Custom Toolbar ---
        Toolbar toolbar = findViewById(R.id.my_toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        // --- B. Initialize Toolbar Views ---
        deviceNameText = findViewById(R.id.device_name);
        deviceMacText = findViewById(R.id.device_mac);
        btnDisconnect = findViewById(R.id.reconnect);   // Icon: bluetooth_off
        btnChangeDevice = findViewById(R.id.disconnect); // Icon: refresh/swap

        // --- C. Setup Bottom Navigation ---
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setOnItemSelectedListener(navListener);

        // --- D. Initialize Fragments (Add all, Hide others) ---
        // We add them all once so they stay alive in memory
        fm.beginTransaction().add(R.id.fragment_container, fragment3, "3").hide(fragment3).commit();
        fm.beginTransaction().add(R.id.fragment_container, fragment2, "2").hide(fragment2).commit();
        fm.beginTransaction().add(R.id.fragment_container, fragment1, "1").commit(); // Show Exo by default

        // --- E. Setup Header Button Actions ---
        setupHeaderActions();
    }

    // --- Navigation Listener (Handles Tab Switching) ---
    private final BottomNavigationView.OnItemSelectedListener navListener =
            new BottomNavigationView.OnItemSelectedListener() {
                @Override
                public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                    int itemId = item.getItemId();

                    // Using if-else to switch tabs
                    if (itemId == R.id.nav_exo) {
                        fm.beginTransaction().hide(active).show(fragment1).commit();
                        active = fragment1;
                        return true;
                    } else if (itemId == R.id.nav_stats) {
                        fm.beginTransaction().hide(active).show(fragment2).commit();
                        active = fragment2;
                        return true;
                    } else if (itemId == R.id.nav_profile) {
                        fm.beginTransaction().hide(active).show(fragment3).commit();
                        active = fragment3;
                        return true;
                    }
                    return false;
                }
            };

    @Override
    protected void onResume() {
        super.onResume();
        // Update the header text every time we return to this activity
        updateHeaderInfo();
    }

    private void updateHeaderInfo() {
        String name = BluetoothPrefs.getLastName(this);
        String mac = BluetoothPrefs.getLastAddress(this);

        if (mac != null) {
            deviceNameText.setText(name);
            deviceMacText.setText(mac);
            findViewById(R.id.device_details).setVisibility(View.VISIBLE);
        } else {
            deviceNameText.setText("No Device Connected");
            deviceMacText.setText("Tap on the icon to connect");
            // Optional: Hide details view if preferred
        }
    }

    @SuppressLint("DetachAndAttachSameFragment")
    private void setupHeaderActions() {
        // 1. Change Device Button (Refresh Icon)
        btnChangeDevice.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ScanActivity.class);
            startActivity(intent);
        });

        // 2. Disconnect Button (Bluetooth Off Icon)
        btnDisconnect.setOnClickListener(v -> {
            // Clear Preferences
            BluetoothPrefs.clearDevice(this);
            updateHeaderInfo();

            // Refresh the Active Fragment to reflect the "Disconnected" state
            // If we are on the Exo tab, we simply hide and show it to trigger 'onHiddenChanged'
            // or we can just detach/attach it to force a reload.
            // For now, simpler is better:
            if (active instanceof ExoskeletonFragment) {
                // Force a reload of the Exo fragment to stop the connection
                fm. beginTransaction().detach(fragment1).attach(fragment1).commit();
            }
        });
    }
}
package com.example.XOskeleton;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    // Fragments
    private final Fragment fragment1 = new ExoskeletonFragment(); // Home
    private final Fragment fragment2 = new StatsFragment();       // Stats
    private final Fragment fragment3 = new ProfileFragment();     // Profile
    private final FragmentManager fm = getSupportFragmentManager();
    private Fragment active = fragment1;

    // Toolbar Elements
    private TextView deviceNameText;
    private TextView deviceMacText;
    private ImageButton btnStop;   // The "Power Off" / Disconnect icon

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // --- 1. Toolbar Setup ---
        Toolbar toolbar = findViewById(R.id.my_toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayShowTitleEnabled(false);

        deviceNameText = findViewById(R.id.device_name);
        deviceMacText = findViewById(R.id.device_mac);

        // This button ID is 'reconnect' in XML, but uses the 'bluetooth_off' icon
        btnStop = findViewById(R.id.reconnect);

        // --- 2. Navigation Setup ---
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
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
        });

        // --- 3. Initialize Fragments ---
        // Add all fragments to the manager but hide Stats and Profile
        fm.beginTransaction().add(R.id.fragment_container, fragment3, "3").hide(fragment3).commit();
        fm.beginTransaction().add(R.id.fragment_container, fragment2, "2").hide(fragment2).commit();
        fm.beginTransaction().add(R.id.fragment_container, fragment1, "1").commit();

        setupHeaderActions();
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
            deviceNameText.setText(name);
            deviceMacText.setText(mac);
            findViewById(R.id.device_details).setVisibility(View.VISIBLE);
        } else {
            deviceNameText.setText("No Device");
            deviceMacText.setText("Tap Scan to add");
        }
    }

    @SuppressLint("DetachAndAttachSameFragment")
    private void setupHeaderActions() {
        // --- STOP / DISCONNECT BUTTON LOGIC ---
        btnStop.setOnClickListener(v -> {
            // 1. Wipe Saved Data (Forget Device)
            // Note: Ensure your BluetoothPrefs class has the method 'clearDevice' or 'clear'
            BluetoothPrefs.clearDevice(this);

            // 2. Update Header Immediately to show "No Device"
            updateHeaderInfo();

            // 3. Force Reload the Exoskeleton Fragment
            // This triggers onPause() (disconnects bluetooth) -> onResume() (checks prefs)
            // Since prefs are now empty, the Fragment will set its button to "Add Device"
            fm.beginTransaction().detach(fragment1).attach(fragment1).commit();

            Toast.makeText(this, "Disconnected & Device Forgotten", Toast.LENGTH_SHORT).show();
        });
    }
}
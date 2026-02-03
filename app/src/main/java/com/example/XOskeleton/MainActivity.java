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

    // Fragments - Removed "final = new ..." to prevent duplicates
    private Fragment fragment1; // Exo (Home)
    private Fragment fragment2; // Stats
    private Fragment fragment3; // Profile

    private final FragmentManager fm = getSupportFragmentManager();
    private Fragment active; // Tracks the currently visible tab

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

        // --- 2. Initialize Fragments (THE CRITICAL FIX) ---
        if (savedInstanceState == null) {
            // First Launch: Create NEW fragments
            fragment1 = new ExoskeletonFragment();
            fragment2 = new StatsFragment();
            fragment3 = new ProfileFragment();

            // Add them all, hide Stats/Profile, show Exo
            fm.beginTransaction().add(R.id.fragment_container, fragment3, "3").hide(fragment3).commit();
            fm.beginTransaction().add(R.id.fragment_container, fragment2, "2").hide(fragment2).commit();
            fm.beginTransaction().add(R.id.fragment_container, fragment1, "1").commit();

            active = fragment1;
        } else {
            // App Restarted (Theme Change/Rotation): RECOVER existing fragments
            // Android automatically saves them; we just need to find them by Tag
            fragment1 = fm.findFragmentByTag("1");
            fragment2 = fm.findFragmentByTag("2");
            fragment3 = fm.findFragmentByTag("3");

            // Restore the 'active' pointer to whichever one is currently visible
            if (fragment1 != null && !fragment1.isHidden()) active = fragment1;
            else if (fragment2 != null && !fragment2.isHidden()) active = fragment2;
            else if (fragment3 != null && !fragment3.isHidden()) active = fragment3;
            else active = fragment1; // Fallback
        }

        // --- 3. Navigation Setup ---
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
            BluetoothPrefs.clearDevice(this);

            // 2. Update Header Immediately to show "No Device"
            updateHeaderInfo();

            // 3. Force Reload the Exoskeleton Fragment
            // We use 'fragment1' specifically because that's where the Bluetooth connection lives
            if (fragment1 != null) {
                fm.beginTransaction().detach(fragment1).attach(fragment1).commit();
            }

            Toast.makeText(this, "Disconnected & Device Forgotten", Toast.LENGTH_SHORT).show();
        });
    }
}
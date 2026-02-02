package com.example.XOskeleton;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    // Fragments
    private final Fragment fragment1 = new ExoskeletonFragment();
    private final Fragment fragment2 = new StatsFragment();
    private final Fragment fragment3 = new ProfileFragment();
    private final FragmentManager fm = getSupportFragmentManager();
    private Fragment active = fragment1;

    // Toolbar
    private TextView deviceNameText;
    private TextView deviceMacText;
    private ImageButton btnStop;   // Icon: bluetooth_off

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // --- Toolbar Setup ---
        Toolbar toolbar = findViewById(R.id.my_toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayShowTitleEnabled(false);

        deviceNameText = findViewById(R.id.device_name);
        deviceMacText = findViewById(R.id.device_mac);

        // --- MAPPING BUTTONS CORRECTLY ---
        // ID "reconnect" has the 'bluetooth_off' icon -> So it acts as STOP/DISCONNECT
        btnStop = findViewById(R.id.reconnect);


        // --- Navigation Setup ---
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setOnItemSelectedListener(navListener);

        // Load Fragments
        fm.beginTransaction().add(R.id.fragment_container, fragment3, "3").hide(fragment3).commit();
        fm.beginTransaction().add(R.id.fragment_container, fragment2, "2").hide(fragment2).commit();
        fm.beginTransaction().add(R.id.fragment_container, fragment1, "1").commit();

        setupHeaderActions();
    }

    private final BottomNavigationView.OnItemSelectedListener navListener = item -> {
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
    };

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

    private void setupHeaderActions() {

        // --- BUTTON 2: DISCONNECT / STOP (Power Off Icon) ---
        btnStop.setOnClickListener(v -> {
            // 1. Wipe Save Data
            BluetoothPrefs.clearDevice(this);
            updateHeaderInfo();

            // 2. Reload the Main Fragment (Effectively killing the connection)
            fm.beginTransaction().detach(fragment1).attach(fragment1).commit();

            Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show();
        });
    }
}
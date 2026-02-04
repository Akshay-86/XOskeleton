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
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private Fragment fragment1; // Exo
    private Fragment fragment2; // Stats
    private Fragment fragment3; // Profile
    private Fragment fragment4; // Dev

    private final FragmentManager fm = getSupportFragmentManager();
    private Fragment active;

    private TextView deviceNameText;
    private TextView deviceMacText;
    private ImageButton btnStop;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.my_toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayShowTitleEnabled(false);

        deviceNameText = findViewById(R.id.device_name);
        deviceMacText = findViewById(R.id.device_mac);
        btnStop = findViewById(R.id.reconnect);

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

            // Restore active state
            if (fragment1 != null && !fragment1.isHidden()) active = fragment1;
            else if (fragment2 != null && !fragment2.isHidden()) active = fragment2;
            else if (fragment3 != null && !fragment3.isHidden()) active = fragment3;
            else if (fragment4 != null && !fragment4.isHidden()) active = fragment4;
            else active = fragment1;
        }

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
            } else if (itemId == R.id.nav_dev) {
                fm.beginTransaction().hide(active).show(fragment4).commit();
                active = fragment4;
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
        btnStop.setOnClickListener(v -> {
            // 1. Wipe Saved Data
            BluetoothPrefs.clearDevice(this);

            // 2. KILL THE CONNECTION (Important!)
            ExoViewModel viewModel = new ViewModelProvider(this).get(ExoViewModel.class);
            viewModel.disconnect();

            // 3. Update Toolbar Text
            updateHeaderInfo();

            // 4. Force UI Refresh
            // If the Exo fragment is active, refresh it so it sees the disconnect immediately
            if (fragment1 != null) {
                fm.beginTransaction().detach(fragment1).attach(fragment1).commit();
            }

            Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show();
        });
    }
}
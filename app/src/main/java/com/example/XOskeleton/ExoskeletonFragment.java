package com.example.XOskeleton;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import java.text.DateFormat;
import java.util.Date;

public class ExoskeletonFragment extends Fragment {

    // UI
    private LinearLayout container;
    private TextView statusText;
    private Button btnChangeDevice;
    private View btnReload;

    // The Brain
    private ExoViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_exoskeleton, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 1. Setup UI References
        statusText = view.findViewById(R.id.statusText);
        container = view.findViewById(R.id.container);
        btnChangeDevice = view.findViewById(R.id.btnChangeDevice);
        btnReload = view.findViewById(R.id.btnReload);

        // 2. Connect to ViewModel (The Brain)
        viewModel = new ViewModelProvider(requireActivity()).get(ExoViewModel.class);

        // 3. OBSERVE STATUS (Updates UI automatically)
        viewModel.statusMessage.observe(getViewLifecycleOwner(), msg -> {
            statusText.setText(msg);
        });

        // 4. OBSERVE DATA (Updates JSON view automatically)
        viewModel.liveDataPacket.observe(getViewLifecycleOwner(), json -> {
            String timestamp = DateFormat.getTimeInstance().format(new Date());
            statusText.setText("Status: Active\nLast Update: " + timestamp);
            // This renderer handles the "diffing" so it won't flicker
            JsonUiRenderer.render(requireContext(), json, container);
        });

        // 5. OBSERVE CONNECTION STATE (Updates Buttons)
        viewModel.isConnected.observe(getViewLifecycleOwner(), connected -> {
            if (connected) {
                btnChangeDevice.setVisibility(View.GONE); // Hide setup buttons when running
            } else {
                btnChangeDevice.setVisibility(View.VISIBLE);
                updateSetupButtons(); // Reset text
            }
        });

        // 6. Button Logic
        btnChangeDevice.setOnClickListener(v -> startActivity(new Intent(requireContext(), ScanActivity.class)));

        if (btnReload != null) {
            btnReload.setOnClickListener(v -> {
                // Manual Refresh Trigger
                String savedMac = BluetoothPrefs.getLastAddress(requireContext());
                if (Boolean.TRUE.equals(viewModel.isConnected.getValue())) {
                    viewModel.disconnect();
                } else if (savedMac != null) {
                    viewModel.connect(savedMac);
                }
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Check connection on return. If not connected, try to auto-connect.
        if (Boolean.FALSE.equals(viewModel.isConnected.getValue())) {
            String savedMac = BluetoothPrefs.getLastAddress(requireContext());
            if (savedMac != null) {
                viewModel.connect(savedMac);
            } else {
                statusText.setText("No Device Saved");
            }
        }
        updateSetupButtons();
    }

    private void updateSetupButtons() {
        String name = BluetoothPrefs.getLastName(requireContext());
        if (name != null) {
            btnChangeDevice.setText("Change Device (" + name + ")");
        } else {
            btnChangeDevice.setText("Add Device");
        }
    }
}
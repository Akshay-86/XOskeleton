package com.example.XOskeleton;

import android.content.Intent;
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

    private LinearLayout container;
    private TextView statusText;
    private Button btnChangeDevice; // This will now ALWAYS say "Add Device"
    private View btnReload;
    private ExoViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_exoskeleton, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        statusText = view.findViewById(R.id.statusText);
        container = view.findViewById(R.id.container);
        btnChangeDevice = view.findViewById(R.id.btnChangeDevice);
        btnReload = view.findViewById(R.id.btnReload);

        // 1. Set Button to always say "Add Device"
        btnChangeDevice.setText("Add Device");

        // 2. Connect to ViewModel
        viewModel = new ViewModelProvider(requireActivity()).get(ExoViewModel.class);

        // 3. Observer: If Connected -> HIDE buttons. If Disconnected -> SHOW "Add Device"
        viewModel.isConnected.observe(getViewLifecycleOwner(), connected -> {
            if (connected) {
                btnChangeDevice.setVisibility(View.GONE);
                if (btnReload != null) btnReload.setVisibility(View.GONE); // Hide refresh too
            } else {
                btnChangeDevice.setVisibility(View.VISIBLE);
                if (btnReload != null) btnReload.setVisibility(View.VISIBLE);
                statusText.setText("Status: Disconnected");
            }
        });

        // 4. Observer: Live Data
        viewModel.liveDataPacket.observe(getViewLifecycleOwner(), json -> {
            String timestamp = DateFormat.getTimeInstance().format(new Date());
            statusText.setText("Status: Active\nLast Update: " + timestamp);
            JsonUiRenderer.render(requireContext(), json, container);
        });

        // 5. Observer: Status Messages
        viewModel.statusMessage.observe(getViewLifecycleOwner(), msg -> statusText.setText(msg));

        // 6. Click Listeners
        btnChangeDevice.setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), ScanActivity.class));
        });

        if (btnReload != null) {
            btnReload.setOnClickListener(v -> {
                // Try to reconnect to the last saved device (if any)
                String savedMac = BluetoothPrefs.getLastAddress(requireContext());
                if (savedMac != null) {
                    viewModel.connect(savedMac);
                } else {
                    statusText.setText("No device saved. Please Add Device.");
                }
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Auto-connect if not connected and we have a saved device
        if (Boolean.FALSE.equals(viewModel.isConnected.getValue())) {
            String savedMac = BluetoothPrefs.getLastAddress(requireContext());
            if (savedMac != null) {
                viewModel.connect(savedMac);
            }
        }
    }
}
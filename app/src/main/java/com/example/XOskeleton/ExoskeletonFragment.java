package com.example.XOskeleton;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
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
    private Button btnChangeDevice;
    private View btnReload;

    // --- NEW: Command UI Elements ---
    private View layoutCommand;
    private EditText inputCommand;
    private Button btnSetCommand;

    private ExoViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_exoskeleton, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // ... (Your existing findViewByIds) ...
        View bottomControls = view.findViewById(R.id.bottomControls); // The container for Add/Reload buttons
        View navBar = requireActivity().findViewById(R.id.bottom_navigation); // TRY to find Main Nav Bar (if you have one)

        statusText = view.findViewById(R.id.statusText);
        container = view.findViewById(R.id.container);
        btnChangeDevice = view.findViewById(R.id.btnChangeDevice);
        btnReload = view.findViewById(R.id.btnReload);

        // --- NEW: Init Views ---
        layoutCommand = view.findViewById(R.id.layoutCommand);
        inputCommand = view.findViewById(R.id.inputCommand);
        btnSetCommand = view.findViewById(R.id.btnSetCommand);

        // 1. Set Button to always say "Add Device"
        btnChangeDevice.setText("Add Device");

        // 2. Connect to ViewModel
        viewModel = new ViewModelProvider(requireActivity()).get(ExoViewModel.class);

        // 3. Observer: Connection Status updates UI
        viewModel.isConnected.observe(getViewLifecycleOwner(), connected -> {
            if (connected) {
                // Connected: Hide "Add Device", Show Commands
                btnChangeDevice.setVisibility(View.GONE);
                if (btnReload != null) btnReload.setVisibility(View.GONE);

                layoutCommand.setVisibility(View.VISIBLE);
                inputCommand.setEnabled(true);
                btnSetCommand.setEnabled(true);
            } else {
                // Disconnected: Show "Add Device", Hide Commands
                btnChangeDevice.setVisibility(View.VISIBLE);
                if (btnReload != null) btnReload.setVisibility(View.VISIBLE);
                statusText.setText("Status: Disconnected");

                layoutCommand.setVisibility(View.GONE);
                inputCommand.setEnabled(false);
                btnSetCommand.setEnabled(false);
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
                String savedMac = BluetoothPrefs.getLastAddress(requireContext());
                if (savedMac != null) {
                    viewModel.connect(savedMac);
                } else {
                    statusText.setText("No device saved. Please Add Device.");
                }
            });
        }

        // --- NEW: Send Command Logic ---
        btnSetCommand.setOnClickListener(v -> {
            String val = inputCommand.getText().toString().trim();
            if (!val.isEmpty()) {
                // Send command "SET_VAL:100"
                viewModel.sendCommand("SET_VAL:" + val);
                inputCommand.setText(""); // Clear input
            }
        });

        // --- NEW: KEYBOARD LISTENER ---
        // This detects if the keyboard is open by checking if screen height shrank
        view.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            android.graphics.Rect r = new android.graphics.Rect();
            view.getWindowVisibleDisplayFrame(r);
            int screenHeight = view.getRootView().getHeight();

            // If more than 15% of screen is missing, keyboard is probably open
            int keypadHeight = screenHeight - r.bottom;

            if (keypadHeight > screenHeight * 0.15) {
                // KEYBOARD IS OPEN -> Hide the bottom stuff
                bottomControls.setVisibility(View.GONE);
                if (navBar != null) navBar.setVisibility(View.GONE);
            } else {
                // KEYBOARD IS CLOSED -> Show bottom stuff

                // Only show bottomControls if disconnected (based on your logic)
                if (!Boolean.TRUE.equals(viewModel.isConnected.getValue())) {
                    bottomControls.setVisibility(View.VISIBLE);
                }

                if (navBar != null) navBar.setVisibility(View.VISIBLE);
            }
        });
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
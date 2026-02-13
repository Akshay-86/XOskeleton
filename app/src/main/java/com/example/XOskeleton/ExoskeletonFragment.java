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

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.slider.Slider;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

public class ExoskeletonFragment extends Fragment {

    private ExoViewModel viewModel;

    // UI References
    private LinearLayout jsonContainer;
    private View layoutDisconnected; // The "Add Device" container
    private Button btnChangeDevice, btnReload;
    private TextView statusText;

    // Bottom Sheet References
    private View bottomSheet;
    private BottomSheetBehavior<View> sheetBehavior;
    private TextView textCurrentMode, textModeHeading, textModeDesc, textPowerPercent;
    private Slider powerSlider;
    private Button[] modeButtons;

    // Modes
    private static final int MODE_ECO = 0;
    private static final int MODE_TRANS = 1;
    private static final int MODE_HYPER = 2;
    private static final int MODE_FITNESS = 3;
    private int currentMode = MODE_ECO;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_exoskeleton, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(ExoViewModel.class);

        // 1. Bind Views
        statusText = view.findViewById(R.id.statusText);
        jsonContainer = view.findViewById(R.id.jsonContainer);
        layoutDisconnected = view.findViewById(R.id.layoutDisconnected);
        btnChangeDevice = view.findViewById(R.id.btnChangeDevice);
        btnReload = view.findViewById(R.id.btnReload);
        bottomSheet = view.findViewById(R.id.bottomSheet);

        // Sheet Internal Views
        textCurrentMode = view.findViewById(R.id.textCurrentMode);
        textModeHeading = view.findViewById(R.id.textModeHeading);
        textModeDesc = view.findViewById(R.id.textModeDesc);
        textPowerPercent = view.findViewById(R.id.textPowerPercent);
        powerSlider = view.findViewById(R.id.powerSlider);

        modeButtons = new Button[]{
                view.findViewById(R.id.btnEco), view.findViewById(R.id.btnTrans),
                view.findViewById(R.id.btnHyper), view.findViewById(R.id.btnFitness)
        };

        // 2. Setup Bottom Sheet Behavior
        sheetBehavior = BottomSheetBehavior.from(bottomSheet);
        sheetBehavior.setHideable(true); // Allow it to be hidden completely
        sheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN); // Start Hidden

        // 3. Connection Observer (The Logic Switch)
        viewModel.isConnected.observe(getViewLifecycleOwner(), connected -> {
            if (connected) {
                // CONNECTED: Hide Buttons, Show Sheet
                layoutDisconnected.setVisibility(View.GONE);
                statusText.setText("Connected - Active");
                statusText.setTextColor(Color.GREEN);

                // Show the sheet (Peek mode)
                if (sheetBehavior.getState() == BottomSheetBehavior.STATE_HIDDEN) {
                    sheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                }
            } else {
                // DISCONNECTED: Show Buttons, Hide Sheet
                layoutDisconnected.setVisibility(View.VISIBLE);
                statusText.setText("Not Connected");
                statusText.setTextColor(Color.WHITE);

                sheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
            }
        });

        // 4. Live Data Observer
        viewModel.liveDataPacket.observe(getViewLifecycleOwner(), json -> {
            JsonUiRenderer.render(requireContext(), json, jsonContainer);
            String ts = DateFormat.getTimeInstance().format(new Date());
            if(Boolean.TRUE.equals(viewModel.isConnected.getValue())) {
                statusText.setText("Active: " + ts);
            }
        });

        // 5. Interaction Listeners
        setupModeButtons();
        updateModeUI();

        powerSlider.addOnChangeListener((slider, value, fromUser) -> {
            textPowerPercent.setText(String.format(Locale.US, "%.0f%%", value));
            if (fromUser) viewModel.sendCommand("SET_POWER:" + (int)value);
        });

        btnChangeDevice.setOnClickListener(v -> startActivity(new Intent(requireContext(), ScanActivity.class)));

        btnReload.setOnClickListener(v -> {
            String savedMac = BluetoothPrefs.getLastAddress(requireContext());
            if (savedMac != null) viewModel.connect(savedMac);
        });
    }

    private void setupModeButtons() {
        for (int i = 0; i < modeButtons.length; i++) {
            final int modeIndex = i;
            modeButtons[i].setOnClickListener(v -> {
                currentMode = modeIndex;
                updateModeUI();
                viewModel.sendCommand("SET_MODE:" + currentMode);
            });
        }
    }

    private void updateModeUI() {
        for (int i = 0; i < modeButtons.length; i++) modeButtons[i].setSelected(i == currentMode);

        switch (currentMode) {
            case MODE_ECO:
                textCurrentMode.setText("Eco Mode");
                textModeHeading.setText("Eco Mode");
                textModeDesc.setText("Max battery. Minimal assist.");
                powerSlider.setValue(25f);
                break;
            case MODE_TRANS:
                textCurrentMode.setText("Transparent");
                textModeHeading.setText("Transparent");
                textModeDesc.setText("Compensates suit weight only.");
                powerSlider.setValue(40f);
                break;
            case MODE_HYPER:
                textCurrentMode.setText("Hyper Mode");
                textModeHeading.setText("Hyper Mode");
                textModeDesc.setText("Maximum power output.");
                powerSlider.setValue(85f);
                break;
            case MODE_FITNESS:
                textCurrentMode.setText("Fitness");
                textModeHeading.setText("Fitness Mode");
                textModeDesc.setText("Adds resistance for training.");
                powerSlider.setValue(10f);
                break;
        }
        textPowerPercent.setText(String.format(Locale.US, "%.0f%%", powerSlider.getValue()));
    }

    @Override
    public void onResume() {
        super.onResume();
        if (Boolean.FALSE.equals(viewModel.isConnected.getValue())) {
            String savedMac = BluetoothPrefs.getLastAddress(requireContext());
            if (savedMac != null) viewModel.connect(savedMac);
        }
    }
}
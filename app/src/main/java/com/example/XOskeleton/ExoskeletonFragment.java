package com.example.XOskeleton;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.slider.Slider;
import com.google.android.material.switchmaterial.SwitchMaterial;

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

    private SwitchMaterial switchSmartAssist;
    private GridLayout gridMotion;
    private TextView textMotionValue;
    private View statusMotionBar;
    private View[] motionButtons;
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
        switchSmartAssist = view.findViewById(R.id.switchSmartAssist);
        gridMotion = view.findViewById(R.id.gridMotion);
        textMotionValue = view.findViewById(R.id.textMotionValue);
        statusMotionBar = view.findViewById(R.id.statusMotionBar);

        // **FIX:** Initialize sheetBehavior after bottomSheet has been found
        if (bottomSheet != null) {
            sheetBehavior = BottomSheetBehavior.from(bottomSheet);
            sheetBehavior.setHideable(true); // Allow it to be hidden completely
            sheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN); // Start Hidden
        }


        // Sheet Internal Views
        textCurrentMode = view.findViewById(R.id.textCurrentMode);
        textModeHeading = view.findViewById(R.id.textModeHeading);
        textModeDesc = view.findViewById(R.id.textModeDesc);
        textPowerPercent = view.findViewById(R.id.textPowerPercent);
        powerSlider = view.findViewById(R.id.powerSlider);

        // Map the included layouts to an array for easy looping
        // (Make sure IDs match what you put in the <include> tags!)
        motionButtons = new View[]{
                view.findViewById(R.id.btnWalk),
                view.findViewById(R.id.btnRaceWalk),
                view.findViewById(R.id.btnUphill),
                view.findViewById(R.id.btnDownhill),
                view.findViewById(R.id.btnStairsUp),
                view.findViewById(R.id.btnStairsDown),
                view.findViewById(R.id.btnGravel),
                view.findViewById(R.id.btnCycling)
        };

        // Initialize Texts/Icons for the grid items manually (since we reused one layout)
        setupGridItem(motionButtons[0], "Walking", R.drawable.ic_walking);
        setupGridItem(motionButtons[1], "Race Walking", R.drawable.ic_running);
        setupGridItem(motionButtons[2], "Uphill", R.drawable.ic_uphill);
        setupGridItem(motionButtons[3], "Downhill", R.drawable.ic_downhill);
        // ... set up the rest ...

        // 2. SWITCH LISTENER
        switchSmartAssist.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updateSmartAssistUI(isChecked);
            // Send command to device
            // viewModel.sendCommand("SMART_ASSIST:" + (isChecked ? "ON" : "OFF"));
        });

        // Initialize state
        updateSmartAssistUI(false);


        modeButtons = new Button[]{
                view.findViewById(R.id.btnEco), view.findViewById(R.id.btnTrans),
                view.findViewById(R.id.btnHyper), view.findViewById(R.id.btnFitness)
        };

        // 2. Setup Bottom Sheet Behavior
        // Removed duplicate initialization, now done above with null check
        // sheetBehavior = BottomSheetBehavior.from(bottomSheet);
        // sheetBehavior.setHideable(true); // Allow it to be hidden completely
        // sheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN); // Start Hidden

        // 3. Connection Observer (The Logic Switch)
        viewModel.isConnected.observe(getViewLifecycleOwner(), connected -> {
            if (connected) {
                // CONNECTED: Hide Buttons, Show Sheet
                layoutDisconnected.setVisibility(View.GONE);
                statusText.setText("Connected - Active");
                statusText.setTextColor(Color.GREEN);

                // Show the sheet (Peek mode)
                if (sheetBehavior != null && sheetBehavior.getState() == BottomSheetBehavior.STATE_HIDDEN) {
                    sheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                }
            } else {
                // DISCONNECTED: Show Buttons, Hide Sheet
                layoutDisconnected.setVisibility(View.VISIBLE);
                statusText.setText("Not Connected");
                statusText.setTextColor(Color.WHITE);

                if (sheetBehavior != null) {
                    sheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
                }
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

        // 3. Connection Observer (THE FIX)
        viewModel.isConnected.observe(getViewLifecycleOwner(), connected -> {
            if (connected) {
                // CONNECTED:
                // 1. Disable Hiding: User cannot swipe it off-screen anymore.
                if (sheetBehavior != null) {
                    sheetBehavior.setHideable(false);

                    // 2. Force it to Peek (Collapsed) or Expanded, but never Hidden.
                    if (sheetBehavior.getState() == BottomSheetBehavior.STATE_HIDDEN) {
                        sheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                    }
                }


                // 3. Update UI
                layoutDisconnected.setVisibility(View.GONE);
                statusText.setText("Connected - Active");
                statusText.setTextColor(Color.GREEN);

            } else {
                // DISCONNECTED:
                // 1. Enable Hiding: So we can hide it programmatically.
                if (sheetBehavior != null) {
                    sheetBehavior.setHideable(true);

                    // 2. Hide it completely.
                    sheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
                }

                // 3. Update UI
                layoutDisconnected.setVisibility(View.VISIBLE);
                statusText.setText("Not Connected");
                statusText.setTextColor(Color.WHITE);
            }
        });

        btnChangeDevice.setOnClickListener(v -> startActivity(new Intent(requireContext(), ScanActivity.class)));

        btnReload.setOnClickListener(v -> {
            String savedMac = BluetoothPrefs.getLastAddress(requireContext());
            if (savedMac != null) viewModel.connect(savedMac);
        });
    }

    private void setupModeButtons() {
        if (modeButtons != null) {
            for (int i = 0; i < modeButtons.length; i++) {
                final int modeIndex = i;
                modeButtons[i].setOnClickListener(v -> {
                    currentMode = modeIndex;
                    updateModeUI();
                    viewModel.sendCommand("SET_MODE:" + currentMode);
                });
            }
        }
    }

    private void updateModeUI() {
        if (modeButtons != null) {
            for (int i = 0; i < modeButtons.length; i++) modeButtons[i].setSelected(i == currentMode);
        }

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

    private void setupGridItem(View view, String title, int iconRes) {
        if(view == null) return;
        TextView tv = view.findViewById(R.id.itemText);
        ImageView iv = view.findViewById(R.id.itemIcon);
        tv.setText(title);
        // iv.setImageResource(iconRes); // Uncomment when you have icons

        // Click Listener for Manual Selection
        view.setOnClickListener(v -> {
            if (!switchSmartAssist.isChecked()) {
                // Highlight this item, dim others
                for(View btn : motionButtons) btn.setAlpha(0.5f);
                view.setAlpha(1.0f);

                // Update text
                textMotionValue.setText(title);
            }
        });
    }

    private void updateSmartAssistUI(boolean isSmartAssistOn) {
        if (isSmartAssistOn) {
            // MODE: AI AUTOMATIC
            // Disable Grid
            for (View btn : motionButtons) {
                if(btn != null) {
                    btn.setEnabled(false);
                    btn.setAlpha(0.3f); // Dim heavily
                }
            }

            // Update Status Bar
            textMotionValue.setText("AI Detecting...");
            textMotionValue.setTextColor(Color.GREEN);

        } else {
            // MODE: MANUAL SELECTION
            // Enable Grid
            for (View btn : motionButtons) {
                if(btn != null) {
                    btn.setEnabled(true);
                    btn.setAlpha(1.0f); // Full brightness
                }
            }

            textMotionValue.setText("Select Mode Below");
            textMotionValue.setTextColor(Color.WHITE);
        }
    }
}

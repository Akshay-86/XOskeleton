package com.example.XOskeleton;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.PopupMenu;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public class DevFragment extends Fragment {

    private ExoViewModel viewModel;
    private DevPropertyAdapter adapter;
    private LineChart chart;
    private Spinner spinnerMotor, spinnerFile;
    private View layoutFileSelector;
    private ArrayAdapter<String> motorSpinnerAdapter;
    private ArrayAdapter<String> fileSpinnerAdapter;

    // UI State
    private boolean isLive = true;
    private String selectedMotorKey = null;
    private String selectedFileName = null; // For Offline Mode

    // Data Lists
    private final List<String> availableMotors = new ArrayList<>();
    private final List<String> availableFiles = new ArrayList<>();

    // Offline Data Maps (Motor Name -> List of Properties)
    private final List<String> offlineProperties = new ArrayList<>();

    // Plotting State
    private final List<String> activeLeftPlots = new ArrayList<>();
    private final List<String> activeRightPlots = new ArrayList<>();

    // TIME LOGIC
    private long startTime = 0;
    private float currentX = 0f;

    private final int[] COLORS = {
            Color.parseColor("#F44336"), Color.parseColor("#2196F3"),
            Color.parseColor("#4CAF50"), Color.parseColor("#FFC107"),
            Color.parseColor("#9C27B0")
    };
    private int colorIndex = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_dev, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(ExoViewModel.class);
        chart = view.findViewById(R.id.devChart);
        setupChart();

        RecyclerView recycler = view.findViewById(R.id.recyclerProperties);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));

        // --- ADAPTER ---
        adapter = new DevPropertyAdapter((view1, propertyName, isCurrentlyPlotted) -> {
            if (isCurrentlyPlotted) {
                removePlot(propertyName);
            } else {
                showPlotMenu(view1, propertyName);
            }
        });
        recycler.setAdapter(adapter);

        // --- SPINNER: MOTOR ---
        spinnerMotor = view.findViewById(R.id.spinnerMotor);
        motorSpinnerAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, availableMotors);
        motorSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerMotor.setAdapter(motorSpinnerAdapter);
        spinnerMotor.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < availableMotors.size()) {
                    String newKey = availableMotors.get(position);
                    if (!newKey.equals(selectedMotorKey)) {
                        selectedMotorKey = newKey;
                        if (!isLive) updateOfflinePropertyList(); // Update list if offline
                        else clearChart();
                    }
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        // --- SPINNER: FILE (Offline) ---
        layoutFileSelector = view.findViewById(R.id.layoutFileSelector);
        spinnerFile = view.findViewById(R.id.spinnerFile);
        fileSpinnerAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, availableFiles);
        fileSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFile.setAdapter(fileSpinnerAdapter);
        spinnerFile.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < availableFiles.size()) {
                    selectedFileName = availableFiles.get(position);
                    loadOfflineFileHeaders(selectedFileName);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        // --- TOGGLE BUTTON ---
        ToggleButton toggle = view.findViewById(R.id.toggleMode);
        toggle.setChecked(true); // Default Live
        toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isLive = isChecked;
            layoutFileSelector.setVisibility(isLive ? View.GONE : View.VISIBLE);

            // RESET EVERYTHING ON TOGGLE
            clearChart();
            availableMotors.clear();
            motorSpinnerAdapter.notifyDataSetChanged();
            adapter.updateData(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());

            if (!isLive) {
                // LOAD FILES
                availableFiles.clear();
                availableFiles.addAll(viewModel.getLogFiles());
                fileSpinnerAdapter.notifyDataSetChanged();
            }
        });

        viewModel.liveDataPacket.observe(getViewLifecycleOwner(), this::processLivePacket);
    }

    // ==========================================
    //              LIVE LOGIC
    // ==========================================
    private void processLivePacket(JSONObject json) {
        if (!isLive) return; // Ignore if offline

        // Time Calc
        long now = System.currentTimeMillis();
        if (startTime == 0) startTime = now;
        currentX = (now - startTime) / 1000f;

        // 1. Discovery
        boolean listChanged = false;
        Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (json.optJSONObject(key) != null && !availableMotors.contains(key)) {
                availableMotors.add(key);
                listChanged = true;
            }
        }
        if (listChanged) {
            Collections.sort(availableMotors);
            motorSpinnerAdapter.notifyDataSetChanged();
            if (selectedMotorKey == null && !availableMotors.isEmpty()) {
                spinnerMotor.setSelection(0);
                selectedMotorKey = availableMotors.get(0);
            }
        }

        // 2. Plotting & List
        if (selectedMotorKey != null) {
            JSONObject motorData = json.optJSONObject(selectedMotorKey);
            if (motorData != null) {
                List<DevPropertyAdapter.PropertyItem> props = new ArrayList<>();
                Iterator<String> propKeys = motorData.keys();
                while (propKeys.hasNext()) {
                    String key = propKeys.next();
                    String val = String.valueOf(motorData.opt(key));
                    props.add(new DevPropertyAdapter.PropertyItem(key, val));

                    double doubleVal = motorData.optDouble(key, Double.NaN);
                    if (!Double.isNaN(doubleVal)) {
                        updateLiveChart(key, doubleVal);
                    }
                }
                Collections.sort(props, (p1, p2) -> p1.name.compareTo(p2.name));
                adapter.updateData(props, activeLeftPlots, activeRightPlots);
            }
        }
    }

    private void updateLiveChart(String propertyName, double value) {
        boolean isLeft = activeLeftPlots.contains(propertyName);
        boolean isRight = activeRightPlots.contains(propertyName);
        if (!isLeft && !isRight) return;

        LineData data = chart.getData();
        if (data == null) { data = new LineData(); chart.setData(data); }

        ILineDataSet set = data.getDataSetByLabel(propertyName, false);
        if (set == null) {
            set = createDataSet(propertyName, isLeft ? YAxis.AxisDependency.LEFT : YAxis.AxisDependency.RIGHT);
            data.addDataSet(set);
        }

        // Sync Color
        if (isLeft) chart.getAxisLeft().setTextColor(set.getColor());
        else chart.getAxisRight().setTextColor(set.getColor());

        data.addEntry(new Entry(currentX, (float) value), data.getIndexOfDataSet((LineDataSet) set));
        if (set.getEntryCount() > 100) ((LineDataSet) set).removeFirst();

        data.notifyDataChanged();
        chart.notifyDataSetChanged();
        chart.setVisibleXRangeMaximum(5f);
        chart.moveViewToX(currentX);
    }

    // ==========================================
    //              OFFLINE LOGIC
    // ==========================================

    // 1. Parse Headers from CSV to find "Motors" (e.g. "right.rpm" -> Motor "right")
    private void loadOfflineFileHeaders(String fileName) {
        List<String> headers = viewModel.getCsvHeaders(fileName);
        availableMotors.clear();
        offlineProperties.clear(); // Temporary hold all props

        for (String h : headers) {
            if (h.contains(".")) {
                String[] parts = h.split("\\.");
                String motor = parts[0]; // "right"
                if (!availableMotors.contains(motor)) {
                    availableMotors.add(motor);
                }
            }
            offlineProperties.add(h);
        }
        Collections.sort(availableMotors);
        motorSpinnerAdapter.notifyDataSetChanged();

        // Auto select first motor
        if (!availableMotors.isEmpty()) {
            spinnerMotor.setSelection(0);
            selectedMotorKey = availableMotors.get(0);
            updateOfflinePropertyList();
        }
    }

    // 2. Show Properties for Selected Motor (No Values)
    private void updateOfflinePropertyList() {
        if (selectedMotorKey == null) return;

        List<DevPropertyAdapter.PropertyItem> props = new ArrayList<>();

        // Filter properties that belong to this motor
        for (String fullKey : offlineProperties) {
            if (fullKey.startsWith(selectedMotorKey + ".")) {
                // Extract "rpm" from "right.rpm"
                String shortName = fullKey.substring(selectedMotorKey.length() + 1);
                // Value is empty in offline mode
                props.add(new DevPropertyAdapter.PropertyItem(shortName, ""));
            }
        }
        Collections.sort(props, (p1, p2) -> p1.name.compareTo(p2.name));
        adapter.updateData(props, activeLeftPlots, activeRightPlots);
    }

    // 3. Plot Entire Column from File
    private void plotOfflineColumn(String shortPropertyName, boolean isLeft) {
        if (selectedFileName == null || selectedMotorKey == null) return;

        // Reconstruct full CSV key: "right" + "." + "rpm"
        String fullKey = selectedMotorKey + "." + shortPropertyName;

        // 1. Get Data from ViewModel (Runs on main thread, but fast enough for 10MB)
        List<Entry> entries = viewModel.getColumnData(selectedFileName, fullKey);

        if (entries.isEmpty()) return;

        LineData data = chart.getData();
        if (data == null) { data = new LineData(); chart.setData(data); }

        // 2. Create DataSet with ALL entries
        LineDataSet set = createDataSet(shortPropertyName, isLeft ? YAxis.AxisDependency.LEFT : YAxis.AxisDependency.RIGHT);
        set.setValues(entries); // Set all at once
        set.setDrawCircles(false); // Disable circles for performance on large datasets

        data.addDataSet(set);

        // Sync Color
        if (isLeft) chart.getAxisLeft().setTextColor(set.getColor());
        else chart.getAxisRight().setTextColor(set.getColor());

        // 3. Refresh
        data.notifyDataChanged();
        chart.notifyDataSetChanged();

        // Reset View to fit all data
        chart.fitScreen();
        chart.invalidate();
    }

    // ==========================================
    //              SHARED HELPERS
    // ==========================================

    private LineDataSet createDataSet(String label, YAxis.AxisDependency axis) {
        LineDataSet set = new LineDataSet(null, label);
        set.setAxisDependency(axis);
        int color = COLORS[colorIndex % COLORS.length];
        set.setColor(color);
        set.setCircleColor(color);
        set.setLineWidth(2f);
        set.setCircleRadius(1f);
        set.setDrawValues(false);
        set.setDrawCircles(false);
        colorIndex++;
        return set;
    }

    private void removePlot(String propertyName) {
        activeLeftPlots.remove(propertyName);
        activeRightPlots.remove(propertyName);
        removeDataSet(propertyName);
        // If offline, refresh list to update (-) back to (+)
        if (!isLive) updateOfflinePropertyList();
    }

    private void removeDataSet(String label) {
        if (chart.getData() != null) {
            ILineDataSet set = chart.getData().getDataSetByLabel(label, false);
            if (set != null) {
                chart.getData().removeDataSet(set);
                chart.notifyDataSetChanged();
                chart.invalidate();
            }
        }
    }

    private void showPlotMenu(View view, String propertyName) {
        PopupMenu popup = new PopupMenu(requireContext(), view);
        popup.getMenu().add(0, 1, 0, "Plot Left Axis");
        popup.getMenu().add(0, 2, 0, "Plot Right Axis");

        popup.setOnMenuItemClickListener(item -> {
            boolean isLeft = (item.getItemId() == 1);

            if (isLeft) {
                activeLeftPlots.add(propertyName);
                activeRightPlots.remove(propertyName);
            } else {
                activeRightPlots.add(propertyName);
                activeLeftPlots.remove(propertyName);
            }

            if (isLive) {
                // Live Mode: Just configure empty set, data comes later
                refreshChartConfig(propertyName, isLeft ? YAxis.AxisDependency.LEFT : YAxis.AxisDependency.RIGHT);
            } else {
                // Offline Mode: LOAD DATA NOW
                // Remove old if exists (to avoid duplicates or axis switch issues)
                removeDataSet(propertyName);
                plotOfflineColumn(propertyName, isLeft);
                updateOfflinePropertyList(); // Update adapter UI
            }
            return true;
        });
        popup.show();
    }

    private void refreshChartConfig(String label, YAxis.AxisDependency axis) {
        if (chart.getData() != null) {
            LineDataSet set = (LineDataSet) chart.getData().getDataSetByLabel(label, false);
            if (set != null) {
                set.setAxisDependency(axis);
                // Sync Color
                if (axis == YAxis.AxisDependency.LEFT) chart.getAxisLeft().setTextColor(set.getColor());
                else chart.getAxisRight().setTextColor(set.getColor());

                chart.notifyDataSetChanged();
                chart.invalidate();
            }
        }
    }

    private void setupChart() {
        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.setPinchZoom(true);
        chart.setBackgroundColor(Color.WHITE);

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setValueFormatter(new ValueFormatter() {
            private final SimpleDateFormat mFormat = new SimpleDateFormat("h:mm:ss a", Locale.US);
            @Override
            public String getFormattedValue(float value) {
                // In offline mode, 'value' might just be seconds, or timestamp
                // If it's large timestamp, format it. If small (0-100), maybe format differently?
                // For now, assume it's timestamp-based
                long originalTimestamp = startTime + (long)(value * 1000);
                return mFormat.format(new Date(originalTimestamp));
            }
        });

        YAxis left = chart.getAxisLeft();
        left.setTextColor(Color.DKGRAY);
        YAxis right = chart.getAxisRight();
        right.setEnabled(true);
        right.setTextColor(Color.DKGRAY);
    }

    private void clearChart() {
        activeLeftPlots.clear();
        activeRightPlots.clear();
        colorIndex = 0;
        startTime = 0;
        if (chart.getData() != null) {
            chart.getData().clearValues();
            chart.clear();
        }
        chart.fitScreen(); // Reset zoom
        chart.getAxisLeft().setTextColor(Color.DKGRAY);
        chart.getAxisRight().setTextColor(Color.DKGRAY);
    }
}
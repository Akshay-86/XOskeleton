package com.example.XOskeleton;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.PopupMenu;
import android.widget.Spinner;
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

    // UI State
    private boolean isLive = true;
    private String selectedMotorKey = null;
    private final List<String> availableMotors = new ArrayList<>();

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

        // --- NEW CLICK LOGIC ---
        // The adapter now gives us 'isCurrentlyPlotted' so we know what to do
        adapter = new DevPropertyAdapter((view1, propertyName, isCurrentlyPlotted) -> {
            if (isCurrentlyPlotted) {
                // If currently plotted (Minus button), remove it
                removePlot(propertyName);
            } else {
                // If not plotted (Plus button), show Add Menu
                showPlotMenu(view1, propertyName);
            }
        });
        recycler.setAdapter(adapter);

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
                        clearChart();
                    }
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        layoutFileSelector = view.findViewById(R.id.layoutFileSelector);
        spinnerFile = view.findViewById(R.id.spinnerFile);
        ToggleButton toggle = view.findViewById(R.id.toggleMode);
        toggle.setChecked(true);
        toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isLive = isChecked;
            layoutFileSelector.setVisibility(isLive ? View.GONE : View.VISIBLE);
            clearChart();
        });

        viewModel.liveDataPacket.observe(getViewLifecycleOwner(), this::processLivePacket);
    }

    private void processLivePacket(JSONObject json) {
        if (!isLive) return;

        // 1. TIME CALCULATION
        long now = System.currentTimeMillis();
        if (startTime == 0) startTime = now;
        currentX = (now - startTime) / 1000f;

        // A. Dynamic Discovery
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

        // B. Property Extraction & Plotting
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
                        updateChartData(key, doubleVal);
                    }
                }
                Collections.sort(props, (p1, p2) -> p1.name.compareTo(p2.name));

                // IMPORTANT: Pass current plot state to adapter so it shows +/- correctly
                adapter.updateData(props, activeLeftPlots, activeRightPlots);
            }
        }
    }

    private void updateChartData(String propertyName, double value) {
        boolean isLeft = activeLeftPlots.contains(propertyName);
        boolean isRight = activeRightPlots.contains(propertyName);
        if (!isLeft && !isRight) return;

        LineData data = chart.getData();
        if (data == null) {
            data = new LineData();
            chart.setData(data);
        }

        ILineDataSet set = data.getDataSetByLabel(propertyName, false);
        if (set == null) {
            // REVERSED AXIS LOGIC: Left Btn -> Right Axis
            set = createDataSet(propertyName, isLeft ? YAxis.AxisDependency.RIGHT : YAxis.AxisDependency.LEFT);
            data.addDataSet(set);
        }

        data.addEntry(new Entry(currentX, (float) value), data.getIndexOfDataSet((LineDataSet) set));

        if (set.getEntryCount() > 100) {
            ((LineDataSet) set).removeFirst();
        }

        data.notifyDataChanged();
        chart.notifyDataSetChanged();
        chart.setVisibleXRangeMaximum(5f);
        chart.moveViewToX(currentX);
    }

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
                long originalTimestamp = startTime + (long)(value * 1000);
                return mFormat.format(new Date(originalTimestamp));
            }
        });

        YAxis left = chart.getAxisLeft();
        left.setTextColor(Color.BLUE);
        YAxis right = chart.getAxisRight();
        right.setEnabled(true);
        right.setTextColor(Color.RED);
    }

    // --- NEW HELPER: Remove Plot Immediately ---
    private void removePlot(String propertyName) {
        activeLeftPlots.remove(propertyName);
        activeRightPlots.remove(propertyName);
        removeDataSet(propertyName);
    }

    // --- UPDATED MENU: Only shows "Add" options ---
    private void showPlotMenu(View view, String propertyName) {
        PopupMenu popup = new PopupMenu(requireContext(), view);
        popup.getMenu().add(0, 1, 0, "Plot Left Axis");
        popup.getMenu().add(0, 2, 0, "Plot Right Axis");

        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: // Plot Left
                    if (!activeLeftPlots.contains(propertyName)) {
                        activeLeftPlots.add(propertyName);
                        activeRightPlots.remove(propertyName);
                        // Reversed Axis Logic
                        refreshChartConfig(propertyName, YAxis.AxisDependency.RIGHT);
                    }
                    break;
                case 2: // Plot Right
                    if (!activeRightPlots.contains(propertyName)) {
                        activeRightPlots.add(propertyName);
                        activeLeftPlots.remove(propertyName);
                        // Reversed Axis Logic
                        refreshChartConfig(propertyName, YAxis.AxisDependency.LEFT);
                    }
                    break;
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
                chart.notifyDataSetChanged();
                chart.invalidate();
            }
        }
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

    private void clearChart() {
        activeLeftPlots.clear();
        activeRightPlots.clear();
        colorIndex = 0;
        startTime = 0;
        if (chart.getData() != null) {
            chart.getData().clearValues();
            chart.clear();
        }
    }
}
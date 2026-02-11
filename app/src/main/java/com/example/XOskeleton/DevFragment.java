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

    private boolean isLive = true;
    private String selectedMotorKey = null;
    private String selectedFileName = null;

    private final List<String> availableMotors = new ArrayList<>();
    private final List<String> availableFiles = new ArrayList<>();
    private final List<String> offlineHeaders = new ArrayList<>();

    private final List<DevPropertyAdapter.PropertyItem> currentProps = new ArrayList<>();
    private final List<String> activeLeftPlots = new ArrayList<>();
    private final List<String> activeRightPlots = new ArrayList<>();

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

        adapter = new DevPropertyAdapter((view1, propertyName, isCurrentlyPlotted) -> {
            if (isCurrentlyPlotted) {
                removePlot(propertyName);
            } else {
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
                        wipeScreen();
                        if (!isLive) updateOfflinePropertyList();
                    }
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

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
                    wipeScreen();
                    startTime = viewModel.getFileStartTime(selectedFileName); // Fix 5:30 AM
                    loadOfflineFileHeaders(selectedFileName);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        ToggleButton toggle = view.findViewById(R.id.toggleMode);
        toggle.setChecked(true);
        toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isLive = isChecked;
            layoutFileSelector.setVisibility(isLive ? View.GONE : View.VISIBLE);
            wipeScreen();
            availableMotors.clear();
            motorSpinnerAdapter.notifyDataSetChanged();
            if (!isLive) {
                availableFiles.clear();
                availableFiles.addAll(viewModel.getLogFiles());
                fileSpinnerAdapter.notifyDataSetChanged();
            }
        });

        viewModel.liveDataPacket.observe(getViewLifecycleOwner(), this::processLivePacket);
    }

    private void processLivePacket(JSONObject json) {
        if (!isLive) return;

        long now = System.currentTimeMillis();
        if (startTime == 0) startTime = now;
        currentX = (now - startTime) / 1000f;

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

        if (selectedMotorKey != null) {
            JSONObject motorData = json.optJSONObject(selectedMotorKey);
            if (motorData != null) {
                currentProps.clear();
                Iterator<String> propKeys = motorData.keys();
                while (propKeys.hasNext()) {
                    String key = propKeys.next();
                    String val = String.valueOf(motorData.opt(key));
                    currentProps.add(new DevPropertyAdapter.PropertyItem(key, val));

                    double doubleVal = motorData.optDouble(key, Double.NaN);
                    if (!Double.isNaN(doubleVal)) {
                        updateLiveChart(key, doubleVal);
                    }
                }
                currentProps.sort((p1, p2) -> p1.name.compareTo(p2.name));
                adapter.updateData(currentProps, activeLeftPlots, activeRightPlots);
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

        if (isLeft) chart.getAxisLeft().setTextColor(set.getColor());
        else chart.getAxisRight().setTextColor(set.getColor());

        data.addEntry(new Entry(currentX, (float) value), data.getIndexOfDataSet((LineDataSet) set));
        if (set.getEntryCount() > 100) ((LineDataSet) set).removeFirst();

        data.notifyDataChanged();
        chart.notifyDataSetChanged();

        // --- ZOOM LIMITS (X & Y) ---
        chart.setVisibleXRangeMaximum(5f);
        chart.setVisibleXRangeMinimum(1.0f); // Limit X Zoom
        chart.setVisibleYRangeMinimum(1.0f, YAxis.AxisDependency.LEFT);  // Limit Left Y Zoom
        chart.setVisibleYRangeMinimum(1.0f, YAxis.AxisDependency.RIGHT); // Limit Right Y Zoom

        chart.moveViewToX(currentX);
    }

    // --- OFFLINE LOGIC ---
    private void loadOfflineFileHeaders(String fileName) {
        List<String> headers = viewModel.getCsvHeaders(fileName);
        availableMotors.clear();
        offlineHeaders.clear();
        offlineHeaders.addAll(headers);

        for (String h : headers) {
            int dotIndex = h.indexOf('.');
            if (dotIndex != -1) {
                String motor = h.substring(0, dotIndex);
                if (!availableMotors.contains(motor)) {
                    availableMotors.add(motor);
                }
            }
        }
        Collections.sort(availableMotors);
        motorSpinnerAdapter.notifyDataSetChanged();
        if (!availableMotors.isEmpty()) {
            spinnerMotor.setSelection(0);
            selectedMotorKey = availableMotors.get(0);
            updateOfflinePropertyList();
        }
    }

    private void updateOfflinePropertyList() {
        if (selectedMotorKey == null) return;
        currentProps.clear();
        String prefix = selectedMotorKey + ".";
        for (String fullKey : offlineHeaders) {
            if (fullKey.startsWith(prefix)) {
                String shortName = fullKey.substring(prefix.length());
                currentProps.add(new DevPropertyAdapter.PropertyItem(shortName, ""));
            }
        }
        Collections.sort(currentProps, (p1, p2) -> p1.name.compareTo(p2.name));
        adapter.updateData(currentProps, activeLeftPlots, activeRightPlots);
    }

    private void plotOfflineColumn(String shortPropertyName, boolean isLeft) {
        if (selectedFileName == null || selectedMotorKey == null) return;
        String fullKey = selectedMotorKey + "." + shortPropertyName;

        List<Entry> entries = viewModel.getColumnData(selectedFileName, fullKey);
        if (entries.isEmpty()) return;

        LineData data = chart.getData();
        if (data == null) { data = new LineData(); chart.setData(data); }

        LineDataSet set = createDataSet(shortPropertyName, isLeft ? YAxis.AxisDependency.LEFT : YAxis.AxisDependency.RIGHT);
        set.setValues(entries);
        set.setDrawCircles(false);
        data.addDataSet(set);

        if (isLeft) chart.getAxisLeft().setTextColor(set.getColor());
        else chart.getAxisRight().setTextColor(set.getColor());

        data.notifyDataChanged();
        chart.notifyDataSetChanged();

        // --- ZOOM LIMITS (X & Y) ---
        chart.fitScreen();
        chart.setVisibleXRangeMinimum(0.5f); // Limit X Zoom
        chart.setVisibleYRangeMinimum(0.5f, YAxis.AxisDependency.LEFT);  // Limit Left Y Zoom
        chart.setVisibleYRangeMinimum(0.5f, YAxis.AxisDependency.RIGHT); // Limit Right Y Zoom

        chart.invalidate();
    }

    // --- HELPERS ---
    private void wipeScreen() {
        activeLeftPlots.clear();
        activeRightPlots.clear();
        clearChart();
        currentProps.clear();
        adapter.updateData(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }

    private void removePlot(String propertyName) {
        activeLeftPlots.remove(propertyName);
        activeRightPlots.remove(propertyName);
        removeDataSet(propertyName);
        adapter.updateData(currentProps, activeLeftPlots, activeRightPlots);
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
                refreshChartConfig(propertyName, isLeft ? YAxis.AxisDependency.LEFT : YAxis.AxisDependency.RIGHT);
            } else {
                removeDataSet(propertyName);
                plotOfflineColumn(propertyName, isLeft);
            }
            adapter.updateData(currentProps, activeLeftPlots, activeRightPlots);
            return true;
        });
        popup.show();
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

    private void refreshChartConfig(String label, YAxis.AxisDependency axis) {
        if (chart.getData() != null) {
            LineDataSet set = (LineDataSet) chart.getData().getDataSetByLabel(label, false);
            if (set != null) {
                set.setAxisDependency(axis);
                if (axis == YAxis.AxisDependency.LEFT) chart.getAxisLeft().setTextColor(set.getColor());
                else chart.getAxisRight().setTextColor(set.getColor());
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

    private void setupChart() {
        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.setPinchZoom(true);
        chart.setBackgroundColor(Color.WHITE);

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(0.5f); // Prevent X grid splitting < 1s

        xAxis.setValueFormatter(new ValueFormatter() {
            private final SimpleDateFormat mFormat = new SimpleDateFormat("h:mm:ss a", Locale.US);
            @Override
            public String getFormattedValue(float value) {
                long originalTimestamp = startTime + (long)(value * 1000);
                return mFormat.format(new Date(originalTimestamp));
            }
        });

        YAxis left = chart.getAxisLeft();
        left.setTextColor(Color.DKGRAY);
        left.setGranularity(0.5f); // Prevent Left Y splitting < 1.0 unit

        YAxis right = chart.getAxisRight();
        right.setEnabled(true);
        right.setTextColor(Color.DKGRAY);
        right.setGranularity(0.5f); // Prevent Right Y splitting < 1.0 unit
    }

    private void clearChart() {
        activeLeftPlots.clear();
        activeRightPlots.clear();
        colorIndex = 0;
        // startTime = 0; // Handled by File/Live logic
        if (chart.getData() != null) {
            chart.getData().clearValues();
            chart.clear();
        }
        chart.fitScreen();
        chart.getAxisLeft().setTextColor(Color.DKGRAY);
        chart.getAxisRight().setTextColor(Color.DKGRAY);
    }
}
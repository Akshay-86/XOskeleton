package com.example.XOskeleton;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

public class StatsFragment extends Fragment {

    private LineChart chartVoltage;
    private LineChart chartCurrent;
    private LineChart chartSpeed;
    private ExoViewModel viewModel; // The Shared Data Box
    private float globalX = 0f; // Continually increasing X value

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_stats, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 1. Bind Views
        chartVoltage = view.findViewById(R.id.chartVoltage);
        chartCurrent = view.findViewById(R.id.chartCurrent);
        chartSpeed = view.findViewById(R.id.chartSpeed);

        // 2. Configure Charts
        setupChart(chartVoltage, Color.parseColor("#FF5722")); // Orange
        setupChart(chartCurrent, Color.parseColor("#2196F3")); // Blue
        setupChart(chartSpeed, Color.parseColor("#4CAF50"));   // Green

        // 3. Connect to Data Stream
        viewModel = new ViewModelProvider(requireActivity()).get(ExoViewModel.class);

        // 4. Start Watching for Updates
        viewModel.voltage.observe(getViewLifecycleOwner(), value -> addEntry(chartVoltage, value));
        viewModel.current.observe(getViewLifecycleOwner(), value -> addEntry(chartCurrent, value));
        viewModel.speed.observe(getViewLifecycleOwner(), value -> addEntry(chartSpeed, value));
    }

    // --- HELPER: Pushes a new point to the graph ---
    private void addEntry(LineChart chart, float value) {
        LineData data = chart.getData();
        if (data == null) {
            data = new LineData();
            chart.setData(data);
        }

        LineDataSet set = (LineDataSet) data.getDataSetByIndex(0);
        if (set == null) {
            set = createSet(chart == chartVoltage ? Color.parseColor("#FF5722") :
                    (chart == chartCurrent ? Color.parseColor("#2196F3") : Color.parseColor("#4CAF50")));
            data.addDataSet(set);
        }

        // 1. CALCULATE NEW X VALUE
        float newX = 0;
        if (set.getEntryCount() > 0) {
            // New X = Last Entry's X + 1
            Entry lastEntry = set.getEntryForIndex(set.getEntryCount() - 1);
            newX = lastEntry.getX() + 1;
        }

        // 2. ADD VALUE
        data.addEntry(new Entry(newX, value), 0);
        data.notifyDataChanged();

        // 3. REMOVE OLD
        if (set.getEntryCount() > 50) {
            set.removeFirst();
        }

        // 4. UPDATE VIEW
        chart.notifyDataSetChanged();
        chart.setVisibleXRangeMaximum(50);
        chart.moveViewToX(newX);
    }

    private void setupChart(LineChart chart, int color) {
        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(false);
        chart.setDragEnabled(false);
        chart.setScaleEnabled(false);
        chart.setDrawGridBackground(false);
        chart.setPinchZoom(false);
        chart.setBackgroundColor(Color.WHITE);
        chart.setViewPortOffsets(10, 0, 10, 0);

        // X-Axis
        XAxis xl = chart.getXAxis();
        xl.setDrawGridLines(true);
        xl.setGridColor(Color.parseColor("#E0E0E0"));
        xl.setDrawAxisLine(false);
        xl.setDrawLabels(false);

        // Y-Axis
        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setDrawGridLines(false);
        leftAxis.setDrawAxisLine(false);
        leftAxis.setDrawLabels(false);
        chart.getAxisRight().setEnabled(false);

        // Initialize Empty Data
        chart.setData(new LineData());
    }

    private LineDataSet createSet(int color) {
        LineDataSet set = new LineDataSet(null, "Data");
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        set.setColor(color);
        set.setLineWidth(2f);
        set.setDrawCircles(false);
        set.setDrawValues(false);
        return set;
    }
}
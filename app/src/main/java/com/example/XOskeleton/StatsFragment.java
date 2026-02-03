package com.example.XOskeleton;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

public class StatsFragment extends Fragment {

    private LineChart chart;
    private Thread simulatorThread; // For testing only

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_stats, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        chart = view.findViewById(R.id.lineChart);
        setupChart();
    }

    private void setupChart() {
        // 1. Configure Chart styling
        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.setDrawGridBackground(false);
        chart.setPinchZoom(true);
        chart.setBackgroundColor(Color.WHITE);

        // 2. Setup Empty Data
        LineData data = new LineData();
        data.setValueTextColor(Color.BLACK);
        chart.setData(data);

        // 3. Customize X-Axis (Bottom)
        XAxis xl = chart.getXAxis();
        xl.setTextColor(Color.BLACK);
        xl.setDrawGridLines(false);
        xl.setAvoidFirstLastClipping(true);
        xl.setEnabled(true);

        // 4. Customize Y-Axis (Left)
        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setTextColor(Color.BLACK);
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(Color.LTGRAY);

        // 5. Disable Right Axis
        YAxis rightAxis = chart.getAxisRight();
        rightAxis.setEnabled(false);
    }

    // --- PUBLIC METHOD: Call this from anywhere to add a point ---
    public void addEntry(float value) {
        if (chart == null) return;

        LineData data = chart.getData();
        if (data != null) {
            LineDataSet set = (LineDataSet) data.getDataSetByIndex(0);

            // Create Set if it doesn't exist yet
            if (set == null) {
                set = createSet();
                data.addDataSet(set);
            }

            // Add Entry
            data.addEntry(new Entry(set.getEntryCount(), value), 0);
            data.notifyDataChanged();

            // Refresh Chart
            chart.notifyDataSetChanged();
            chart.setVisibleXRangeMaximum(50); // Show only last 50 points
            chart.moveViewToX(data.getEntryCount()); // Scroll to end
        }
    }

    private LineDataSet createSet() {
        LineDataSet set = new LineDataSet(null, "Voltage Data");
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        set.setColor(Color.parseColor("#0099CC")); // Blue Line
        set.setLineWidth(2f);
        set.setDrawCircles(false); // No dots for smooth line
        set.setFillAlpha(65);
        set.setFillColor(Color.parseColor("#0099CC"));
        set.setHighLightColor(Color.rgb(244, 117, 117));
        set.setValueTextColor(Color.BLACK);
        set.setValueTextSize(9f);
        set.setDrawValues(false); // Hide numbers on the line
        return set;
    }

    // --- REMOVE THIS LATER: Just for testing ---
    @Override
    public void onResume() {
        super.onResume();
        startSimulation();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (simulatorThread != null) simulatorThread.interrupt();
    }

    private void startSimulation() {
        simulatorThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        // Generate random dummy number
                        float val = (float) (Math.random() * 10) + 40;
                        addEntry(val);
                    });
                }
                try {
                    Thread.sleep(100); // 100ms speed
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        simulatorThread.start();
    }
}
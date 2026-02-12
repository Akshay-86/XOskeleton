package com.example.XOskeleton;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.android.material.button.MaterialButtonToggleGroup;

public class StatsFragment extends Fragment {

    private ExoViewModel viewModel;
    private TextView textUptime, textStatus;
    private BarChart historyChart;
    private MaterialButtonToggleGroup toggleHistory;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_stats, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(ExoViewModel.class);

        textUptime = view.findViewById(R.id.textUptime);
        textStatus = view.findViewById(R.id.textStatus);
        historyChart = view.findViewById(R.id.historyChart);
        toggleHistory = view.findViewById(R.id.toggleHistory);

        setupChart();

        // 1. Live Uptime Observer
        viewModel.liveUptime.observe(getViewLifecycleOwner(), time -> textUptime.setText(time));

        // 2. Connection Status Observer (Colors)
        viewModel.isConnected.observe(getViewLifecycleOwner(), connected -> {
            if (connected) {
                textStatus.setText("System Active");
                textStatus.setTextColor(Color.parseColor("#4CAF50")); // Green
            } else {
                textStatus.setText("Disconnected");
                textStatus.setTextColor(Color.parseColor("#F44336")); // Red
            }
        });

        // 3. History Data Observer
        viewModel.historyEntries.observe(getViewLifecycleOwner(), entries -> {
            if (entries == null || entries.isEmpty()) {
                historyChart.clear();
                return;
            }

            BarDataSet set = new BarDataSet(entries, "Active Minutes");
            set.setColor(Color.parseColor("#673AB7")); // Purple
            set.setValueTextColor(Color.BLACK);
            set.setValueTextSize(12f);

            BarData data = new BarData(set);
            data.setBarWidth(0.5f); // Slim bars

            historyChart.setData(data);
            historyChart.animateY(1000); // Animation
            historyChart.invalidate();   // Refresh
        });

        // 4. X-Axis Label Observer
        viewModel.historyLabels.observe(getViewLifecycleOwner(), labels -> {
            if (labels != null) {
                XAxis xAxis = historyChart.getXAxis();
                xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
                xAxis.setLabelCount(labels.size());
            }
        });

        // 5. Toggle Logic (Daily vs Hourly)
        toggleHistory.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.btnDay) viewModel.calculateUsageHistory(0); // Daily
                else viewModel.calculateUsageHistory(1); // Hourly
            }
        });

        // Initial Load
        viewModel.calculateUsageHistory(0);
    }

    private void setupChart() {
        historyChart.getDescription().setEnabled(false);
        historyChart.getLegend().setEnabled(false);
        historyChart.setDrawGridBackground(false);

        // X-Axis
        XAxis xAxis = historyChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);

        // Left Axis
        YAxis left = historyChart.getAxisLeft();
        left.setAxisMinimum(0f); // Start at 0
        left.setDrawGridLines(true);

        // Right Axis (Disable)
        historyChart.getAxisRight().setEnabled(false);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Reload data whenever we come back to this screen
        boolean isDaily = toggleHistory.getCheckedButtonId() == R.id.btnDay;
        viewModel.calculateUsageHistory(isDaily ? 0 : 1);
    }
}
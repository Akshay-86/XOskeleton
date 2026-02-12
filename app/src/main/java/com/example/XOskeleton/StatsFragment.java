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
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter; // Import this
import com.google.android.material.button.MaterialButtonToggleGroup;

import java.util.Locale;

public class StatsFragment extends Fragment {

    private ExoViewModel viewModel;
    private TextView textUptime, textStatus, tvNoData, labelX;
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
        tvNoData = view.findViewById(R.id.tvNoData); // New "No Data" Text
        labelX = view.findViewById(R.id.labelX);     // X Label to update text dynamically

        setupChart();

        // 1. Live Uptime Observer
        viewModel.liveUptime.observe(getViewLifecycleOwner(), time -> textUptime.setText(time));

        // 2. Connection Status Observer
        viewModel.isConnected.observe(getViewLifecycleOwner(), connected -> {
            if (connected) {
                textStatus.setText("System Active");
                textStatus.setTextColor(Color.parseColor("#4CAF50"));
            } else {
                textStatus.setText("Disconnected");
                textStatus.setTextColor(Color.parseColor("#F44336"));
            }
        });

        // 3. History Data Observer
        viewModel.historyEntries.observe(getViewLifecycleOwner(), entries -> {
            if (entries == null || entries.isEmpty()) {
                historyChart.clear();
                tvNoData.setVisibility(View.VISIBLE); // Show "No Data" text
                return;
            }
            tvNoData.setVisibility(View.GONE);

            BarDataSet set = new BarDataSet(entries, "Active Minutes");
            set.setColor(Color.parseColor("#673AB7"));
            set.setValueTextColor(Color.BLACK);
            set.setValueTextSize(12f);

            // Add "m" to the numbers ON TOP of the bars
            set.setValueFormatter(new ValueFormatter() {
                @Override
                public String getFormattedValue(float value) {
                    return String.format(Locale.US, "%.1f m", value);
                }
            });

            BarData data = new BarData(set);
            data.setBarWidth(0.35f);

            historyChart.setData(data);
            historyChart.animateY(1000);
            historyChart.invalidate();
        });

        // 4. X-Axis Label Observer
        viewModel.historyLabels.observe(getViewLifecycleOwner(), labels -> {
            if (labels != null) {
                XAxis xAxis = historyChart.getXAxis();
                xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
                xAxis.setLabelCount(labels.size());
            }
        });

        // 5. Toggle Logic
        toggleHistory.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.btnDay) {
                    labelX.setText("Timeline (Days)"); // Update Label Text
                    viewModel.calculateUsageHistory(0);
                } else {
                    labelX.setText("Timeline (Hours)"); // Update Label Text
                    viewModel.calculateUsageHistory(1);
                }
            }
        });

        // Initial Load
        viewModel.calculateUsageHistory(0);
    }

    private void setupChart() {
        historyChart.getDescription().setEnabled(false);
        historyChart.getLegend().setEnabled(false);
        historyChart.setDrawGridBackground(false);
        // Make space for labels
        historyChart.setExtraOffsets(0, 0, 0, 10);

        // X-Axis
        XAxis xAxis = historyChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);

        // Left Axis (Y-Axis)
        YAxis left = historyChart.getAxisLeft();
        left.setAxisMinimum(0f);
        left.setDrawGridLines(true);

        // ADD UNITS TO Y-AXIS NUMBERS (e.g., "10 m")
        left.setValueFormatter(new ValueFormatter() {
            @Override
            public String getAxisLabel(float value, com.github.mikephil.charting.components.AxisBase axis) {
                return (int) value + " m";
            }
        });

        // Right Axis (Disable)
        historyChart.getAxisRight().setEnabled(false);
    }

    @Override
    public void onResume() {
        super.onResume();
        boolean isDaily = toggleHistory.getCheckedButtonId() == R.id.btnDay;
        viewModel.calculateUsageHistory(isDaily ? 0 : 1);
    }
}
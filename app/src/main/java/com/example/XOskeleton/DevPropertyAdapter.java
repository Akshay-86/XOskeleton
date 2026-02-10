package com.example.XOskeleton;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DevPropertyAdapter extends RecyclerView.Adapter<DevPropertyAdapter.ViewHolder> {

    public static class PropertyItem {
        String name;
        String value;
        public PropertyItem(String n, String v) { name = n; value = v; }
    }

    private final List<PropertyItem> items = new ArrayList<>();
    private final Set<String> activeKeys = new HashSet<>();
    private final OnItemActionListener actionListener;

    public interface OnItemActionListener {
        void onActionClick(View view, String propertyName, boolean isCurrentlyPlotted);
    }

    public DevPropertyAdapter(OnItemActionListener listener) {
        this.actionListener = listener;
    }

    public void updateData(List<PropertyItem> newItems, List<String> plottedLeft, List<String> plottedRight) {
        // 1. CAPTURE OLD STATE
        Set<String> oldActiveKeys = new HashSet<>(activeKeys);

        // 2. UPDATE NEW STATE
        activeKeys.clear();
        if (plottedLeft != null) activeKeys.addAll(plottedLeft);
        if (plottedRight != null) activeKeys.addAll(plottedRight);

        // 3. HANDLE STRUCTURE CHANGE (Different size? Reset list)
        if (items.size() != newItems.size()) {
            items.clear();
            items.addAll(newItems);
            notifyDataSetChanged();
            return;
        }

        // 4. SMART UPDATE (Same size? Update rows)
        for (int i = 0; i < items.size(); i++) {
            PropertyItem oldItem = items.get(i);
            PropertyItem newItem = newItems.get(i);
            String name = newItem.name;

            // Detect Changes
            boolean wasPlotted = oldActiveKeys.contains(name);
            boolean isNowPlotted = activeKeys.contains(name);
            boolean valueChanged = !oldItem.value.equals(newItem.value);
            boolean nameChanged = !oldItem.name.equals(newItem.name); // Check name change too!

            // Sync Data
            oldItem.value = newItem.value;
            oldItem.name = newItem.name; // CRITICAL FIX: Update Name!

            // Refresh Row if ANYTHING changed
            if (valueChanged || nameChanged || wasPlotted != isNowPlotted) {
                notifyItemChanged(i, "UPDATE_CONTENT");
            }
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_dev_property, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        bind(holder, position);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty()) {
            bind(holder, position);
        } else {
            super.onBindViewHolder(holder, position, payloads);
        }
    }

    private void bind(ViewHolder holder, int position) {
        PropertyItem item = items.get(position);
        boolean isPlotted = activeKeys.contains(item.name);

        holder.textKey.setText(item.name);
        holder.textValue.setText(item.value);

        // Logic for Button Appearance
        if (isPlotted) {
            holder.btnAction.setText("-");
            holder.btnAction.setTextColor(Color.RED);
        } else {
            holder.btnAction.setText("+");
            holder.btnAction.setTextColor(Color.BLACK);
        }

        holder.btnAction.setOnClickListener(v -> {
            actionListener.onActionClick(v, item.name, isPlotted);
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textKey, textValue, btnAction;
        ViewHolder(View v) {
            super(v);
            textKey = v.findViewById(R.id.key);
            textValue = v.findViewById(R.id.value);
            btnAction = v.findViewById(R.id.btnAction);
        }
    }
}
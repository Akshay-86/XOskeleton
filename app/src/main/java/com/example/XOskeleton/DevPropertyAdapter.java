package com.example.XOskeleton;

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
    private final Set<String> activeKeys = new HashSet<>(); // Tracks what is currently plotted
    private final OnItemActionListener actionListener;

    public interface OnItemActionListener {
        void onActionClick(View view, String propertyName, boolean isCurrentlyPlotted);
    }

    public DevPropertyAdapter(OnItemActionListener listener) {
        this.actionListener = listener;
    }

    // Updated to accept the list of active plots
    public void updateData(List<PropertyItem> newItems, List<String> plottedLeft, List<String> plottedRight) {
        // Update Active Keys Set for fast lookup
        activeKeys.clear();
        if (plottedLeft != null) activeKeys.addAll(plottedLeft);
        if (plottedRight != null) activeKeys.addAll(plottedRight);

        // Standard List Update Logic
        if (items.size() != newItems.size()) {
            items.clear();
            items.addAll(newItems);
            notifyDataSetChanged();
            return;
        }

        for (int i = 0; i < items.size(); i++) {
            PropertyItem oldItem = items.get(i);
            PropertyItem newItem = newItems.get(i);

            // Check if plot state changed for this item (e.g., user just clicked plot)
            boolean wasPlotted = activeKeys.contains(oldItem.name);
            boolean isPlotted = activeKeys.contains(newItem.name);

            // Check if value changed
            boolean valueChanged = !oldItem.value.equals(newItem.value);

            oldItem.value = newItem.value; // Sync data

            if (valueChanged || wasPlotted != isPlotted) {
                // Refresh row if Text changed OR Plot State changed
                notifyItemChanged(i, "UPDATE_content");
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
            bind(holder, position); // Just re-bind logic, it's cheap
        } else {
            super.onBindViewHolder(holder, position, payloads);
        }
    }

    private void bind(ViewHolder holder, int position) {
        PropertyItem item = items.get(position);
        boolean isPlotted = activeKeys.contains(item.name);

        holder.textKey.setText(item.name);
        holder.textValue.setText(item.value);

        // Toggle Icon based on state
        if (isPlotted) {
            holder.btnAction.setText("-");
            holder.btnAction.setTextColor(0xFFFF0000); // Red for remove
        } else {
            holder.btnAction.setText("+");
            holder.btnAction.setTextColor(0xFF000000); // Black for add
        }

        // Click Listener for the BUTTON only
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
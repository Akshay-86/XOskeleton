package com.example.XOskeleton;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class DevPropertyAdapter extends RecyclerView.Adapter<DevPropertyAdapter.ViewHolder> {

    public static class PropertyItem {
        String name;
        String value;
        public PropertyItem(String n, String v) { name = n; value = v; }
    }

    private final List<PropertyItem> items = new ArrayList<>();
    private final OnItemLongClickListener longClickListener;

    public interface OnItemLongClickListener {
        void onLongClick(View view, String propertyName);
    }

    public DevPropertyAdapter(OnItemLongClickListener listener) {
        this.longClickListener = listener;
    }

    public void updateData(List<PropertyItem> newItems) {
        if (items.size() != newItems.size()) {
            items.clear();
            items.addAll(newItems);
            notifyDataSetChanged();
            return;
        }

        for (int i = 0; i < items.size(); i++) {
            PropertyItem oldItem = items.get(i);
            PropertyItem newItem = newItems.get(i);

            // FIX: Check for difference BEFORE updating the value!
            // Previously we updated first, so they were always "equal"
            if (!oldItem.value.equals(newItem.value)) {
                oldItem.value = newItem.value; // Update local data
                notifyItemChanged(i, "UPDATE_TEXT_ONLY"); // Update UI
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
        PropertyItem item = items.get(position);
        holder.textKey.setText(item.name);
        holder.textValue.setText(item.value);
        holder.itemView.setOnLongClickListener(v -> {
            longClickListener.onLongClick(v, item.name);
            return true;
        });
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty()) {
            for (Object payload : payloads) {
                if (payload.equals("UPDATE_TEXT_ONLY")) {
                    holder.textValue.setText(items.get(position).value);
                }
            }
        } else {
            super.onBindViewHolder(holder, position, payloads);
        }
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textKey, textValue;
        ViewHolder(View v) {
            super(v);
            textKey = v.findViewById(R.id.key);
            textValue = v.findViewById(R.id.value);
        }
    }
}
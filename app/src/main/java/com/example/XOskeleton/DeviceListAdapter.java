package com.example.basicbluetoothapp;

import android.bluetooth.BluetoothDevice;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class DeviceListAdapter extends RecyclerView.Adapter<DeviceListAdapter.ViewHolder> {

    private final List<BluetoothDevice> devices;
    private final OnDeviceClickListener listener; // 1. Add listener variable

    // 2. Define interface
    public interface OnDeviceClickListener {
        void onDeviceClick(BluetoothDevice device);
    }

    // 3. Update constructor to accept listener
    public DeviceListAdapter(List<BluetoothDevice> devices, OnDeviceClickListener listener) {
        this.devices = devices;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_bt_device, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        BluetoothDevice device = devices.get(position);

        try {
            holder.name.setText(device.getName() != null ? device.getName() : "Unknown Device");
        } catch (SecurityException e) {
            holder.name.setText("Permission Error");
        }
        holder.mac.setText(device.getAddress());

        // 4. Set click listener on the ItemView
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDeviceClick(device);
            }
        });
    }

    @Override
    public int getItemCount() {
        return devices.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name, mac;

        ViewHolder(View v) {
            super(v);
            name = v.findViewById(R.id.deviceName);
            mac = v.findViewById(R.id.deviceMac);
        }
    }
}
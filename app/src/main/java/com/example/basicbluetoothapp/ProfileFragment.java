package com.example.basicbluetoothapp;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class ProfileFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // 1. Inflate the layout
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        // 2. Move your logic here.
        // IMPORTANT: Use 'view.findViewById' instead of just 'findViewById'
        //TextView tv = view.findViewById(R.id.text);

        return view;
    }
}
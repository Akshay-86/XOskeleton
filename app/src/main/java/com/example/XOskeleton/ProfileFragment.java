package com.example.XOskeleton;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class ProfileFragment extends Fragment {

    // --- MISSING METHOD ADDED HERE ---
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // This line tells Android to load "fragment_profile.xml"
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Now this will work because the view exists
        setText(view, R.id.row_manage_account, "Manage Account");
        setText(view, R.id.row_security, "Account Security");
        setText(view, R.id.row_dark_mode, "Dark Mode");
        setText(view, R.id.row_my_device, "My Device");

        setText(view, R.id.row_help, "Product Help");
        setText(view, R.id.row_privacy, "Privacy Policy");
        setText(view, R.id.row_about, "About us");
    }

    private void setText(View parent, int rowId, String text) {
        View row = parent.findViewById(rowId);
        // Safety check to prevent crashing if ID is wrong
        if (row != null) {
            TextView tv = row.findViewById(R.id.row_text);
            if (tv != null) {
                tv.setText(text);
            }

            // Optional: Click Listener
            row.setOnClickListener(v -> {
                // TODO: Handle click
            });
        }
    }
}
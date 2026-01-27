package com.example.basicbluetoothapp;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.Iterator;

public class JsonUiRenderer {

    public static void render(Context ctx, JSONObject json, LinearLayout parent) {
        // Clear old data so we don't duplicate items
        parent.removeAllViews();

        Iterator<String> keys = json.keys();

        while (keys.hasNext()) {
            String key = keys.next();
            Object value = json.opt(key);

            if (value instanceof JSONObject) {
                // If it's a nested object, use item_section.xml
                addSection(ctx, key, (JSONObject) value, parent);
            } else {
                // If it's a value, use item_key_value.xml
                addKeyValue(ctx, key, String.valueOf(value), parent);
            }
        }
    }

    private static void addKeyValue(Context ctx, String key, String value, LinearLayout parent) {
        // Inflate your specific XML file
        View v = LayoutInflater.from(ctx)
                .inflate(R.layout.item_key_value, parent, false);

        // Bind data to the IDs in your XML
        TextView tKey = v.findViewById(R.id.key);
        TextView tValue = v.findViewById(R.id.value);

        if (tKey != null) tKey.setText(key);
        if (tValue != null) tValue.setText(value);

        parent.addView(v);
    }

    private static void addSection(Context ctx, String title, JSONObject obj, LinearLayout parent) {
        // Inflate your specific XML file
        View v = LayoutInflater.from(ctx)
                .inflate(R.layout.item_section, parent, false);

        TextView tTitle = v.findViewById(R.id.sectionTitle);
        LinearLayout sectionContainer = v.findViewById(R.id.sectionContainer);

        if (tTitle != null) tTitle.setText(title);

        // Recursively render the inner JSON into the section's container
        if (sectionContainer != null) {
            render(ctx, obj, sectionContainer);
        }

        parent.addView(v);
    }
}
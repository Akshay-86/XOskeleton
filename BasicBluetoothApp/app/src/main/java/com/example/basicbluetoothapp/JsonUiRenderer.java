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
        parent.removeAllViews();

        Iterator<String> keys = json.keys();

        while (keys.hasNext()) {
            String key = keys.next();
            Object value = json.opt(key);

            if (value instanceof JSONObject) {
                addSection(ctx, key, (JSONObject) value, parent);
            } else {
                addKeyValue(ctx, key, String.valueOf(value), parent);
            }
        }
    }

    private static void addKeyValue(Context ctx, String key, String value, LinearLayout parent) {
        View v = LayoutInflater.from(ctx)
                .inflate(R.layout.item_key_value, parent, false);

        ((TextView) v.findViewById(R.id.key)).setText(key);
        ((TextView) v.findViewById(R.id.value)).setText(value);

        parent.addView(v);
    }

    private static void addSection(Context ctx, String title, JSONObject obj, LinearLayout parent) {
        View v = LayoutInflater.from(ctx)
                .inflate(R.layout.item_section, parent, false);

        TextView sectionTitle = v.findViewById(R.id.sectionTitle);
        LinearLayout sectionContainer = v.findViewById(R.id.sectionContainer);

        sectionTitle.setText(title);

        render(ctx, obj, sectionContainer);
        parent.addView(v);
    }
}

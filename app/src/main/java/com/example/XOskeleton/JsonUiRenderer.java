package com.example.XOskeleton;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.Iterator;

public class JsonUiRenderer {

    public static void render(Context ctx, JSONObject json, LinearLayout parent) {

        // --- STEP 1: CLEANUP (Garbage Collection) ---
        // Remove views that are no longer present in the JSON
        // We iterate backwards to safely remove items
        for (int i = parent.getChildCount() - 1; i >= 0; i--) {
            View child = parent.getChildAt(i);
            Object tag = child.getTag();
            // If the view has a tag (key) but that key is NOT in the new JSON, delete it
            if (tag instanceof String && !json.has((String) tag)) {
                parent.removeViewAt(i);
            }
        }

        // --- STEP 2: UPDATE or CREATE ---
        Iterator<String> keys = json.keys();

        while (keys.hasNext()) {
            String key = keys.next();
            Object value = json.opt(key);

            // Try to find the existing view for this key in this specific container
            // findViewWithTag searches the hierarchy, which is perfect for this
            View existingView = parent.findViewWithTag(key);

            if (value instanceof JSONObject) {
                // --- CASE A: SECTION (Nested Object) ---
                if (existingView == null) {
                    // Create New Section
                    existingView = addSection(ctx, key, parent);
                    existingView.setTag(key); // CRITICAL: Tag it so we find it next time
                }

                // RECURSION: Update the container INSIDE this section
                // IMPORTANT: Your item_section.xml MUST have a LinearLayout with id 'section_container'
                // If it doesn't, add one to the XML, or use the root view if appropriate.
                LinearLayout sectionContainer = existingView.findViewById(R.id.section_container);
                if (sectionContainer != null) {
                    render(ctx, (JSONObject) value, sectionContainer);
                }

            } else {
                // --- CASE B: KEY-VALUE PAIR ---
                String stringValue = String.valueOf(value);

                if (existingView == null) {
                    // Create New Row
                    addKeyValue(ctx, key, stringValue, parent);
                } else {
                    // Update Existing Row (NO FLICKER)
                    updateKeyValue(existingView, stringValue);
                }
            }
        }
    }

    private static void addKeyValue(Context ctx, String key, String value, LinearLayout parent) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_key_value, parent, false);

        // CRITICAL: Set the tag so 'render' can find this view later
        v.setTag(key);

        TextView tKey = v.findViewById(R.id.key);
        TextView tValue = v.findViewById(R.id.value);

        if (tKey != null) tKey.setText(key);
        if (tValue != null) tValue.setText(value);

        parent.addView(v);
    }

    private static void updateKeyValue(View v, String newValue) {
        TextView tValue = v.findViewById(R.id.value);
        if (tValue != null) {
            // Optimization: Only setText if the value actually changed
            // This prevents the Android text layout engine from running unnecessarily
            if (!tValue.getText().toString().equals(newValue)) {
                tValue.setText(newValue);
            }
        }
    }

    private static View addSection(Context ctx, String title, LinearLayout parent) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_section, parent, false);

        // Setup Title (assuming you have an ID like section_title)
        TextView tTitle = v.findViewById(R.id.section_title);
        if (tTitle != null) tTitle.setText(title);

        parent.addView(v);
        return v;
    }
}
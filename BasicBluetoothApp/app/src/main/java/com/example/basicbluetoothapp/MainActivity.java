package com.example.basicbluetoothapp;

import android.os.Bundle;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {

    LinearLayout jsonContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        jsonContainer = findViewById(R.id.jsonContainer);

        new Thread(new BluetoothServer(json -> {
            runOnUiThread(() -> {
                try {
                    JSONObject obj = new JSONObject(json);
                    JsonUiRenderer.render(this, obj, jsonContainer);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        })).start();
    }
}

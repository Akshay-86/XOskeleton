package com.example.basicbluetoothapp;

import android.content.Context;
import android.content.SharedPreferences;

public class BluetoothPrefs {
    private static final String PREF_NAME = "BluetoothPrefs";
    private static final String KEY_DEVICE_ADDRESS = "last_device_address";
    private static final String KEY_DEVICE_NAME = "last_device_name";

    public static void saveDevice(Context context, String name, String address) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_DEVICE_NAME, name)
                .putString(KEY_DEVICE_ADDRESS, address)
                .apply();
    }

    public static String getLastAddress(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString(KEY_DEVICE_ADDRESS, null);
    }

    public static String getLastName(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString(KEY_DEVICE_NAME, "Unknown Device");
    }

    public static void clearDevice(Context context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit().clear().apply();
    }
}
package com.example.basicbluetoothapp;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.UUID;

public class BluetoothServer implements Runnable {

    public interface OnJsonReceived {
        void onReceive(String json);
    }

    private final OnJsonReceived callback;

    public BluetoothServer(OnJsonReceived callback) {
        this.callback = callback;
    }

    private static final UUID UUID_SPP =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    @Override
    public void run() {
        try {
            BluetoothServerSocket serverSocket =
                    BluetoothAdapter.getDefaultAdapter()
                            .listenUsingRfcommWithServiceRecord("RFCOMM_Server", UUID_SPP);

            BluetoothSocket socket = serverSocket.accept();

            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(socket.getInputStream()));

            String line;
            while ((line = reader.readLine()) != null) {
                callback.onReceive(line);
            }

        } catch (Exception e) {
            Log.e("BT_SERVER", "Error", e);
        }
    }
}

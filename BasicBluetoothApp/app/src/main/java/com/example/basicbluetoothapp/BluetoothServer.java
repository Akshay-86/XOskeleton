package com.example.basicbluetoothapp;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.util.Log;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class BluetoothServer implements Runnable {

    private static final String TAG = "BT_SERVER";
    private static final UUID UUID_SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    public interface OnJsonReceived {
        void onReceive(String json);
    }

    private final OnJsonReceived callback;
    private final AtomicBoolean isRunning = new AtomicBoolean(true);
    private BluetoothServerSocket serverSocket = null;
    private BluetoothSocket socket = null;

    public BluetoothServer(OnJsonReceived callback) {
        this.callback = callback;
    }

    @Override
    public void run() {
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null) {
                Log.e(TAG, "Bluetooth Adapter not found");
                return;
            }

            serverSocket = adapter.listenUsingRfcommWithServiceRecord("RFCOMM_Server", UUID_SPP);
            Log.d(TAG, "Server started. Waiting for connection...");

            while (isRunning.get()) {
                try {
                    // This blocks until a connection is made or serverSocket is closed
                    socket = serverSocket.accept();
                    Log.d(TAG, "Device connected!");

                    BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                    String line;
                    while (isRunning.get() && (line = reader.readLine()) != null) {
                        Log.d(TAG, "Received: " + line);
                        callback.onReceive(line);
                    }
                } catch (IOException e) {
                    if (isRunning.get()) {
                        Log.e(TAG, "Error during connection or data reception", e);
                    } else {
                        Log.d(TAG, "Server socket closed intentionally.");
                    }
                } finally {
                    closeSocket();
                }
            }

        } catch (SecurityException e) {
            Log.e(TAG, "Permission missing for Bluetooth server", e);
        } catch (IOException e) {
            if (isRunning.get()) {
                Log.e(TAG, "Server error", e);
            }
        } finally {
            stopServer();
        }
    }

    private void closeSocket() {
        try {
            if (socket != null) {
                socket.close();
                socket = null;
            }
        } catch (IOException e) {
            Log.e(TAG, "Error closing client socket", e);
        }
    }

    public void stopServer() {
        isRunning.set(false);
        try {
            if (serverSocket != null) {
                serverSocket.close();
                serverSocket = null;
            }
        } catch (IOException e) {
            Log.e(TAG, "Error closing server socket", e);
        }
        closeSocket();
    }
}

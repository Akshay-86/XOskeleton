package com.example.XOskeleton;

import android.content.Context;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DataLogger {
    private final Context context;
    private File currentFile;

    public DataLogger(Context context) {
        this.context = context;
        // Don't create file immediately on init, wait for connection
    }

    public void createNewFile() {
        // Create a new file name with timestamp
        String fileName = "Log_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".csv";
        currentFile = new File(context.getExternalFilesDir(null), fileName);
    }

    public void save(String data) {
        if (currentFile == null) return;
        try (FileWriter writer = new FileWriter(currentFile, true)) {
            writer.append(data).append("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public List<String> getAllFiles() {
        File dir = context.getExternalFilesDir(null);
        if (dir == null) return new ArrayList<>();

        String[] files = dir.list((d, name) -> name.endsWith(".csv"));
        if (files != null) {
            List<String> list = Arrays.asList(files);
            // Sort to show newest first
            Collections.sort(list, Collections.reverseOrder());
            return list;
        }
        return new ArrayList<>();
    }

    public List<String[]> readFile(String fileName) {
        List<String[]> data = new ArrayList<>();
        File file = new File(context.getExternalFilesDir(null), fileName);
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file)))) {
            String line;
            while ((line = br.readLine()) != null) {
                // Split by comma, but handle empty values
                data.add(line.split(",", -1));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return data;
    }
}
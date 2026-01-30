package com.example.XOskeleton;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DataLogger {

    private final File logFile;
    long name;
    public DataLogger(Context context) {
        File dir = context.getExternalFilesDir(null);

        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US);
        String fileName = "log_" + formatter.format(new Date()) + ".csv";

        logFile = new File(dir, fileName);
    }

    public void save(String csvLine) {
        synchronized (this) {
            try {
                boolean isNewFile = !logFile.exists();
                FileWriter writer = new FileWriter(logFile, true);

                if (isNewFile) {
                    // ADDED "latency_ms" to the end
                    writer.append("packet_id,timestamp,packet_size,transmission_rate,frequency,relativeLatencyMs,RTT,Latency(ms)RTT\n");
                }

                writer.append(csvLine + "\n");
                writer.flush();
                writer.close();
            } catch (IOException e) {
                Log.e("DataLogger", "Error writing csv", e);
            }
        }
    }
}
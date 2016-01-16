package com.yellowbkpk.gpslogger;

import android.location.Location;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class GeoJsonOutputter implements Outputter {
    private static final String TAG = GeoJsonOutputter.class.getName();
    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    private Executor executor = Executors.newSingleThreadExecutor();

    public GeoJsonOutputter() {
        DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    @Override
    public void write(Location location) {
        this.executor.execute(new AppendGeometryTask(location));
    }

    private class AppendGeometryTask implements Runnable {
        private final Location location;

        public AppendGeometryTask(Location location) {
            this.location = location;
        }

        @Override
        public void run() {
            File rootDir = new File(Environment.getExternalStorageDirectory(), "GPSLogger");
            if(rootDir.mkdirs() || rootDir.isDirectory()) {
                Log.i(TAG, rootDir.getAbsolutePath() + " created");
            } else {
                Log.i(TAG, rootDir.getAbsolutePath() + " wasn't created for some reason");
            }

            try {
                File f = new File(rootDir, DATE_FORMAT.format(new Date()) + ".csv");
                Log.i(TAG, "Using file " + f.getAbsolutePath());
                FileOutputStream fos = new FileOutputStream(f, true);
                OutputStreamWriter osw = new OutputStreamWriter(fos);
                StringBuilder line = new StringBuilder();
                line.append(this.location.getLatitude());
                line.append(",");
                line.append(this.location.getLongitude());
                line.append(",");
                line.append(this.location.getAltitude());
                line.append(",");
                line.append(this.location.getTime());
                line.append("\n");

                Log.i(TAG, "Writing a location to the CSV file: " + f.getAbsolutePath());
                osw.write(line.toString());

                osw.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}

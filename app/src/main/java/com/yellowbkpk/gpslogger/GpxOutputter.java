package com.yellowbkpk.gpslogger;

import android.location.Location;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class GpxOutputter implements Outputter {
    private static final String TAG = GpxOutputter.class.getName();
    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    private static final DateFormat ISO_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    private Executor executor = Executors.newSingleThreadExecutor();
    private File mRootDir;
    private boolean mFirstItemInList = true;

    public GpxOutputter() {
        ISO_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    @Override
    public void writeLocation(Location location) {
        this.executor.execute(new AppendGeometryTask(location));
    }

    @Override
    public void startNewTrace() {
        this.executor.execute(new StartNewTraceTask());
    }

    @Override
    public void endTrace() {
        this.executor.execute(new EndTraceTask());
    }

    private class AppendGeometryTask implements Runnable {
        private final Location location;

        public AppendGeometryTask(Location location) {
            this.location = location;
        }

        @Override
        public void run() {
            ensureDirectoryExists();

            try {
                File f = new File(mRootDir, DATE_FORMAT.format(new Date()) + ".gpx");
                Log.i(TAG, "Using file " + f.getAbsolutePath());

                if(!f.exists()) {
                    FileOutputStream fos = new FileOutputStream(f);
                    OutputStreamWriter osw = new OutputStreamWriter(fos);
                    osw.write("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n");
                    osw.write("<gpx version=\"1.0\" creator=\"GPSLogger\" ");
                    osw.write("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" ");
                    osw.write("xmlns=\"http://www.topografix.com/GPX/1/0\" ");
                    osw.write("xsi:schemaLocation=\"http://www.topografix.com/GPX/1/0 ");
                    osw.write("http://www.topografix.com/GPX/1/0/gpx.xsd\">\n");
                    osw.write("<time>");
                    osw.write(ISO_FORMAT.format(new Date()));
                    osw.write("</time>");
                    osw.write("<trk></trk></gpx>");
                    osw.close();

                    mFirstItemInList = true;
                }

                int fromTheEnd = mFirstItemInList ? 12 : 21;
                long offset = f.length() - fromTheEnd;

                String segment = buildTrackpointString(location);

                RandomAccessFile raf = new RandomAccessFile(f, "rw");
                raf.seek(offset);
                raf.write(segment.getBytes());
                raf.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            mFirstItemInList = false;
        }

        private String buildTrackpointString(Location location) {
            StringBuilder sb = new StringBuilder();

            if(mFirstItemInList) {
                sb.append("<trkseg>");
            }

            sb.append("<trkpt lat=\"");
            sb.append(location.getLatitude());
            sb.append("\" lon=\"");
            sb.append(location.getLongitude());
            sb.append("\">");

            if(location.hasAltitude()) {
                sb.append("<ele>").append(location.getAltitude()).append("</ele>");
            }

            sb.append("<time>");
            Date date = location.getTime() <= 0 ? new Date() : new Date(location.getTime());
            sb.append(ISO_FORMAT.format(date));
            sb.append("</time>");

            if(location.hasBearing()) {
                sb.append("<course>").append(location.getBearing()).append("</course>");
            }

            if(location.hasSpeed()) {
                sb.append("<speed>").append(location.getSpeed()).append("</speed>");
            }

            Bundle extras = location.getExtras();
            if(extras != null) {
                Log.i(TAG, "Location extras: " + extras);
            }

            sb.append("</trkpt>\n");
            sb.append("</trkseg></trk></gpx>");

            return sb.toString();
        }
    }

    private void ensureDirectoryExists() {
        mRootDir = new File(Environment.getExternalStorageDirectory(), "GPSLogger");
        mRootDir.mkdirs();
    }

    private class StartNewTraceTask implements Runnable {
        @Override
        public void run() {
            mFirstItemInList = true;
        }
    }

    private class EndTraceTask implements Runnable {
        @Override
        public void run() {
        }
    }
}

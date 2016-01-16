package com.yellowbkpk.gpslogger;

import android.location.Location;

public interface Outputter {
    void writeLocation(Location location);
    void startNewTrace();
    void endTrace();
}

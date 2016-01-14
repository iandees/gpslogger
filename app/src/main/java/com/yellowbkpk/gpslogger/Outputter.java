package com.yellowbkpk.gpslogger;

import android.location.Location;

public interface Outputter {
    void write(Location location);
}

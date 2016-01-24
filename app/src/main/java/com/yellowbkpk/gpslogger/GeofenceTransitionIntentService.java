package com.yellowbkpk.gpslogger;

import android.app.IntentService;
import android.content.Intent;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class GeofenceTransitionIntentService extends IntentService {

    private static final String TAG = GeofenceTransitionIntentService.class.getName();

    public GeofenceTransitionIntentService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        GeofencingEvent geofencingEvent = GeofencingEvent.fromIntent(intent);
        if (geofencingEvent.hasError()) {
            Log.e(TAG, "Geofencing error: " + geofencingEvent.getErrorCode());
            return;
        }

        SharedPreferences sharedPrefs = getSharedPreferences(LocationService.SHARED_PREFERENCES_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPrefs.edit();

        // Get the transition type.
        int geofenceTransition = geofencingEvent.getGeofenceTransition();

        // Test that the reported transition was of interest.
        if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER ||
                geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) {

            // Get the geofences that were triggered. A single event can trigger multiple geofences.
            List<Geofence> triggeringGeofences = geofencingEvent.getTriggeringGeofences();
            Set<String> fenceNames = new HashSet<>();
            for (Geofence fence : triggeringGeofences) {
                fenceNames.add(fence.getRequestId());
            }

            // Get the transition details as a String.
            String geofenceTransitionDetails = getGeofenceTransitionDetails(
                    this,
                    geofenceTransition,
                    triggeringGeofences
            );

            // Send notification and log the transition details.
            Log.i(TAG, geofenceTransitionDetails);

            if(geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {
                // Entering a geofence, disabling logger
                Set<String> fencesWereInside = sharedPrefs.getStringSet(LocationService.PREFS_FENCES_INSIDE, new HashSet<String>());
                fencesWereInside.addAll(fenceNames);
                editor.putStringSet(LocationService.PREFS_FENCES_INSIDE, fencesWereInside);
                editor.commit();

                Intent loggingService = new Intent(this, LocationService.class);
                loggingService.setAction(LocationService.ACTION_PAUSE);
                startService(loggingService);
            } else if(geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) {
                // Exiting a geofence, enable logger
                Set<String> fencesWereInside = sharedPrefs.getStringSet(LocationService.PREFS_FENCES_INSIDE, new HashSet<String>());
                fencesWereInside.removeAll(fenceNames);
                editor.putStringSet(LocationService.PREFS_FENCES_INSIDE, fencesWereInside);
                editor.commit();

                Intent loggingService = new Intent(this, LocationService.class);
                loggingService.setAction(LocationService.ACTION_PLAY);
                startService(loggingService);
            }
        } else {
            // Log the error.
            Log.e(TAG, "Error during transition " + geofenceTransition);
        }
    }

    private String getGeofenceTransitionDetails(
            Context context,
            int geofenceTransition,
            List<Geofence> triggeringGeofences) {

        String geofenceTransitionString = getTransitionString(geofenceTransition);

        // Get the Ids of each geofence that was triggered.
        ArrayList triggeringGeofencesIdsList = new ArrayList();
        for (Geofence geofence : triggeringGeofences) {
            triggeringGeofencesIdsList.add(geofence.getRequestId());
        }
        String triggeringGeofencesIdsString = TextUtils.join(", ", triggeringGeofencesIdsList);

        return geofenceTransitionString + ": " + triggeringGeofencesIdsString;
    }

    private String getTransitionString(int transitionType) {
        switch (transitionType) {
            case Geofence.GEOFENCE_TRANSITION_ENTER:
                return getString(R.string.geofence_transition_entered);
            case Geofence.GEOFENCE_TRANSITION_EXIT:
                return getString(R.string.geofence_transition_exited);
            default:
                return getString(R.string.unknown_geofence_transition);
        }
    }
}

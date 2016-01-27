package com.yellowbkpk.gpslogger;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LocationService extends Service implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, LocationListener, ResultCallback<Status> {

    private static final String TAG = LocationService.class.getName();
    private static final int ONGOING_NOTIFICATION_ID = 25;
    public static final String SHARED_PREFERENCES_NAME = "gpslogger";

    private static final Map<String, LatLng> FENCES = new HashMap<>();
    private static final float GEOFENCE_RADIUS_IN_METERS = 50f;
    private static final String KEY_EXISTING_FENCES_HASH = "existing fences hash";

    static {
//        FENCES.put("home", new LatLng(37.7689528, -79.4521164));
    }

    public static final String EXTRA_LAT = "LAT";
    public static final String EXTRA_LON = "LON";
    public static final String INTENT_LOCATION = "com.yellowbkpk.location_update";
    public static final String ACTION_PAUSE = "pause";
    public static final String ACTION_STOP = "stop";
    public static final String ACTION_PLAY = "play";
    public static final String ACTION_PAUSE_AND_FENCE = "pause_and_fence";
    public static final String PREFS_FENCES_INSIDE = "inside_fences";

    private LocationRequest mLocationRequest;
    private boolean mCollectingData = false;
    private GoogleApiClient mGoogleApiClient;
    private Outputter mWriter;
    private PendingIntent mGeofencePendingIntent;
    private List<Geofence> mGeofenceList = new ArrayList<>();
    private SharedPreferences mSharedPreferences;
    private ArrayDeque<Float> mPreviousSpeeds = new ArrayDeque<>(15);
    private boolean mFencesChanged = false;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        if (intent == null) {
            return START_STICKY;
        }

        Log.i(TAG, "onStartCommand, intent action=" + intent.getAction() + " extras=" + intent.getExtras());

        String actionPausePlay = intent.getAction();
        if(ACTION_PAUSE.equals(actionPausePlay)) {
            pauseDataCollection();
        } else if(ACTION_PLAY.equals(actionPausePlay)) {
            unpauseDataCollection();
        } else if(ACTION_STOP.equals(actionPausePlay)) {
            stopLocationUpdates();
            stopSelf();
        } else if(ACTION_PAUSE_AND_FENCE.equals(actionPausePlay)) {
            pauseDataCollection();
            double lat = intent.getDoubleExtra(EXTRA_LAT, 0);
            double lon = intent.getDoubleExtra(EXTRA_LON, 0);

            setupTemporaryFenceAround(new LatLng(lat, lon));
        }

        return START_STICKY;
    }

    private void setupTemporaryFenceAround(LatLng location) {
        Log.i(TAG, "Creating temporary fence around " + location);
        FENCES.put("temporary", location);
        populateGeofenceList();

        if (mGoogleApiClient.isConnected()) {
            // To trigger a call to onConnected(), which re-adds the fences
            mGoogleApiClient.reconnect();
        } else {
            mGoogleApiClient.connect();
        }
    }

    private void unpauseDataCollection() {
        startLocationUpdates();
        mWriter.startNewTrace();
        buildNotification();
    }

    private void pauseDataCollection() {
        stopLocationUpdates();
        mWriter.endTrace();
        buildNotification();
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy");
        super.onDestroy();
        stopLocationUpdates();
        stopForeground(true);
    }

    @Override
    public void onCreate() {
        Log.i(TAG, "onCreate");
        super.onCreate();

        mWriter = new GpxOutputter();

        buildGoogleApiClient();
        mGoogleApiClient.connect();
        createLocationRequest();

        mSharedPreferences = getSharedPreferences(SHARED_PREFERENCES_NAME, MODE_PRIVATE);

        buildNotification();
    }

    private void buildNotification() {
        PendingIntent contentPendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), 0);

        Intent pauseIntent = new Intent(this, LocationService.class);
        pauseIntent.setAction(mCollectingData ? ACTION_PAUSE : ACTION_PLAY);
        int pausePlayIcon = mCollectingData ? R.drawable.ic_action_pause : R.drawable.ic_action_play;
        int pausePlayCaption = mCollectingData ? R.string.notif_pause : R.string.notif_play;
        PendingIntent pausePendingIntent = PendingIntent.getService(this, 100, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Intent stopIntent = new Intent(this, LocationService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 101, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        String notificationContentText;
        Set<String> fencesInside = mSharedPreferences.getStringSet(LocationService.PREFS_FENCES_INSIDE, null);

        if (mCollectingData) {
            notificationContentText = getString(R.string.notif_collecting_data);
        } else {
            notificationContentText = getString(R.string.notif_not_collecting);
            if (fencesInside != null) {
                notificationContentText = getString(R.string.notif_in_fence, fencesInside);
            }
        }

        Notification notification = new Notification.Builder(this)
                .setContentTitle(getText(R.string.notification_title))
                .setContentText(notificationContentText)
                .setSmallIcon(R.mipmap.ic_launcher)
                .addAction(pausePlayIcon, getText(pausePlayCaption), pausePendingIntent)
                .addAction(R.drawable.ic_action_stop, getText(R.string.notif_stop), stopPendingIntent)
                .setContentIntent(contentPendingIntent)
                .build();
        startForeground(ONGOING_NOTIFICATION_ID, notification);
    }

    protected void startLocationUpdates() {
        mCollectingData = true;

        if (mLocationRequest != null) {
            createLocationRequest();
        }

        if (mGoogleApiClient.isConnected()) {
            Log.i(TAG, "Requesting location updates");
            LocationServices.FusedLocationApi.requestLocationUpdates(
                    mGoogleApiClient, mLocationRequest, this);
        } else {
            Log.i(TAG, "No client connected. Requesting connection.");
            mGoogleApiClient.connect();
        }
    }

    protected void stopLocationUpdates() {
        Log.i(TAG, "Stopping location updates");
        mCollectingData = false;
        if (mGoogleApiClient.isConnected()) {
            LocationServices.FusedLocationApi.removeLocationUpdates(
                    mGoogleApiClient, this);
        }
    }

    private void createLocationRequest() {
        // Create the LocationRequest object
        mLocationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(1 * 1000)        // 1 second, in milliseconds
                .setFastestInterval(0);
    }

    private void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.i(TAG, "Connected to Google Play Services");

        if (mLocationRequest == null) {
            createLocationRequest();
        }

        if (mCollectingData) {
            Log.i(TAG, "Should be collecting, so requesting location updates");
            LocationServices.FusedLocationApi.requestLocationUpdates(
                    mGoogleApiClient, mLocationRequest, this);
        } else {
            Log.i(TAG, "Should NOT be collecting, so removing location updates");
            LocationServices.FusedLocationApi.removeLocationUpdates(
                    mGoogleApiClient, this);
        }

        if (mFencesChanged) {
            Log.i(TAG, "Adding " + mGeofenceList.size() + " geofences");
            if (mGeofenceList.size() > 0) {
                LocationServices.GeofencingApi.addGeofences(
                        mGoogleApiClient,
                        // The GeofenceRequest object.
                        getGeofencingRequest(),
                        // A pending intent that that is reused when calling removeGeofences(). This
                        // pending intent is used to generate an intent when a matched geofence
                        // transition is observed.
                        getGeofencePendingIntent()
                ).setResultCallback(this); // Result processed in onResult().
            }
        }

    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.w(TAG, "Connection to Google Play Services suspended");
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.e(TAG, "Connection to Google Play Services failed");
    }

    @Override
    public void onLocationChanged(Location location) {
        sendLocationIntent(location);

        mPreviousSpeeds.offerFirst(location.getSpeed());
        float sum = 0;
        for (Float speed : mPreviousSpeeds) {
            sum += speed;
        }
        float average = sum / mPreviousSpeeds.size();
        if (mPreviousSpeeds.size() >= 40) {
            Log.i(TAG, "Weighted average speed is " + average);
            mPreviousSpeeds.removeLast();
            if (average < 1.0f) {
                startService(new Intent(this, LocationService.class)
                        .setAction(ACTION_PAUSE_AND_FENCE)
                        .putExtra(EXTRA_LAT, location.getLatitude())
                        .putExtra(EXTRA_LON, location.getLongitude())
                );
                mPreviousSpeeds.clear();
            }
        }

        writeLocation(location);
    }

    private void sendLocationIntent(Location location) {
        Intent intent = new Intent(LocationService.INTENT_LOCATION);
        intent.putExtra(LocationService.EXTRA_LAT, location.getLatitude());
        intent.putExtra(LocationService.EXTRA_LON, location.getLongitude());
        sendBroadcast(intent);
    }

    private void writeLocation(Location location) {
        if (location.getAccuracy() < 25.0f) {
            // Write the location to file
            mWriter.writeLocation(location);
        } else {
            Log.i(TAG, "Skipping inaccurate location (" + location.getAccuracy() + " meters)");
        }
    }

    private PendingIntent getGeofencePendingIntent() {
        // Reuse the PendingIntent if we already have it.
        if (mGeofencePendingIntent == null) {
            Intent intent = new Intent(this, GeofenceTransitionIntentService.class);
            // We use FLAG_UPDATE_CURRENT so that we get the same pending intent back when
            // calling addGeofences() and removeGeofences().
            mGeofencePendingIntent = PendingIntent.getService(this, 0, intent, PendingIntent.
                    FLAG_UPDATE_CURRENT);
        }

        return mGeofencePendingIntent;
    }

    private GeofencingRequest getGeofencingRequest() {
        GeofencingRequest.Builder builder = new GeofencingRequest.Builder();

        // The INITIAL_TRIGGER_ENTER flag indicates that geofencing service should trigger a
        // GEOFENCE_TRANSITION_ENTER notification when the geofence is added and if the device
        // is already inside that geofence.
        builder.setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER);

        // Add the geofences to be monitored by geofencing service.
        builder.addGeofences(mGeofenceList);

        // Return a GeofencingRequest.
        return builder.build();
    }

    public void populateGeofenceList() {
        mGeofenceList.clear();
        for (Map.Entry<String, LatLng> entry : FENCES.entrySet()) {

            mGeofenceList.add(new Geofence.Builder()
                    // Set the request ID of the geofence. This is a string to identify this
                    // geofence.
                    .setRequestId(entry.getKey())

                            // Set the circular region of this geofence.
                    .setCircularRegion(
                            entry.getValue().latitude,
                            entry.getValue().longitude,
                            GEOFENCE_RADIUS_IN_METERS
                    )

                    .setExpirationDuration(Geofence.NEVER_EXPIRE)

                            // Set the transition types of interest. Alerts are only generated for these
                            // transition. We track entry and exit transitions in this sample.
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER |
                            Geofence.GEOFENCE_TRANSITION_EXIT)

                            // Create the geofence.
                    .build());
        }
        mFencesChanged = true;
    }

    @Override
    public void onResult(@NonNull Status status) {
        if (status.isSuccess()) {
            // Update state and save in shared preferences.
            Log.i(TAG, "Success adding geofences");
            mFencesChanged = false;
        } else {
            // Get the status code for the error and log it using a user-friendly message.
            Log.e(TAG, "Error adding the geofences: " + status.getStatusMessage());
        }
    }
}

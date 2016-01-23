package com.yellowbkpk.gpslogger;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.util.Set;

public class LocationService extends Service implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, LocationListener {

    private static final String TAG = LocationService.class.getName();
    private static final int ONGOING_NOTIFICATION_ID = 25;

    public static final String EXTRA_LAT = "LAT";
    public static final String EXTRA_LON = "LON";
    public static final String INTENT_LOCATION = "com.yellowbkpk.location_update";
    public static final String KEY_PAUSE_PLAY = "pauseplay";
    public static final String ACTION_PAUSE = "pause";
    public static final String ACTION_STOP = "stop";
    public static final String ACTION_PLAY = "play";
    public static final String PREFS_FENCES_INSIDE = "inside_fences";

    private LocationRequest mLocationRequest;
    private boolean mCollectingData = false;
    private GoogleApiClient mGoogleApiClient;
    private Outputter mWriter;
    private SharedPreferences mSharedPreferences;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand, intent action=" + intent.getAction() + " extras=" + intent.getExtras());
        super.onStartCommand(intent, flags, startId);

        String actionPausePlay = intent.getAction();
        if(actionPausePlay == ACTION_PAUSE) {
            pauseDataCollection();
        } else if(actionPausePlay == ACTION_PLAY) {
            unpauseDataCollection();
        } else if(actionPausePlay == ACTION_STOP) {
            stopLocationUpdates();
            stopSelf();
        }

        return START_STICKY;
    }

    private void unpauseDataCollection() {
        mCollectingData = true;
        startLocationUpdates();
        mWriter.startNewTrace();
        buildNotification();
    }

    private void pauseDataCollection() {
        mCollectingData = false;
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

        mSharedPreferences = getSharedPreferences(MainActivity.SHARED_PREFERENCES_NAME, MODE_PRIVATE);

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
        if (mGoogleApiClient.isConnected() && mLocationRequest != null) {
            Log.i(TAG, "Requesting location updates");
            LocationServices.FusedLocationApi.requestLocationUpdates(
                    mGoogleApiClient, mLocationRequest, this);
        } else {
            Log.i(TAG, "No client connected or location request not built yet");
        }
    }

    protected void stopLocationUpdates() {
        Log.i(TAG, "Stopping location updates");
        LocationServices.FusedLocationApi.removeLocationUpdates(
                mGoogleApiClient, this);
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
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.w(TAG, "Connection to Google Play Services suspended");
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.e(TAG, "Connection to Google Play Services failed");
    }

    @Override
    public void onLocationChanged(Location location) {
        Log.i(TAG, "Location changed");
        handleNewLocation(location);
    }

    private void handleNewLocation(Location location) {
        // Send an intent to the UI
        Intent intent = new Intent(LocationService.INTENT_LOCATION);
        intent.putExtra(LocationService.EXTRA_LAT, location.getLatitude());
        intent.putExtra(LocationService.EXTRA_LON, location.getLongitude());
        sendBroadcast(intent);

        // Write the location to file
        mWriter.writeLocation(location);
    }
}

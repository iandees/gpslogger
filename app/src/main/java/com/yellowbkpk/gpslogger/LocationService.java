package com.yellowbkpk.gpslogger;

import android.app.Service;
import android.content.Intent;
import android.content.IntentSender;
import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

public class LocationService extends Service implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, LocationListener {

    private static final String TAG = LocationService.class.getSimpleName();
    private static final int CONNECTION_FAILURE_RESOLUTION_REQUEST = 5000;
    public static final String EXTRA_LAT = "LAT";
    public static final String EXTRA_LON = "LON";
    public static final String INTENT_LOCATION = "com.yellowbkpk.location_update";

    private LocationRequest mLocationRequest;
    private GoogleApiClient mGoogleApiClient;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand");
        super.onStartCommand(intent, flags, startId);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy");
        super.onDestroy();
        stopLocationUpdates();
    }

    @Override
    public void onCreate() {
        Log.i(TAG, "onCreate");
        super.onCreate();

        buildGoogleApiClient();
        mGoogleApiClient.connect();
        createLocationRequest();
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
                .setInterval(10 * 1000)        // 10 seconds, in milliseconds
                .setFastestInterval(1 * 1000); // 1 second, in milliseconds
    }

    private void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.i(TAG, "Connected to Google Play Services");
        startLocationUpdates();
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
        Intent intent = new Intent(LocationService.INTENT_LOCATION);
        intent.putExtra(LocationService.EXTRA_LAT, location.getLatitude());
        intent.putExtra(LocationService.EXTRA_LON, location.getLongitude());
        sendBroadcast(intent);
    }
}

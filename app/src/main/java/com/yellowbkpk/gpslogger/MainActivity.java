package com.yellowbkpk.gpslogger;

import android.app.ActivityManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.SyncStateContract;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private static final String TAG = MainActivity.class.getName();
    private static final int CONNECTION_FAILURE_RESOLUTION_REQUEST = 5000;

    private boolean mRequestingLocationUpdates = false;

    private TextView lblLocation;
    private Button btnStartLocationUpdates;
    private BroadcastReceiver mLocationReceiver;
    private GoogleApiClient mGoogleApiClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        startService(new Intent(this, LocationService.class));

        lblLocation = (TextView) findViewById(R.id.lblLocation);
        btnStartLocationUpdates = (Button) findViewById(R.id.btnLocationUpdates);

        // Toggling the periodic location updates
        btnStartLocationUpdates.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePeriodicLocationUpdates();
            }
        });

        mLocationReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Bundle extras = intent.getExtras();
                Double lat = extras.getDouble(LocationService.EXTRA_LAT);
                Double lon = extras.getDouble(LocationService.EXTRA_LON);
                Log.d(TAG, "Received location intent: " + extras);
                lblLocation.setText(String.format("%.4f, %.4f", lat, lon));
                lblLocation.invalidate();
            }
        };
    }

    private void testForGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        mGoogleApiClient.connect();
        Log.d(TAG, "Checking for Google API Client");
    }

    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private void togglePeriodicLocationUpdates() {
        if (!mRequestingLocationUpdates) {
            mRequestingLocationUpdates = true;

            // Starting the location updates
            Intent intent = new Intent(this, LocationService.class);
            intent.setAction(LocationService.ACTION_PLAY);
            startService(intent);

            Log.d(TAG, "Periodic location updates started!");

        } else {
            mRequestingLocationUpdates = false;

            // Stopping the location updates
            Intent intent = new Intent(this, LocationService.class);
            intent.setAction(LocationService.ACTION_STOP);
            startService(intent);

            Log.d(TAG, "Periodic location updates stopped!");
        }

        updateToggleButtonText();
    }

    private void updateToggleButtonText() {
        if (mRequestingLocationUpdates) {
            btnStartLocationUpdates
                    .setText(getString(R.string.btn_stop_location_updates));
        } else {
            btnStartLocationUpdates
                    .setText(getString(R.string.btn_start_location_updates));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Start listening for locations again
        registerReceiver(mLocationReceiver, new IntentFilter(LocationService.INTENT_LOCATION));

        // Build the Google Play Service location client to test if we can use it
        testForGoogleApiClient();

        // Check if the service is already running
        mRequestingLocationUpdates = isMyServiceRunning(LocationService.class);
        updateToggleButtonText();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mLocationReceiver);
    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.i(TAG, "Connected to Google Play Services");
        btnStartLocationUpdates.setEnabled(true);
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.i(TAG, "Connection to Google Play Services suspended");
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.i(TAG, "Connection to Google Play Services failed");
        /*
         * Google Play services can resolve some errors it detects.
         * If the error has a resolution, try sending an Intent to
         * start a Google Play services activity that can resolve
         * error.
         */
        if (connectionResult.hasResolution()) {
            try {
                // Start an Activity that tries to resolve the error
                connectionResult.startResolutionForResult(this, CONNECTION_FAILURE_RESOLUTION_REQUEST);
                /*
                 * Thrown if Google Play services canceled the original
                 * PendingIntent
                 */
            } catch (IntentSender.SendIntentException e) {
                // Log the error
                e.printStackTrace();
            }
        } else {
            /*
             * If no resolution is available, display a dialog to the
             * user with the error.
             */
            Log.i(TAG, "Location services connection failed with code " + connectionResult.getErrorCode());
        }
    }
}

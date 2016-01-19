package com.yellowbkpk.gpslogger;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.util.Log;

import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;

public class ActivityRecognitionIntentService extends IntentService {

    private static final String TAG = ActivityRecognitionIntentService.class.getName();

    public ActivityRecognitionIntentService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        ActivityRecognitionResult result = ActivityRecognitionResult.extractResult(intent);
        DetectedActivity mostProbableActivity = result.getMostProbableActivity();
        int confidence = mostProbableActivity.getConfidence();
        int type = mostProbableActivity.getType();
        String activityName = getActivityString(this, type);

        SharedPreferences prefs = getSharedPreferences(MainActivity.SHARED_PREFERENCES_NAME, MODE_PRIVATE);
        prefs.edit()
                .putString(LocationService.PREFS_ACTIVITY_NAME, activityName)
                .putInt(LocationService.PREFS_ACTIVITY_CONFIDENCE, confidence)
                .apply();

        if (confidence >= 75) {
            switch (type) {
                case DetectedActivity.STILL: {
                    Intent loggerIntent = new Intent(this, LocationService.class);
                    loggerIntent.setAction(LocationService.ACTION_PAUSE);
                    startService(loggerIntent);
                    break;
                }
                case DetectedActivity.IN_VEHICLE:
                case DetectedActivity.ON_BICYCLE:
                case DetectedActivity.ON_FOOT:
                case DetectedActivity.RUNNING:
                case DetectedActivity.WALKING: {
                    Intent loggerIntent = new Intent(this, LocationService.class);
                    loggerIntent.setAction(LocationService.ACTION_PLAY);
                    startService(loggerIntent);
                    break;
                }
                case DetectedActivity.TILTING:
                case DetectedActivity.UNKNOWN:
                    break;
            }
        }

        Log.i(TAG, "Detected activity " + activityName + ", confidence " + confidence);
    }

    public static String getActivityString(Context context, int detectedActivityType) {
        Resources resources = context.getResources();
        switch(detectedActivityType) {
            case DetectedActivity.IN_VEHICLE:
                return resources.getString(R.string.activity_in_vehicle);
            case DetectedActivity.ON_BICYCLE:
                return resources.getString(R.string.activity_on_bicycle);
            case DetectedActivity.ON_FOOT:
                return resources.getString(R.string.activity_on_foot);
            case DetectedActivity.RUNNING:
                return resources.getString(R.string.activity_running);
            case DetectedActivity.STILL:
                return resources.getString(R.string.activity_still);
            case DetectedActivity.TILTING:
                return resources.getString(R.string.activity_tilting);
            case DetectedActivity.UNKNOWN:
                return resources.getString(R.string.activity_unknown);
            case DetectedActivity.WALKING:
                return resources.getString(R.string.activity_walking);
            default:
                return resources.getString(R.string.activity_unidentifiable_activity, detectedActivityType);
        }
    }
}

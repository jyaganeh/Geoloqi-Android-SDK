package com.geoloqi.android.volta.receiver;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import com.geoloqi.android.sdk.LQTracker.LQTrackerProfile;
import com.geoloqi.android.sdk.receiver.LQBroadcastReceiver;
import com.geoloqi.android.volta.ui.LauncherActivity;

/**
 * <p>This is a sample BroadcastReceiver implementation that
 * extends from {@link LQBroadcastReceiver}. This implementation
 * is designed to highlight how to consume broadcast intents and
 * take action on the messages received.</p>
 * 
 * @author Tristan Waddington
 * @author Josh Yaganeh
 * @author Court Fowler
 */
public class SampleReceiver extends LQBroadcastReceiver {
    public static final String TAG = "Volta.SampleReceiver";
    public static final String PUSH_URI_KEY = "link";
    public static final String START_TEST_URI_SEGMENT = "start_test";
    public static final String STOP_TEST_URI_SEGMENT = "stop_test";
    public static final String TEST_ID_QUERY_PARAM = "id";
    public static final String PROFILE_QUERY_PARAM = "profile";

    @Override
    public void onTrackerProfileChanged(Context context,
                    LQTrackerProfile oldProfile, LQTrackerProfile newProfile) {
        try {
            OnTrackerProfileChangedListener listener = (OnTrackerProfileChangedListener) context;
            listener.onTrackerProfileChanged(oldProfile, newProfile);
        } catch (ClassCastException e) {
            // The broadcast receiver is running with a Context that
            // does not implement OnTrackerProfileChangedListener. If your activity
            // has implemented the interface, then this generally means
            // that the receiver is running in a global context and
            // is not bound to any particular activity.
        }
    }

    @Override
    public void onLocationChanged(Context context, Location location) {
        try {
            OnLocationChangedListener listener = (OnLocationChangedListener) context;
            listener.onLocationChanged(location);
        } catch (ClassCastException e) {
            // The broadcast receiver is running with a Context that
            // does not implement OnLocationChangedListener. If your activity
            // has implemented the interface, then this generally means
            // that the receiver is running in a global context and
            // is not bound to any particular activity.
        }
    }

    @Override
    public void onLocationUploaded(Context context, int count) {
        try {
            OnLocationUploadedListener listener = (OnLocationUploadedListener) context;
            listener.onLocationUploaded(count);
        } catch (ClassCastException e) {
            // The broadcast receiver is running with a Context that
            // does not implement OnLocationUploadedListener. If your activity
            // has implemented the interface, then this generally means
            // that the receiver is running in a global context and
            // is not bound to any particular activity.
        }
    }

    @Override
    public void onPushMessageReceived(Context context, Bundle data) {
        // A new intent to send to the LauncherActivity
        Intent launcherIntent = new Intent(context, LauncherActivity.class);

        // Parsing data out of the link
        final Uri uri = Uri.parse(data.getString(PUSH_URI_KEY));
        final int testId = Integer.parseInt(uri.getQueryParameter(TEST_ID_QUERY_PARAM));

        // Check to see if we are starting or stopping the test
        int startTest = -1;
        final String commandFromUri = uri.getHost();
        if (commandFromUri.equals(START_TEST_URI_SEGMENT)) {
            startTest = 1;
        } else if (commandFromUri.equals(STOP_TEST_URI_SEGMENT)) {
            startTest = 0;
        }

        switch (startTest) {
            case 0:
                launcherIntent.setAction(LauncherActivity.INTENT_STOP_TEST);
                launcherIntent.putExtra(LauncherActivity.EXTRA_TEST_ID, testId);
                break;

            case 1:
                launcherIntent.setAction(LauncherActivity.INTENT_START_TEST);
                launcherIntent.putExtra(LauncherActivity.EXTRA_PROFILE,
                        Integer.parseInt(uri.getQueryParameter(PROFILE_QUERY_PARAM)));
                launcherIntent.putExtra(LauncherActivity.EXTRA_TEST_ID, testId);
                break;

            default:
                Log.w(TAG, "Push Notification received with invalid command: " + commandFromUri);
                break;
        }

        if (LauncherActivity.sIsRunning) {
            try {
                OnPushNotificationReceivedListener listener = (OnPushNotificationReceivedListener) context;
                listener.onPushMessageReceived(launcherIntent);
            } catch (ClassCastException e) {
                // The broadcast receiver is running with a Context that
                // does not implement OnPushNotificationReceivedListener. If your activity
                // has implemented the interface, then this generally means
                // that the receiver is running in a global context and
                // is not bound to any particular activity.
            }
        } else {
            launcherIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(launcherIntent);
        }

        // Dump the message payload to the console
        Log.d(TAG, "Push message payload:");
        for (String key : data.keySet()) {
            Log.d(TAG, String.format("%s: %s", key, data.get(key)));
        }
    }

    public interface OnTrackerProfileChangedListener {
        public void onTrackerProfileChanged(LQTrackerProfile oldProfile,
                        LQTrackerProfile newProfile);
    }

    public interface OnLocationChangedListener {
        public void onLocationChanged(Location location);
    }

    public interface OnLocationUploadedListener {
        public void onLocationUploaded(int count);
    }

    public interface OnPushNotificationReceivedListener {
        public void onPushMessageReceived(Intent command);
    }
}

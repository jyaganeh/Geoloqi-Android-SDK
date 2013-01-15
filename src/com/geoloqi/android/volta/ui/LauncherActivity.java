package com.geoloqi.android.volta.ui;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.*;
import com.geoloqi.android.volta.R;
import com.geoloqi.android.volta.receiver.SampleReceiver;
import com.geoloqi.android.sdk.LQSharedPreferences;
import com.geoloqi.android.sdk.LQTracker;
import com.geoloqi.android.sdk.LQTracker.LQTrackerProfile;
import com.geoloqi.android.sdk.provider.LQDatabaseHelper;
import com.geoloqi.android.sdk.receiver.LQBroadcastReceiver;
import com.geoloqi.android.sdk.service.LQService;
import com.geoloqi.android.sdk.service.LQService.LQBinder;
import com.geoloqi.android.volta.VoltaService;
import com.geoloqi.android.volta.VoltaService.VoltaBinder;

/**
 * <p>This is the main {@link Activity} for the Geoloqi Sample Android
 * app. It starts up and binds to the {@link LQService} tracker. It also
 * registers to receive broadcasts from the tracker using the
 * interfaces defined on the {@link SampleReceiver}.</p>
 * 
 * @author Tristan Waddington
 * @author Josh Yaganeh
 * @author Court Fowler
 */
public class LauncherActivity extends Activity implements SampleReceiver.OnLocationChangedListener,
        SampleReceiver.OnLocationUploadedListener, SampleReceiver.OnPushNotificationReceivedListener {
    public static final String TAG = "Volta.LauncherActivity";
    public static final String INTENT_START_TEST = "volta.ui.LauncherActivity.StartTest";
    public static final String INTENT_STOP_TEST = "volta.ui.LauncherActivity.StopTest";
    public static final String EXTRA_TEST_ID = "volta.ui.LauncherActivity.TestID";
    public static final String EXTRA_PROFILE = "volta.ui.LauncherActivity.Profile";
    public static boolean sIsRunning = false;

    private LQService mLqService;
    private boolean mLqServiceBound;
    private SampleReceiver mLocationReceiver = new SampleReceiver();

    private VoltaService mVoltaService;
    private boolean mVoltaServiceBound;

    private boolean mTestInProgress = false;
    private Intent mPendingIntent = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        // If this activity was created in order to start/stop a test, let's hold onto that
        // intent until we are bound to the VoltaService.
        String action = getIntent() != null ? getIntent().getAction() : null;
        if (!TextUtils.isEmpty(action) &&
                (action.equals(INTENT_START_TEST) || action.equals(INTENT_STOP_TEST))) {
            mPendingIntent = getIntent();
        }
        
        // Start the tracking service
        Intent lqIntent = new Intent(this, LQService.class);
        startService(lqIntent);

        // We will handle push notifications ourselves, so disable handling in the SDK
        LQSharedPreferences.disablePushNotificationHandling(this);

        // Start the Volta service
        Intent vIntent = new Intent(this, VoltaService.class);
        startService(vIntent);
    }

    private void handleIntent(Intent intent) {
        if (intent.getAction().equals(INTENT_START_TEST)) {
            if (intent.hasExtra(EXTRA_TEST_ID) && intent.hasExtra(EXTRA_PROFILE)) {
                final int profile = intent.getIntExtra(EXTRA_PROFILE, 2);
                final int testId = intent.getIntExtra(EXTRA_TEST_ID, -1);
                startTest(testId, profile);
            } else {
                Log.w(TAG, "Start test intent received in LauncherActivity without required params!");
            }
        } else if (intent.getAction().equals(INTENT_STOP_TEST)) {
            if (intent.hasExtra(EXTRA_TEST_ID)) {
                final int testId = intent.getIntExtra(EXTRA_TEST_ID, -1);
                stopTest(testId);
            } else {
                Log.w(TAG, "Stop test intent received in LauncherActivity without required params!");
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        sIsRunning = true;
    }

    @Override
    public void onResume() {
        super.onResume();
        
        // Bind to the tracking service so we can call public methods on it
        Intent lqIntent = new Intent(this, LQService.class);
        bindService(lqIntent, mLQServiceConnection, 0);

        // Bind to the tracking service so we can call public methods on it
        Intent vIntent = new Intent(this, VoltaService.class);
        bindService(vIntent, mVoltaServiceConnection, 0);

        // Wire up the sample location receiver
        registerReceiver(mLocationReceiver,
                LQBroadcastReceiver.getDefaultIntentFilter());
    }

    @Override
    public void onPause() {
        super.onPause();
        
        // Unbind from LQService
        if (mLqServiceBound) {
            unbindService(mLQServiceConnection);
            mLqServiceBound = false;
        }

        // Unbind from VoltaService
        if (mVoltaServiceBound) {
            unbindService(mVoltaServiceConnection);
            mVoltaServiceBound = false;
        }
        
        // Unregister our location receiver
        unregisterReceiver(mLocationReceiver);
    }

    @Override
    public void onStop() {
        super.onStop();
        sIsRunning = false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
        case R.id.settings:
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return false;
    }

    private void startTest(int testId, int profile) {
        if (mVoltaServiceBound) {
            if (!mTestInProgress) {
                // Start
                mVoltaService.startTest(testId, profile);
            }

            mTestInProgress = true;
        }  else {
            Toast.makeText(this, "VoltaService not bound!", Toast.LENGTH_LONG).show();
        }
    }

    private void stopTest(int testId) {
        if (mVoltaServiceBound) {
            if (mTestInProgress && testId == VoltaService.getTestId()) {
                // Stop
                mVoltaService.stopTest();

                mTestInProgress = false;
            } else {
                Toast.makeText(this, "Stop received, but no test in progress OR ids did not match", Toast.LENGTH_LONG);
            }
        }  else {
            Toast.makeText(this, "VoltaService not bound!", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Display the number of batched location fixes waiting to be sent.
     */
    private void showBatchedLocationCount() {
        TextView updates = (TextView) findViewById(R.id.batched_updates);
        if (updates != null) {
            final LQTracker tracker = mLqService.getTracker();
            final LQDatabaseHelper helper = new LQDatabaseHelper(this);
            final SQLiteDatabase db = helper.getWritableDatabase();
            final Cursor c = tracker.getBatchedLocationFixes(db);
            updates.setText(String.format("%d batched updates",
                            c.getCount()));
            c.close();
            db.close();
        }
    }

    /**
     * Display the values from the last recorded location fix.
     * @param location
     */
    private void showCurrentLocation(Location location) {
        TextView latitudeView = (TextView) findViewById(R.id.location_lat);
        if (latitudeView != null) {
            latitudeView.setText(Double.toString(location.getLatitude()));
        }
        
        TextView longitudeView = (TextView) findViewById(R.id.location_long);
        if (longitudeView != null) {
            longitudeView.setText(Double.toString(location.getLongitude()));
        }
        
        TextView accuracyView = (TextView) findViewById(R.id.location_accuracy);
        if (accuracyView != null) {
            accuracyView.setText(String.valueOf(location.getAccuracy()));
        }
        
        TextView speedView = (TextView) findViewById(R.id.location_speed);
        if (speedView != null) {
            speedView.setText(String.format("%.2f km/h", (location.getSpeed() * 3.6)));
        }
        
        TextView providerView = (TextView) findViewById(R.id.location_provider);
        if (providerView != null) {
            providerView.setText(location.getProvider());
        }
    }

    /** Defines callbacks for LQService binding, passed to bindService() */
    private ServiceConnection mLQServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            try {
                LQBinder binder = (LQBinder) service;
                mLqService = binder.getService();
                mLqServiceBound = true;
            } catch (ClassCastException e) {
                // Pass
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mLqServiceBound = false;
        }
    };

    /** Defines callbacks for VoltaService binding, passed to bindService() */
    private ServiceConnection mVoltaServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            try {
                // We've bound to LocalService, cast the IBinder and get LocalService instance.
                VoltaBinder binder = (VoltaBinder) service;
                mVoltaService = binder.getService();
                mVoltaServiceBound = true;
                // We might have an intent waiting to be processed, so let's check and run it if so.
                if (mPendingIntent != null) {
                    handleIntent(mPendingIntent);
                }
            } catch (ClassCastException e) {
                // Pass
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mVoltaServiceBound = false;
        }
    };

    @Override
    public void onLocationChanged(Location location) {
        showBatchedLocationCount();
        showCurrentLocation(location);
    }

    @Override
    public void onLocationUploaded(int count) {
        showBatchedLocationCount();
    }

    @Override
    public void onPushMessageReceived(Intent command) {
        handleIntent(command);
    }
}

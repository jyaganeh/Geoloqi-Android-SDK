package com.geoloqi.android.volta;

import android.app.Service;
import android.content.*;
import android.location.GpsStatus;
import android.location.LocationManager;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;
import com.geoloqi.android.sdk.LQSharedPreferences;
import com.geoloqi.android.sdk.LQTracker;
import com.geoloqi.android.sdk.service.LQService;
import com.geoloqi.android.volta.util.DeviceUuidFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

// TODO: make this into an intent-service
// TODO: export this in the manifest if you run into trouble with receiving intents from the system service
// TODO: stop listeners and receivers in onPause

/**
 * A service for monitoring system state during Geoloqi test runs.
 *
 * Periodic data is gathered every minute, triggered by  {@link Intent ACTION_TIME_TICK}.
 * Event based data is gathered when screen state and cellular reception changes.
 *
 * TODO: submit GpsStatus.getMaxSatellites() to the server out of band
 *
 * @author Josh Yaganeh
 * @author Court Fowler
 */
public class VoltaService extends Service {

    public static final String TAG = "VoltaService";

    private static final String DATA_POINT_SCREEN_STATE = "screen_state";
    private static final String DATA_POINT_WIFI_STATE = "wifi_state";
    private static final String DATA_POINT_GPS_STATE = "gps_state";

    private static final String DATA_POINT_BATTERY_PERCENT = "battery_percent";
    private static final String DATA_POINT_BATTERY_VOLTAGE = "battery_voltage";

    private static final String DATA_POINT_GSM_SIGNAL_STRENGTH = "cell_signal_gsm";
    private static final String DATA_POINT_CDMA_SIGNAL_STRENGTH = "cell_signal_cdma";
    private static final String DATA_POINT_EVDO_SIGNAL_STRENGTH = "cell_signal_evdo";

    private static final String PREF_PREFIX = "com.geoloqi.volta.preference";
    private static final String PREF_IS_DEVICE_REGISTERED = PREF_PREFIX + "IS_DEVICE_REGISTERED";

    private final IBinder mBinder = new VoltaBinder();

    private BroadcastReceiver mReceiver;

    private TelephonyManager mTelephonyManager;
    private VoltaPhoneStateListener mPhoneStateListener;

    private LocationManager mLocationManager;
    private GpsStatus.Listener mGpsStateListener;

    private WifiManager mWifiManager;

    private JSONArray mDataQueue;
    private JSONObject mTest;

    private static int mTestId = -1;
    private UUID mDeviceUuid;

    private SharedPreferences mPreferences;


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public class VoltaBinder extends Binder {
        public VoltaService getService() {
            return VoltaService.this;
        }
    }

    /**
     * A receiver to listen for user created intents from LQService.
     */
    private BroadcastReceiver mUserCreatedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (LQService.ACTION_ANON_USER_CREATED.equals(action)) {
                mPreferences = PreferenceManager.getDefaultSharedPreferences(context);
                boolean registered = mPreferences.getBoolean(PREF_IS_DEVICE_REGISTERED, false);
                if (!registered) {
                    // post device info to server
                    boolean success = VoltaHttpClient.postDeviceInfo(getDeviceInfo());
                    // remember that we've done this so that it doesn't happen again
                    mPreferences.edit().putBoolean(PREF_IS_DEVICE_REGISTERED, success).commit();
                }
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        // Register for user created intents
        // Register our battery receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(LQService.ACTION_ANON_USER_CREATED);
        registerReceiver(mUserCreatedReceiver, filter);

        // Get the unique id from the device
        mDeviceUuid = new DeviceUuidFactory(this).getDeviceUuid();
    }

    @Override
    public void onDestroy() {
        try {
            // Unregister our user-created receiver
            unregisterReceiver(mUserCreatedReceiver);
        } catch (IllegalArgumentException e) {
            // Pass
        }

        super.onDestroy();
    }

    /**
     * Called when starting a test, this method posts an initial set of data to the Volta server and
     * begins logging.
     */
    public void startTest(int testId, int profileId) {
        // Instantiate receivers, listeners, and managers for the various services we'll monitor.
        mReceiver = new VoltaBroadcastReceiver();

        mPhoneStateListener = new VoltaPhoneStateListener();
        mTelephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

        mGpsStateListener = new VoltaGpsStateListener();
        mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);

        mTestId = testId;

        // Reset mTest, mHardwareInfo, and mDataQueue
        mTest = new JSONObject();
        mDataQueue = new JSONArray();

        LQTracker.LQTrackerProfile profile = LQTracker.LQTrackerProfile.values()[profileId];

        Intent setProfileIntent = new Intent(this, LQService.class);
        setProfileIntent.setAction(LQService.ACTION_SET_TRACKER_PROFILE);
        setProfileIntent.putExtra(LQService.EXTRA_PROFILE, profile);

        startService(setProfileIntent);

        try {
            mTest.put("uuid", mDeviceUuid.toString());
            mTest.put("test_id", mTestId);
        } catch (JSONException e) {
            Log.e(TAG, "Could not create test", e);
        }

        // Get initial periodic data
        recordPeriodicDataPoints();

        // Register for system intents
        IntentFilter filter = new IntentFilter(Intent.ACTION_TIME_TICK);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(mReceiver, filter);

        // Attach phone state listener
        mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);

        // Attach gps status listener
        mLocationManager.addGpsStatusListener(mGpsStateListener);

        Log.d(TAG, "The current geoloqi user is: " + LQSharedPreferences.getSessionUsername(this) +
                    " (" + LQSharedPreferences.getSessionUserId(this) + ")");

        try {
            mTest.put("data", mDataQueue);
            VoltaHttpClient.postDataPoints(mTest);
        } catch (Exception e) {
            Log.e(TAG, "Error adding data points to test object.", e);
        }

        mDataQueue = new JSONArray();

        Toast.makeText(this, "Test started.", Toast.LENGTH_SHORT).show();
    }

    /**
     * Called when stopping a test, this method stops logging and posts a final set of data to the
     * Volta server.:
     */
    public void stopTest() {
        // Get final periodic data
        recordPeriodicDataPoints();

        // Unregister for system intents
        unregisterReceiver(mReceiver);
        mReceiver = null;

        // Detach phone state listener
        mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
        mTelephonyManager = null;

        // Detach gps status listener
        mLocationManager.removeGpsStatusListener(mGpsStateListener);
        mLocationManager = null;

        // Null out the rest
        mPhoneStateListener = null;
        mGpsStateListener = null;
        mWifiManager = null;

        try {
            mTest.put("data", null);
            mTest.put("data", mDataQueue);
            VoltaHttpClient.postDataPoints(mTest);
        } catch (Exception e) {
            Log.e(TAG, "Error adding data points to test object.", e);
        }

        Toast.makeText(this, "Test stopped.", Toast.LENGTH_SHORT).show();
    }

    /**
     * Records a data point with the given type and value.
     *
     * TODO: make this actually post to the server.
     *
     * @param type
     * @param value
     */
    public void recordDataPoint(String type, int value) {
        // TODO: use a dedicated timer. this changes when the system time changes.
        Long timestamp = System.currentTimeMillis() / 1000;
        JSONObject dataPoint = new JSONObject();

        try {
            dataPoint.put("timestamp", timestamp);
            dataPoint.put("type", type);
            dataPoint.put("value", value);
        } catch (JSONException e) {
            Log.e(TAG, "error building datapoint", e);
        }

        mDataQueue.put(dataPoint);

        Log.d(TAG, "DATAPOINT: " + type + ", " + value + ", " + timestamp);
    }

    /**
     * Records a data point with the given type and value.
     *
     * TODO: make this actually post to the server.
     *
     * @param type
     * @param value
     */
    public void recordDataPoint(String type, float value) {
        // TODO: use dedicated timer. this changes when the system time changes.
        Long timestamp = System.currentTimeMillis() / 1000;
        JSONObject dataPoint = new JSONObject();

        try {
            dataPoint.put("timestamp", timestamp);
            dataPoint.put("type", type);
            dataPoint.put("value", value);
        } catch (JSONException e) {
            Log.e(TAG, "error building datapoint", e);
        }

        mDataQueue.put(dataPoint);

        Log.d(TAG, "DATAPOINT: " + type + ", " + value + ", " + timestamp);
    }

    public void recordPeriodicDataPoints() {
        recordBatteryState();
        recordWifiState();
    }

    public static int getTestId() {
        return mTestId;
    }

    /**
     * Checks and records the current state of the battery.
     */
    private void recordBatteryState() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, filter);

        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        float percent = (level / (float) scale);

        int voltage = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);

        recordDataPoint(DATA_POINT_BATTERY_PERCENT, percent);
        recordDataPoint(DATA_POINT_BATTERY_VOLTAGE, voltage);
    }

    /**
     * Checks and records the current state of the WiFi radio.
     */
    private void recordWifiState() {
        int wifi = mWifiManager.isWifiEnabled() ? 1 : 0;
        recordDataPoint(DATA_POINT_WIFI_STATE, wifi);
    }

    private JSONObject getDeviceInfo() {
        JSONObject deviceInfo = new JSONObject();
        JSONObject extra = new JSONObject();

        // Populate Hardware Info
        try {
            deviceInfo.put("user_id", LQSharedPreferences.getSessionUserId(this));
            deviceInfo.put("uuid", mDeviceUuid.toString());
            deviceInfo.put("mobile_platform", "android");
            deviceInfo.put("carrier",  ((TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE)).getNetworkOperatorName());
            deviceInfo.put("hardware_model", Build.MANUFACTURER + " " + Build.MODEL);

            extra.put("build", Build.DISPLAY);
            extra.put("version", Build.VERSION.RELEASE);
            extra.put("api_level", Build.VERSION.SDK_INT);

            deviceInfo.put("extra", extra);
        } catch (JSONException e) {
            Log.e(TAG, "Could not get hardware info", e);
        }

        return deviceInfo;
    }

    private class VoltaBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_TIME_TICK)) {
                // Fired by the Android OS every minute
                recordPeriodicDataPoints();
            } else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                recordDataPoint(DATA_POINT_SCREEN_STATE, 1);
            } else if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                recordDataPoint(DATA_POINT_SCREEN_STATE, 0);
            }
        }
    }

    private class VoltaPhoneStateListener extends PhoneStateListener {
        @Override
        public void onSignalStrengthsChanged(SignalStrength signalStrength) {
            recordDataPoint(DATA_POINT_GSM_SIGNAL_STRENGTH,
                    signalStrength.getGsmSignalStrength());
            recordDataPoint(DATA_POINT_CDMA_SIGNAL_STRENGTH,
                    signalStrength.getCdmaDbm());
            recordDataPoint(DATA_POINT_EVDO_SIGNAL_STRENGTH,
                    signalStrength.getEvdoDbm());
        }

    }

    private class VoltaGpsStateListener implements GpsStatus.Listener {
        @Override
        public void onGpsStatusChanged(int i) {
            if (i == GpsStatus.GPS_EVENT_STARTED || i == GpsStatus.GPS_EVENT_STOPPED) {
                recordDataPoint(DATA_POINT_GPS_STATE, i);
            }
        }
    }
}
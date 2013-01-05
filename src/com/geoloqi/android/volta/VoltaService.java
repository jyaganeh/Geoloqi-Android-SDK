package com.geoloqi.android.volta;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.GpsStatus;
import android.location.LocationManager;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;
import com.geoloqi.android.sdk.LQSharedPreferences;
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
 */
public class VoltaService extends Service {

    public static final String TAG = "VoltaService";

    private static final String DATA_POINT_SCREEN_STATE = "screen_state";
    private static final String DATA_POINT_WIFI_STATE = "wifi_state";
    private static final String DATA_POINT_GPS_STATE = "gps_state";

    private static final String DATA_POINT_BATTERY_LEVEL = "battery_level";
    private static final String DATA_POINT_BATTERY_SCALE = "battery_scale";
    private static final String DATA_POINT_BATTERY_PERCENT = "battery_percent";
    private static final String DATA_POINT_BATTERY_VOLTAGE = "battery_voltage";

    private static final String DATA_POINT_GSM_SIGNAL_STRENGTH = "cell_signal_gsm";
    private static final String DATA_POINT_CDMA_SIGNAL_STRENGTH = "cell_signal_cdma";
    private static final String DATA_POINT_EVDO_SIGNAL_STRENGTH = "cell_signal_evdo";

    private final IBinder mBinder = new VoltaBinder();

    private BroadcastReceiver mReceiver;

    private TelephonyManager mTelephonyManager;
    private VoltaPhoneStateListener mPhoneStateListener;

    private LocationManager mLocationManager;
    private GpsStatus.Listener mGpsStateListener;

    private WifiManager mWifiManager;

    private JSONArray mDataQueue;
    private JSONObject mTest;
    private JSONObject mHardwareInfo;
    private String mTestUuid;

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

    @Override
    public void onCreate() {
        super.onCreate();

        // Instantiate receivers, listeners, and managers for the various services we'll monitor.
        mReceiver = new VoltaBroadcastReceiver();

        mPhoneStateListener = new VoltaPhoneStateListener();
        mTelephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

        mGpsStateListener = new VoltaGpsStateListener();
        mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);

        Toast.makeText(this, "VoltaService running!", Toast.LENGTH_SHORT).show();
    }

    /**
     * Called when starting a test, this method posts an initial set of data to the Volta server and
     * begins logging.
     */
    public void startTest() {
        createTest();

        checkPeriodic();

        // Register for system intents
        IntentFilter filter = new IntentFilter(Intent.ACTION_TIME_TICK);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(mReceiver, filter);

        // Attach phone state listener
        mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);

        // Attach gps status listener
        mLocationManager.addGpsStatusListener(mGpsStateListener);

        Toast.makeText(this, "Test started.", Toast.LENGTH_SHORT).show();
    }

    /**
     * Called when stopping a test, this method stops logging and posts a final set of data to the
     * Volta server.
     */
    public void stopTest() {
        checkPeriodic();

        // Unregister for system intents
        unregisterReceiver(mReceiver);

        // Detach phone state listener
        mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);

        // Detach gps status listener
        mLocationManager.removeGpsStatusListener(mGpsStateListener);

        try {
            mTest.put("hardware_info", mHardwareInfo);
            mTest.put("data", mDataQueue);
            VoltaHttpClient.uploadDataPoints(mTest);
        } catch (Exception e) {
            Log.e(TAG, "Error adding hardware info and data points.", e);
        }

        Toast.makeText(this, "Test stopped.", Toast.LENGTH_SHORT).show();
    }

    private void createTest() {
        // Reset mTest, mHardwareInfo, and mDataQueue
        mTest = new JSONObject();
        mHardwareInfo = new JSONObject();
        mDataQueue = new JSONArray();

        // Generate a test UUID
        mTestUuid = UUID.randomUUID().toString();

        // Populate Hardware Info
        try {
            mHardwareInfo.put("sdk_version", Build.VERSION.SDK_INT);
            mHardwareInfo.put("model", Build.MODEL);
            mHardwareInfo.put("product", Build.PRODUCT);
        } catch (JSONException e) {
            Log.e(TAG, "Could not get hardware info", e);
        }

        // Populate Test info
        String deviceUuid = LQSharedPreferences.getDeviceUuid(this);
        String time = String.valueOf(System.currentTimeMillis() / 1000);

        try {
            mTest.put("test_uuid", mTestUuid);
            mTest.put("device_uuid", deviceUuid);
            mTest.put("mobile_platform", "android");
            mTest.put("carrier", mTelephonyManager.getNetworkOperatorName());
            mTest.put("timestamp", time);

        } catch (JSONException e) {
            Log.e(TAG, "Could not create test", e);
        }
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
        // TODO: use dedicated timer. this changes when the system time changes.
        Long timestamp = System.currentTimeMillis();
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

    public void checkPeriodic() {
        checkBatteryState();
        checkWifiState();
    }

    /**
     * Checks and records the current state of the battery.
     */
    private void checkBatteryState() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, filter);

        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int percent = (int)(level / (float) scale) * 100;

        int voltage = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);

        recordDataPoint(DATA_POINT_BATTERY_LEVEL, level);
        recordDataPoint(DATA_POINT_BATTERY_SCALE, scale);
        recordDataPoint(DATA_POINT_BATTERY_PERCENT, percent);
        recordDataPoint(DATA_POINT_BATTERY_VOLTAGE, voltage);
    }

    /**
     * Checks and records the current state of the WiFi radio.
     */
    private void checkWifiState() {
        int wifi = mWifiManager.isWifiEnabled() ? 1 : 0;
        recordDataPoint(DATA_POINT_WIFI_STATE, wifi);
    }

    private class VoltaBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_TIME_TICK)) {
                // Fired by the Android OS every minute
                checkPeriodic();
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
            if (signalStrength.isGsm()) {
                recordDataPoint(DATA_POINT_GSM_SIGNAL_STRENGTH,
                        signalStrength.getGsmSignalStrength());
            } else {
                // Data and Voice can be on either CDMA or EVDO depending on carrier.
                // We don't know which is which, so log both!
                recordDataPoint(DATA_POINT_CDMA_SIGNAL_STRENGTH,
                        signalStrength.getCdmaDbm());
                recordDataPoint(DATA_POINT_EVDO_SIGNAL_STRENGTH,
                        signalStrength.getEvdoDbm());
            }
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
package com.geoloqi.android.volta;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.IBinder;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

// TODO: make this into an intent-service
// TODO: export this in the manifest if you run into trouble with receiving intents from the system service
// TODO: stop listeners and receivers in onPause

/**
 * A service for monitoring system state during Geoloqi test runs.
 *
 * Periodic data is gathered every minute, triggered by  {@link Intent ACTION_TIME_TICK}.
 * Event based data is gathered when screen state and cellular reception changes.
 *
 * @author Josh Yaganeh
 */
public class VoltaService extends Service {

    public static final String TAG = "VoltaService";

    private static final String DATA_POINT_SCREEN_STATE = "screen";
    private static final String DATA_POINT_WIFI_STATE = "wifi";

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

    private boolean mTestInProgress = false;

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

        // Register for intents
        IntentFilter filter = new IntentFilter(Intent.ACTION_TIME_TICK);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);

        mReceiver = new VoltaBroadcastReceiver();
        registerReceiver(mReceiver, filter);

        // Set up phone state listener to receive signal strength updates
        mPhoneStateListener = new VoltaPhoneStateListener();
        mTelephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);

        Toast.makeText(this, "VoltaService started.", Toast.LENGTH_SHORT).show();
    }

    /**
     * Called when starting a test, this method posts an initial set of data to the Volta server and
     * begins logging.
     */
    public void startTest() {
        mTestInProgress = true;
        checkBatteryState();
        Toast.makeText(this, "Test started.", Toast.LENGTH_SHORT).show();
    }

    /**
     * Called when stopping a test, this method stops logging and posts a final set of data to the
     * Volta server.
     */
    public void stopTest() {
        mTestInProgress = false;
        checkBatteryState();
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
        // TODO: use dedicated timer. this changes when the system time changes.
        Long timestamp = System.currentTimeMillis();
        Log.d(TAG, "DATAPOINT: " + type + ", " + value + ", " + timestamp);
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

    private class VoltaBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_TIME_TICK)) {
                // Fired by the Android OS every minute
                checkBatteryState();
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
}
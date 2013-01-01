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

public class VoltaService extends Service {

    public static final String TAG = "VoltaService";

    private static final String DATA_POINT_SCREEN_STATE = "screen";
    private static final String DATA_POINT_WIFI_STATE = "wifi";
    private static final String DATA_POINT_BATTERY_LEVEL = "battery_level";
    private static final String DATA_POINT_BATTERY_SCALE = "battery_scale";
    private static final String DATA_POINT_BATTERY_PERCENT = "battery_percent";
    private static final String DATA_POINT_BATTERY_VOLTAGE = "battery_voltage";
    private static final String DATA_POINT_LOCATION_UPDATE = "location";
    private static final String DATA_POINT_LOCATION_PROVIDER = "provider";
    private static final String DATA_POINT_TRACKING_PROFILE = "profile";
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
        Log.d(TAG, "Received start id " + startId + ": " + intent);
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
        Toast.makeText(this, "VoltaService started.", Toast.LENGTH_SHORT).show();

        IntentFilter filter = new IntentFilter(Intent.ACTION_TIME_TICK);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);

        mReceiver = new VoltaBroadcastReceiver();
        registerReceiver(mReceiver, filter);

        mPhoneStateListener = new VoltaPhoneStateListener();
        mTelephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
    }

    public void startTest() {
        mTestInProgress = true;
        checkBatteryState();
        Toast.makeText(this, "Test started.", Toast.LENGTH_SHORT).show();
    }

    public void stopTest() {
        mTestInProgress = false;
        checkBatteryState();
        Toast.makeText(this, "Test stopped.", Toast.LENGTH_SHORT).show();
    }

    /**
     * Called when ACTION_TIME_TICK intent is received (happens every minute)
     */
    private void checkPeriodicData() {
        checkBatteryState();
    }

    public void recordDataPoint(String type, int value) {
        // TODO: use dedicated timer. this changes when the system time changes.
        Long timestamp = System.currentTimeMillis();
        Log.d(TAG, "DATAPOINT: " + type + ", " + value + ", " + timestamp);
    }

    private void checkBatteryState() {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, ifilter);

        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        /*
        boolean isCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING ||
                             status == BatteryManager.BATTERY_STATUS_FULL);

        int powerSource = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        */
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
                checkPeriodicData();
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
                recordDataPoint(DATA_POINT_CDMA_SIGNAL_STRENGTH,
                        signalStrength.getCdmaDbm());
                recordDataPoint(DATA_POINT_EVDO_SIGNAL_STRENGTH,
                        signalStrength.getEvdoDbm());
            }
        }
    }
}
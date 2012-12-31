package com.geoloqi.android.volta;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

public class VoltaService extends Service {

    public static final String TAG = "VoltaService";

    private final IBinder mBinder = new VoltaBinder();

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
    }

    public void startTest() {
        mTestInProgress = true;
        Toast.makeText(this, "Volta Test Started!", Toast.LENGTH_LONG).show();
    }

    public void stopTest() {
        mTestInProgress = false;
        Toast.makeText(this, "Volta Test Stopped!", Toast.LENGTH_LONG).show();
    }
}

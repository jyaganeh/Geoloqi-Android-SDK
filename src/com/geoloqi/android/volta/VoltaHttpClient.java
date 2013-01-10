package com.geoloqi.android.volta;

import android.util.Log;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONException;
import org.json.JSONObject;

public class VoltaHttpClient {
    public static final String TAG = "VoltaHttpClient";

    private static final String BASE_URL = "http://volta.geoloqi.com/api/1/";
    private static final String TEST_URL = BASE_URL + "tests/";

    private static final String DATA_POINTS_URL = BASE_URL + "datapoints/";
    private static final String DEVICE_INFO_URL = BASE_URL + "devices/";


    public static String makeRequest(String path, JSONObject postData) throws Exception {
        Log.d(TAG, "Making request to: " + path);
        DefaultHttpClient httpClient = new DefaultHttpClient();
        HttpPost httpPost = new HttpPost(path);
        StringEntity entity = new StringEntity(postData.toString());
        Log.d(TAG, "Posts:");
        Log.d(TAG, postData.toString());
        Log.d(TAG, "Entity:");
        Log.d(TAG, entity.getContent().toString());
        httpPost.setEntity(entity);
        httpPost.setHeader("Accept", "application/json");
        httpPost.setHeader("Content-Type", "application/json");

        ResponseHandler responseHandler = new BasicResponseHandler();
        return (String) httpClient.execute(httpPost, responseHandler);
    }

    public static boolean postDeviceInfo(JSONObject deviceInfo) {
        String url = DEVICE_INFO_URL;

        try {
            String response = makeRequest(url, deviceInfo);
            Log.d(TAG, "Status Code: " + response);
        } catch (Exception e) {
            Log.e(TAG, "Error making request", e);
            return false;
        }

        return true;
    }

    public static boolean postDataPoints(JSONObject dataPoints) {
        String url = TEST_URL;

        try {
            Log.d(TAG, dataPoints.toString(2));
        } catch (JSONException e) {
            Log.d(TAG, dataPoints.toString());
        }

        try {
            String response = makeRequest(url, dataPoints);
            Log.d(TAG, "Status Code: " + response);
        } catch (Exception e) {
            Log.e(TAG, "Error making request", e);
            return false;
        }

        return true;
    }
}

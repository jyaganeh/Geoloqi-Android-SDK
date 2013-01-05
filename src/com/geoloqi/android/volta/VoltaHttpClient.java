package com.geoloqi.android.volta;

import android.util.Log;
import org.apache.http.HttpResponse;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONObject;

public class VoltaHttpClient {
    public static final String TAG = "VoltaHttpClient";
    private static final String VOLTA_URL = "http://geoloqi-volta.herokuapp.com/api/incoming";


    public static String makeRequest(String path, JSONObject postData) throws Exception {
        Log.d(TAG, "Making request to: " + path);
        DefaultHttpClient httpClient = new DefaultHttpClient();
        HttpPost httpPost = new HttpPost(path);
        StringEntity entity = new StringEntity(postData.toString());

        httpPost.setEntity(entity);
        httpPost.setHeader("Accept", "application/json");
        httpPost.setHeader("Content-Type", "application/json");

        ResponseHandler responseHandler = new BasicResponseHandler();
        return (String) httpClient.execute(httpPost, responseHandler);
    }

    public static boolean uploadDataPoints(JSONObject test) {
        try {
            String response = makeRequest(VOLTA_URL, test);
            Log.d(TAG, "Status Code: " + response);
        } catch (Exception e) {
            Log.e(TAG, "Error making request", e);
            return false;
        }
        return true;
    }
}

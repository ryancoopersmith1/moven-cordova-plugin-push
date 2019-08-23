package com.adobe.phonegap.push;

import com.moven.td.dev.R;

import android.content.Intent;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.os.Bundle;

import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.FirebaseInstanceIdService;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.HttpURLConnection;
import java.net.URL;

public class PushInstanceIDListenerService extends FirebaseInstanceIdService implements PushConstants {
    public static final String LOG_TAG = "Push_InsIdService";

    @Override
    public void onTokenRefresh() {
        // Get updated InstanceID token.
        String refreshedToken = FirebaseInstanceId.getInstance().getToken();
        Log.d(LOG_TAG, "Refreshed token: " + refreshedToken);
        sendRegistrationToServer(refreshedToken);
    }

    public void sendRegistrationToServer(String token) {
        Context context = getApplicationContext();

        SharedPreferences prefs = context.getSharedPreferences(COM_ADOBE_PHONEGAP_PUSH,
            Context.MODE_PRIVATE);
        String oldToken = prefs.getString(FCM_TOKEN, null);

        if (oldToken.isEmpty()) {
            return;
        }

        try {
            JSONObject body = new JSONObject();
            body.put("token", token);
            body.put("oldToken", oldToken);

            String appHost = context.getResources()
                .getString(R.string.app_host);
            String urlString = appHost.concat("/pay/device/refresh");

            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setReadTimeout(10000 /* milliseconds */);
            conn.setConnectTimeout(15000 /* milliseconds */);
            conn.setDoInput(true);
            conn.setDoOutput(true);

            conn.setRequestMethod("PUT");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Accept", "*/*");

            conn.setRequestProperty("x-moven-channel", "mobile");
            conn.setRequestProperty("x-moven-osversion", String.valueOf(android.os.Build.VERSION.SDK_INT));
            conn.setRequestProperty("x-moven-appos","android");

            if (body != null) {
                conn.setRequestProperty("Content-Length", Integer.toString(body.toString().getBytes().length));
            }

            sendRequest(body, conn);

            conn.connect();
            int responseCode = conn.getResponseCode();

            if (responseCode >= 200 && responseCode <= 299) {
                Log.d(LOG_TAG, "Successfully saved refreshed token in server. Response code: " + responseCode);

                // TODO IMPORTANT - make sure token was actually replaced before doing this?
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString(FCM_TOKEN, token);
            } else {
                Log.e(LOG_TAG, "Failed to save refreshed token in server. Response code: " + responseCode);
            }
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }
    }

    public static void sendRequest(JSONObject body, HttpURLConnection conn) throws IOException {
        DataOutputStream wr = new DataOutputStream(
                conn.getOutputStream ());
        wr.writeBytes(body.toString());
        wr.flush();
        wr.close();
    }
}

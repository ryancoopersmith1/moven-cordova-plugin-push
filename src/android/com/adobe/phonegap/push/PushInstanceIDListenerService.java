package com.adobe.phonegap.push;

import ANDROID_APP_ID.R;

import android.content.Intent;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.os.Bundle;
import android.os.AsyncTask;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;

import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.FirebaseInstanceIdService;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.lang.InterruptedException;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.HttpURLConnection;
import java.net.URL;

class ForegroundCheckTask extends AsyncTask<Context, Void, Boolean> {

    @Override
    protected Boolean doInBackground(Context... params) {
        final Context context = params[0].getApplicationContext();
        return isAppOnForeground(context);
    }

    private boolean isAppOnForeground(Context context) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<RunningAppProcessInfo> appProcesses = activityManager.getRunningAppProcesses();
        if (appProcesses == null) {
            return false;
        }

        final String packageName = context.getPackageName();
        for (RunningAppProcessInfo appProcess : appProcesses) {
            if (appProcess.importance == RunningAppProcessInfo.IMPORTANCE_FOREGROUND && appProcess.processName.equals(packageName)) {
                return true;
            }
        }

        return false;
    }
}

public class PushInstanceIDListenerService extends FirebaseInstanceIdService implements PushConstants {
    public static final String LOG_TAG = "Push_InsIdService";

    @Override
    public void onTokenRefresh() {
        // Get updated InstanceID token.
        String refreshedToken = FirebaseInstanceId.getInstance().getToken();
        Log.d(LOG_TAG, "Refreshed token: " + refreshedToken);
        sendRegistrationToServer(refreshedToken);
    }

    private void sendRegistrationToServer(String token) {
        if (token == null || token.isEmpty()) {
            Log.d(LOG_TAG, "Empty registration ID received from FCM");
            return;
        }

        Context context = getApplicationContext();

        try {
            boolean foregroud = new ForegroundCheckTask().execute(context).get();
            if (foregroud) {
                Log.d(LOG_TAG, "App is in foreground. Not updating FCM token");
                return;
            }
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
            return;
        }

        SharedPreferences prefs = context.getSharedPreferences(PushPlugin.COM_ADOBE_PHONEGAP_PUSH,
            Context.MODE_PRIVATE);
        String oldToken = prefs.getString(REGISTRATION_ID, null);

        Log.d(LOG_TAG, "Old FCM token to update: " + oldToken);
        if (oldToken == null || oldToken.isEmpty()) {
            return;
        }

        try {
            JSONObject body = new JSONObject();
            body.put("token", token);
            body.put("old_token", oldToken);

            String pushTokenRefreshHost = context.getResources()
                .getString(R.string.push_token_refresh_host);
            String urlString = pushTokenRefreshHost.concat("/push-token-refresh");

            Log.d(LOG_TAG, "Sending request to " + urlString + " to update FCM token");

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

                SharedPreferences.Editor editor = prefs.edit();
                editor.putString(REGISTRATION_ID, token);
                editor.apply();
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

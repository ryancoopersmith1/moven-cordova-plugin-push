package com.adobe.phonegap.push;

import android.content.Intent;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.FirebaseInstanceIdService;

import org.json.JSONException;

import java.io.IOException;

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
        SharedPreferences prefs = getApplicationContext().getSharedPreferences(COM_ADOBE_PHONEGAP_PUSH,
            Context.MODE_PRIVATE);
        String oldToken = prefs.getString(FCM_TOKEN, null);
        // API call to new endpoint to find old token and update with new token
    }
}

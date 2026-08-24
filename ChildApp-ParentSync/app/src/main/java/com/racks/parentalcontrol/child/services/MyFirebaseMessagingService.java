package com.racks.parentalcontrol.child.services;

import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        if (!remoteMessage.getData().isEmpty()) {
            String action = remoteMessage.getData().get("action");
            if ("START_SERVICE".equals(action)) {
                sendBroadcast(new Intent("com.racks.ACTION_START_FOREGROUND_SERVICE"));
            } else if ("STOP_SERVICE".equals(action)) {
                sendBroadcast(new Intent("com.racks.ACTION_STOP_FOREGROUND_SERVICE"));
            }
        }
    }

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
    }
}

package com.racks.parentalcontrol.child.services;

import android.app.Notification;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;

import com.racks.parentalcontrol.child.remote.FirebaseClient;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class NotificationListenerService extends android.service.notification.NotificationListenerService {

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        Notification notification = sbn.getNotification();
        if (notification == null) return;

        String packageName = sbn.getPackageName();
        String appName = getAppNameFromPackage(getApplicationContext(), packageName);
        Bundle extras = notification.extras;

        String title = extras.getString(Notification.EXTRA_TITLE);
        CharSequence text = extras.getCharSequence(Notification.EXTRA_TEXT);
        String subText = extras.getString(Notification.EXTRA_SUB_TEXT);
        String infoText = extras.getString(Notification.EXTRA_INFO_TEXT);
        CharSequence bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        CharSequence summaryText = extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT);


        long postTime = sbn.getPostTime();
        if (shouldIgnoreNotification(sbn, notification)) return;


        Map<String, Object> notificationData = new HashMap<>();
        notificationData.put("package_name", packageName);
        notificationData.put("app_name", appName);
        notificationData.put("title", title);
        notificationData.put("text", text != null ? text.toString() : null);
        notificationData.put("sub_text", subText);
        notificationData.put("info_text", infoText);
        notificationData.put("big_text",  bigText != null ? bigText.toString() : null);
        notificationData.put("summary_text", summaryText != null ? summaryText.toString() : null);
        notificationData.put("timestamp", postTime);
        FirebaseClient firebaseClient = new FirebaseClient();
        firebaseClient.updateNotifications(notificationData);
    }
    private boolean shouldIgnoreNotification(StatusBarNotification sbn, Notification notification) {
        String packageName = sbn.getPackageName();
        if (packageName.equals("com.internet.speed.meter.lite") || packageName.equals("com.android.systemui") ||
                packageName.equals("com.spotify.music")) {
            return true;
        }
        if (Notification.CATEGORY_TRANSPORT.equals(notification.category)) {
            return true;
        }

        if (notification.extras.get("android.mediaSession") != null) {
            return true;
        }

        Set<String> mediaApps = new HashSet<>(Arrays.asList(
                "com.spotify.music",
                "com.google.android.apps.youtube.music",
                "com.mxtech.videoplayer.ad",
                "com.jio.media.jiobeats",
                "com.wynk.music"
        ));
        if (mediaApps.contains(sbn.getPackageName())) {
            return true;
        }
        return (sbn.getNotification().flags & Notification.FLAG_ONGOING_EVENT) != 0;
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {

    }
    private String getAppNameFromPackage(Context context, String packageName) {
        try {
            PackageManager pm = context.getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);
            return pm.getApplicationLabel(ai).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return packageName; // fallback to package name
        }
    }

}

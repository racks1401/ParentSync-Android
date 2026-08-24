package com.racks.parentalcontrol.child.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.racks.parentalcontrol.child.R;
import com.racks.parentalcontrol.child.webrtc.MyPeerConnectionObserver;
import com.racks.parentalcontrol.child.webrtc.WebRTCClient;

public class ScreenCaptureService extends Service {
    private int mResultCode;
    private Intent mResultData;
    private String target;
    private WebRTCClient webRTCClient;
    private boolean isScreenshot = false;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP_CAPTURING".equals(intent.getAction())) {
            stopForeground(true);
            stopSelf();
            Toast.makeText(getApplicationContext(), "Screen Capturing stopped", Toast.LENGTH_SHORT).show();
            return START_NOT_STICKY;
        }
        if (intent!=null){
            if (intent.hasExtra("isScreenshot")){
                isScreenshot = intent.getBooleanExtra("isScreenshot", false);
                mResultCode = intent.getIntExtra("resultCode", -1);
                mResultData = intent.getParcelableExtra("data");
            }else {
                isScreenshot = false;
                mResultCode = intent.getIntExtra("resultCode", -1);
                mResultData = intent.getParcelableExtra("data");
                target = intent.getStringExtra("target");
            }
        }else{
            stopSelf(startId);
        }
        Toast.makeText(getApplicationContext(), "Screen Capturing Started", Toast.LENGTH_SHORT).show();
        String channelId = "MyScreenCaptureChannelId";
        String channelName = "My Screen Capture Service";
        NotificationChannel channel = new NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
        Notification notification = new NotificationCompat.Builder(this, channelId)
                .setContentTitle("Running in background")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOngoing(true)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .build();
        startFGS(notification);
        return START_STICKY;
    }

    private void startFGS(Notification notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE){
            startForeground(101, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(101, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(101, notification);
        }
        initWebRTCAndCaptureScreen();
    }

    private void initWebRTCAndCaptureScreen() {
        webRTCClient = WebRTCClient.getInstance(getApplicationContext(), new MyPeerConnectionObserver());
        if (isScreenshot){
            MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            MediaProjection mediaProjection = mpm.getMediaProjection(mResultCode, mResultData);
            SnapshotManager.getInstance(getApplicationContext()).setMediaProjection(mediaProjection);
            SnapshotManager.getInstance(getApplicationContext()).takeScreenshot(() -> {
                stopForeground(true);
                stopSelf();
            });
        }else{
            webRTCClient.startScreenShareFromIntent(mResultData);
            webRTCClient.call(target);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        webRTCClient.stopScreenShare();
    }
}

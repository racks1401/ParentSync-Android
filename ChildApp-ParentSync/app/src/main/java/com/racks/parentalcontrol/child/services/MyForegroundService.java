package com.racks.parentalcontrol.child.services;

import android.Manifest;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationManager;

import android.os.BatteryManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.gson.Gson;
import com.racks.parentalcontrol.child.R;
import com.racks.parentalcontrol.child.activities.TransparentPermissionActivity;
import com.racks.parentalcontrol.child.remote.FirebaseClient;
import com.racks.parentalcontrol.child.utils.Helper;
import com.racks.parentalcontrol.child.utils.MySharedPreferences;
import com.racks.parentalcontrol.child.utils.PermissionHelper;
import com.racks.parentalcontrol.child.utils.ReadCallLogs;
import com.racks.parentalcontrol.child.webrtc.MyPeerConnectionObserver;
import com.racks.parentalcontrol.child.webrtc.WebRTCClient;

import org.webrtc.IceCandidate;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.RtpReceiver;
import org.webrtc.SessionDescription;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MyForegroundService extends Service{
    private WebRTCClient webRTCClient;
    private Gson gson = new Gson();
    private FirebaseClient firebaseClient;
    private String target;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private LocationCallback locationCallback;
    private SnapshotManager snapshotManager;
    private MySharedPreferences sharedPreferences;
    private Boolean routeEnabled = false;
    private Location lastUploadedLocation = null;
    private static final float MIN_UPLOAD_DISTANCE_METERS = 1.5f;
    private String currentDateKey;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());

    private final IBinder binder = new LocalBinder();

    public class LocalBinder extends Binder {
        public MyForegroundService getService() {
            return MyForegroundService.this;
        }
    }

    private void webrtcConnected() {
        Log.d("RaviKumar-MyForeground", "webrtcConnected() called");
    }
    private void webrtcClosed() {
        Log.d("RaviKumar-MyForeground", "webrtcClosed() called");
    }

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int batteryPct = (int) ((level / (float) scale) * 100);

            updateBatteryPercentage(batteryPct);
        }
    };

    private void updateBatteryPercentage(int batteryPct) {
        Log.d("RaviKumar-ForegroundService", "Battery "+batteryPct);
        firebaseClient.updateBatteryStatus(String.valueOf(batteryPct), () -> {},err -> {});
    }

    @Override
    public void onCreate() {
        super.onCreate();
        this.firebaseClient = new FirebaseClient();
        sharedPreferences = new MySharedPreferences(getApplicationContext());
        sharedPreferences.setFgServiceRunningFlag(true);
        snapshotManager = SnapshotManager.getInstance(getApplicationContext());
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(batteryReceiver, filter);
        firebaseClient.updateOnlineStatus("online", ()->{},err->{});
        currentDateKey = dateFormat.format(new Date());
    }

    private void startForegroundService() {
        String channelId = "MyChannelId";
        String channelName = "My Foreground Service";

        NotificationChannel channel = new NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_MIN
        );
        channel.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }

        Intent stopSelf = new Intent(this, MyForegroundService.class);
        stopSelf.setAction("STOP_FOREGROUND_SERVICE");
        PendingIntent pStopSelf = PendingIntent.getService(
                this,
                0,
                stopSelf,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification notification = new NotificationCompat.Builder(this, channelId)
                .setContentTitle("Service Running")
                .setContentText("Location Service is running.")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .addAction(R.drawable.ic_launcher_foreground, "Exit", pStopSelf)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q){
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);

        }else{
            startForeground(1, notification);
        }

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP_FOREGROUND_SERVICE".equals(intent.getAction())) {
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        startForegroundService();
        webRTCClient = WebRTCClient.getInstance(getApplicationContext(), new MyPeerConnectionObserver() {
            @Override
            public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
                super.onAddTrack(rtpReceiver, mediaStreams);
            }

            @Override
            public void onConnectionChange(PeerConnection.PeerConnectionState newState) {
                if (newState == PeerConnection.PeerConnectionState.CONNECTED) webrtcConnected();
                if (newState == PeerConnection.PeerConnectionState.CLOSED || newState == PeerConnection.PeerConnectionState.DISCONNECTED ||
                        newState == PeerConnection.PeerConnectionState.FAILED) webrtcClosed();
            }

            @Override
            public void onIceConnectionChange(PeerConnection.IceConnectionState newState) {
                Log.d("RaviKumar-MyForegroundService", "onIceConnectionChange: " + newState);
                super.onIceConnectionChange(newState);
            }

            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                super.onIceCandidate(iceCandidate);
                webRTCClient.sendIceCandidate(iceCandidate, target);
            }

        });
        listenForIncomingCalls();
        listenForTriggers();
        checkLocationEnabledAtStart();
        startLocationUpdates();
        return START_STICKY;
    }
    private void startLocationUpdates() {
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(getApplicationContext());
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                Location currentLocation = locationResult.getLastLocation();
                if (currentLocation != null) {
                    if (shouldUpload(currentLocation)) {
                        updateFirebaseDatabase(currentLocation);
                    }
                }
            }
        };
        fetchLastLocation(locationCallback);
    }

    private void fetchLastLocation(LocationCallback locationCallback) {

        LocationRequest locationRequest =
                new LocationRequest.Builder(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        1500
                )
                        .setMinUpdateIntervalMillis(1000)
                        .build();

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED
                &&
                ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED) {

            Log.e(
                    "RaviKumar-ForegroundService",
                    "Location permission not granted"
            );

            return;
        }

        fusedLocationProviderClient
                .requestLocationUpdates(
                        locationRequest,
                        locationCallback,
                        Looper.getMainLooper()
                )
                .addOnSuccessListener(unused -> {
                    Log.d(
                            "RaviKumar-ForegroundService",
                            "Location updates registered successfully"
                    );
                })
                .addOnFailureListener(e -> {
                    Log.e(
                            "RaviKumar-ForegroundService",
                            "Failed to register location updates",
                            e
                    );
                });
    }
    private boolean shouldUpload(Location newLocation) {
        if (lastUploadedLocation == null) return true;
        float distance = newLocation.distanceTo(lastUploadedLocation);
        return distance >= MIN_UPLOAD_DISTANCE_METERS;
    }

    private void updateFirebaseDatabase(Location location) {
        String now = dateFormat.format(new Date());
        if (!now.equals(currentDateKey)) {
            currentDateKey = now;
        }

        Map<String, Object> locationDetail = new HashMap<>();
        locationDetail.put("latitude", location.getLatitude());
        locationDetail.put("longitude", location.getLongitude());
        locationDetail.put("lastUpdate", System.currentTimeMillis());
        locationDetail.put("accuracy", location.getAccuracy());
        locationDetail.put("provider", location.getProvider());

        DatabaseReference baseRef = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(firebaseClient.getAuthUID())
                .child("Children")
                .child(firebaseClient.getDeviceId())
                .child("Location");

        if (routeEnabled && location.getAccuracy() <= 10f) {
            baseRef.child("location_history").child(currentDateKey).push().setValue(locationDetail);
        }
        baseRef.child("current_location").updateChildren(locationDetail);
    }

    private void listenForTriggers() {
        firebaseClient.listenForTriggers(triggerModel -> {
            if (triggerModel != null) {
                if (triggerModel.getEnableRoute()!=null){
                    routeEnabled = triggerModel.getEnableRoute();
                }
                if (Boolean.TRUE.equals(triggerModel.getResetConnection())) {
                    stopScreenSharing();
                    webRTCClient.cleanupBeforeSwitchingStreamMode(() -> {
                        firebaseClient.resetTrigger("resetConnection", false);
                        webRTCClient.closePeerConnection();
                    });
                }
                Boolean showAppIcon = triggerModel.getShowAppIcon();
                if (showAppIcon == null) {
                    showAppIcon = true;
                    firebaseClient.resetTrigger("showAppIcon", true);
                }
                Helper.setAppIconVisibility(getApplicationContext(), showAppIcon);

                if (Boolean.TRUE.equals(triggerModel.getSwitchCamera())) {
                    webRTCClient.switchCamera();
                }
                if (Boolean.TRUE.equals(triggerModel.getToggleAudio())) {
                    webRTCClient.toggleAudio();
                }
                if (Boolean.TRUE.equals(triggerModel.getToggleVideo())) {
                    webRTCClient.toggleVideo();
                }
                if (Boolean.TRUE.equals(triggerModel.getFrontSnapshot())) {
                    if (PermissionHelper.isCameraPermissionGranted(getApplicationContext())){
                        snapshotManager.takeSnapshot(true);
                    }else{
                        firebaseClient.showErrorRemotely("Camera permission is not granted by the child", "MessageToParent");

                    }
                    firebaseClient.resetTrigger("frontSnapshot", false);
                }
                if (Boolean.TRUE.equals(triggerModel.getRearSnapshot())) {
                    if (PermissionHelper.isCameraPermissionGranted(getApplicationContext())){
                        snapshotManager.takeSnapshot(false);
                    }else{
                        firebaseClient.showErrorRemotely("Camera permission is not granted by the child", "MessageToParent");
                    }
                    firebaseClient.resetTrigger("rearSnapshot", false);
                }
                if (Boolean.TRUE.equals(triggerModel.getGetCallLogs())) {
                    if (PermissionHelper.isCallLogsPermissionGranted(getApplicationContext())){
                        ReadCallLogs callLogs = new ReadCallLogs(getApplicationContext());
                        callLogs.readAndUploadCallLogs();
                    }else{
                        firebaseClient.showErrorRemotely("Call Logs permission is not granted by the child", "MessageToParent");
                    }
                    firebaseClient.resetTrigger("getCallLogs", false);
                }
                if (Boolean.TRUE.equals(triggerModel.getScreenSnapshot())) {
                    if (PermissionHelper.isAccessibilityServiceEnabled(getApplicationContext())){
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R){
                            Intent intent = new Intent(AutoClickAccessibilityService.ACTION_TAKE_SCREENSHOT);
                            intent.putExtra("useTakeScreenshot", true);
                            sendBroadcast(intent);
                        }else{
                            Intent intent = new Intent(getApplicationContext(), TransparentPermissionActivity.class);
                            intent.putExtra("isScreenShare", false);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                        }
                    }else{
                        firebaseClient.showErrorRemotely("Accessibility permission is not granted by the child", "MessageToParent");
                    }
                    firebaseClient.resetTrigger("screenSnapshot", false);
                }
            } else {
                Log.w("RaviKumar-MyForegroundService", "TriggerModel is null");
            }
        });
    }

    private void stopScreenSharing() {
        if (isScreenCaptureServiceRunning()){
            Intent screenIntent = new Intent(this, ScreenCaptureService.class);
            screenIntent.setAction("STOP_CAPTURING");
            startService(screenIntent);
        }
    }

    private boolean isScreenCaptureServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (ScreenCaptureService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    private void listenForIncomingCalls() {
        firebaseClient.observeIncomingLatestEvent(model -> {
            this.target = model.getSender();
            switch (model.getType()) {
                case Offer:
                    webRTCClient.onRemoteSessionReceived(new SessionDescription(
                            SessionDescription.Type.OFFER, model.getData()
                    ));
                    webRTCClient.answer(target);
                    break;
                case Answer:
                    webRTCClient.onRemoteSessionReceived(new SessionDescription(
                            SessionDescription.Type.ANSWER, model.getData()
                    ));
                    break;
                case IceCandidate:
                    try {
                        IceCandidate candidate = gson.fromJson(model.getData(), IceCandidate.class);
                        webRTCClient.addIceCandidate(candidate);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
                case StartCall:
                    webRTCClient.resetConnection(()->{
                        switch (model.getStreamMode()){
                            case CAMERA:
                                if (PermissionHelper.isCameraPermissionGranted(getApplicationContext())){
                                    stopScreenSharing();
                                    webRTCClient.startLocalVideoStreaming();
                                    webRTCClient.call(target);
                                }else{
                                    firebaseClient.showErrorRemotely("Camera permission is not granted by the child", "ConnectionError");
                                }
                                break;

                            case AUDIO_ONLY:
                                if (PermissionHelper.isMicrophonePermissionGranted(getApplicationContext())){
                                    stopScreenSharing();
                                    webRTCClient.startAudioOnly();
                                    webRTCClient.call(target);
                                }else{
                                    firebaseClient.showErrorRemotely("Microphone permission is not granted by the child", "ConnectionError");
                                }
                                break;

                            case SCREEN:
                                if (Build.VERSION.SDK_INT != Build.VERSION_CODES.UPSIDE_DOWN_CAKE){
                                    if (PermissionHelper.isAccessibilityServiceEnabled(getApplicationContext())){
                                        Intent intent = new Intent(getApplicationContext(), TransparentPermissionActivity.class);
                                        intent.putExtra("isScreenShare", true);
                                        intent.putExtra("target", target);
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                        startActivity(intent);
                                        Log.d("RaviKumar-ForegroundService", "StartCall- screenshare");

                                    }else{
                                        firebaseClient.showErrorRemotely("Accessibility permission is not granted by the child", "ConnectionError");

                                    }
                                }else{
                                    firebaseClient.showErrorRemotely("AutoClick is not working in Android 14", "ConnectionError");
                                }
                                break;
                        }
                    });
                    break;
            }
        });
    }
    private void checkLocationEnabledAtStart() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        boolean isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

        if (!isGpsEnabled && !isNetworkEnabled) {
            firebaseClient.showErrorRemotely("Location is disabled in child device", "MessageToParent");
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopScreenSharing();
        sharedPreferences.setFgServiceRunningFlag(false);
        if (webRTCClient!=null){
            webRTCClient.cleanupBeforeSwitchingStreamMode(WebRTCClient::destroyInstance);
        }
        firebaseClient.removeIncomingCallListener();
        firebaseClient.removeTriggerListener();
        if (fusedLocationProviderClient != null && locationCallback != null) {
            fusedLocationProviderClient.removeLocationUpdates(locationCallback);
        }
        unregisterReceiver(batteryReceiver);
        firebaseClient.updateBatteryStatus("unknown", ()->{}, err->{});
        firebaseClient.updateOnlineStatus(String.valueOf(System.currentTimeMillis()), ()->{}, err->{});
    }
}

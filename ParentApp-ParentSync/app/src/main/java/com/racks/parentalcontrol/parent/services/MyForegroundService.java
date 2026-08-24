package com.racks.parentalcontrol.parent.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.gson.Gson;
import com.racks.parentalcontrol.parent.R;
import com.racks.parentalcontrol.parent.interfaces.SuccessCallBack;
import com.racks.parentalcontrol.parent.remote.FirebaseClient;
import com.racks.parentalcontrol.parent.models.ChildDetailModel;
import com.racks.parentalcontrol.parent.models.DataModel;
import com.racks.parentalcontrol.parent.models.DataModelType;
import com.racks.parentalcontrol.parent.interfaces.ErrorCallBack;
import com.racks.parentalcontrol.parent.utils.MySharedPreferences;
import com.racks.parentalcontrol.parent.interfaces.NewEventCallBack;
import com.racks.parentalcontrol.parent.models.StreamMode;
import com.racks.parentalcontrol.parent.webrtc.MyPeerConnectionObserver;
import com.racks.parentalcontrol.parent.webrtc.WebRTCClient;

import org.webrtc.AudioTrack;
import org.webrtc.IceCandidate;
import org.webrtc.MediaStream;
import org.webrtc.MediaStreamTrack;
import org.webrtc.PeerConnection;
import org.webrtc.RtpTransceiver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoTrack;

public class MyForegroundService extends Service {

    public static MyForegroundService instance;
    private WebRTCClient webRTCClient;
    private final Gson gson = new Gson();
    private FirebaseClient firebaseClient;
    private String target;

    private final IBinder binder = new LocalBinder();
    private static Listener listener;
    public static void setListener(Listener l) {
        listener = l;
    }

    public class LocalBinder extends Binder {
        public MyForegroundService getService() {
            return MyForegroundService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        MySharedPreferences mySharedPreferences = new MySharedPreferences(getApplicationContext());
        firebaseClient = new FirebaseClient(mySharedPreferences);
        startForegroundService();
        webRTCClient = new WebRTCClient(getApplicationContext(), new MyPeerConnectionObserver(){

            @Override
            public void onTrack(RtpTransceiver transceiver) {
                MediaStreamTrack track = transceiver.getReceiver().track();

                if (listener != null) {
                    if (track instanceof VideoTrack) {
                        VideoTrack videoTrack = (VideoTrack) track;
                        videoTrack.setEnabled(true);
                        listener.onRemoteVideoTrack(videoTrack);

                    } else if (track instanceof AudioTrack) {
                        AudioTrack audioTrack = (AudioTrack) track;
                        listener.onRemoteAudioTrack(audioTrack);
                    }
                }

            }

            @Override
            public void onConnectionChange(PeerConnection.PeerConnectionState newState) {
                if (listener!=null){
                    if (newState == PeerConnection.PeerConnectionState.CONNECTED) listener.webrtcConnected();
                    if (newState == PeerConnection.PeerConnectionState.CLOSED || newState == PeerConnection.PeerConnectionState.DISCONNECTED ||
                            newState == PeerConnection.PeerConnectionState.FAILED) listener.webrtcClosed();
                }
            }

            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                super.onIceCandidate(iceCandidate);
                webRTCClient.sendIceCandidate(iceCandidate, target);
            }
        });
        listenForIncomingCalls();
    }

    private void startForegroundService() {
        Toast.makeText(getApplicationContext(), "Service Started", Toast.LENGTH_SHORT).show();
        String channelId = "MyChannelId";
        String channelName = "My Foreground Service";

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
                .setContentText("Foreground service is running.")
                .setSmallIcon(R.mipmap.ic_launcher_foreground)
                .addAction(R.mipmap.ic_launcher_foreground, "Exit", pStopSelf)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(false)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE) // <- critical for Android 14+
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(1, notification);
        }

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP_FOREGROUND_SERVICE".equals(intent.getAction())) {
            removeAllListeners();
            stopForeground(true);
            stopSelf();
            LocalBroadcastManager.getInstance(getApplicationContext())
                    .sendBroadcast(new Intent("SERVICE_STOPPED"));
            Toast.makeText(getApplicationContext(), "Service Stopped", Toast.LENGTH_SHORT).show();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    private void listenForIncomingCalls() {
        firebaseClient.observeIncomingLatestEvent(new NewEventCallBack() {
            @Override
            public void onNewEventReceived(DataModel model) {
                target = model.getSender();
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
                }
            }
            @Override
            public void onNewEvenReceived(ChildDetailModel childDetailModel) {

            }
            @Override
            public void onError(String err) {

            }

        });
    }
    public void initRemoteView(SurfaceViewRenderer remoteView){
        Log.d("RaviKumar-MyForegroundService", "initRemoteView");
        webRTCClient.initRemoteSurfaceView(remoteView);
    }
    public void endCall(){
        closeConnection();
    }
    public void switchCamera(){
        firebaseClient.createTrigger("switchCamera", true, () -> {

        }, err -> {
            Toast.makeText(this, "Trigger failed"+err, Toast.LENGTH_SHORT).show();
        });
    }
    public void toggleAudio(){
        firebaseClient.createTrigger("toggleAudio", true, () -> {

        }, err -> {

        });
    }
    public void toggleVideo(){
        firebaseClient.createTrigger("toggleVideo", true, () -> {

        }, err -> {

        });
    }
    public void sendCallRequest(String target, String curr_user, StreamMode streamMode, ErrorCallBack errorCallBack){
        Log.d("RaviKumar-MyForegroundService", "sendCallRequest"+ target);
        firebaseClient.sendMessageToOtherUser(
                new DataModel(target, curr_user, null, DataModelType.StartCall, streamMode), errorCallBack);
    }
    public void releaseRenderer(SurfaceViewRenderer viewRenderer){
        webRTCClient.releaseRenderer(viewRenderer);
    }
    public void closeConnection(){
        firebaseClient.createTrigger("resetConnection", true, ()->{}, err->{
            Toast.makeText(this, "Connection Reset failed"+err, Toast.LENGTH_SHORT).show();
        });
        webRTCClient.closeConnection();
    }
    public void createConnection(){
        webRTCClient.createConnection();
    }



    public interface Listener {
        void webrtcConnected();
        void webrtcClosed();
        void onRemoteVideoTrack(VideoTrack videoTrack);
        void onRemoteAudioTrack(AudioTrack audioTrack);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        removeAllListeners();
        stopForeground(true);
        stopSelf();
        super.onTaskRemoved(rootIntent);
    }
    public void removeAllListeners(){
        webRTCClient.closeConnection();
        firebaseClient.removeIncomingEventListener();
        firebaseClient.removeChildLocationListener();
        firebaseClient.removeChildDetailListener();
        firebaseClient.removeChildMsgListener();
    }
}

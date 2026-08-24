package com.racks.parentalcontrol.child.webrtc;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.projection.MediaProjection;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.util.Log;

import com.google.gson.Gson;
import com.racks.parentalcontrol.child.remote.FirebaseClient;
import com.racks.parentalcontrol.child.models.DataModel;
import com.racks.parentalcontrol.child.models.DataModelType;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.ScreenCapturerAndroid;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import java.util.ArrayList;
import java.util.List;

public class WebRTCClient {

    private final Gson gson = new Gson();
    private final Context context;
    private final String username = "child";
    private final EglBase.Context eglBaseContext= EglBase.create().getEglBaseContext();
    private final PeerConnectionFactory peerConnectionFactory;
    private PeerConnection peerConnection;
    private final List<PeerConnection.IceServer> iceServer = new ArrayList<>();
    private VideoCapturer videoCapturer;
    private VideoSource localVideoSource;
    private AudioSource localAudioSource;
    private final String localTrackId = "local_track";
    private VideoTrack localVideoTrack;
    private boolean isAudioEnabled = false;
    private boolean isVideoEnabled = false;
    private AudioTrack localAudioTrack;
    private MediaStream localStream;
    private boolean isScreenSharing;
    private final MediaConstraints mediaConstraints = new MediaConstraints();
    private final FirebaseClient firebaseClient;
    private final PeerConnection.Observer connectionObserver;
    private static WebRTCClient instance;

    public static WebRTCClient getInstance(Context context, PeerConnection.Observer observer) {
        if (instance == null) {
            instance = new WebRTCClient(context, observer);
        }
        return instance;
    }

    public WebRTCClient(Context context, PeerConnection.Observer observer) {
        this.firebaseClient = new FirebaseClient();
        this.connectionObserver = observer;
        this.context = context.getApplicationContext();
        AudioManager audioManager = (AudioManager) this.context.getSystemService(Context.AUDIO_SERVICE);
        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        audioManager.setSpeakerphoneOn(true);
        initPeerConnectionFactory();
        peerConnectionFactory = createPeerConnectionFactory();
        iceServer.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
        iceServer.add(PeerConnection.IceServer.builder("turn:a.relay.metered.ca:443?transport=tcp")
                .setUsername("83eebabf8b4cce9d5dbcb649")
                .setPassword("2D7JvfkOQtBdYW3R").createIceServer());
        peerConnection = createPeerConnection(connectionObserver);
        localVideoSource = peerConnectionFactory.createVideoSource(false);
        localAudioSource = peerConnectionFactory.createAudioSource(new MediaConstraints());
        mediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo","true"));
        mediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
    }

    //initializing peer connection section
    private void initPeerConnectionFactory() {
        Log.d("RaviKumar-WebRTC", "initPeerConnectionFactory");
        PeerConnectionFactory.InitializationOptions options = PeerConnectionFactory.InitializationOptions.builder(context)
                .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
                .setEnableInternalTracer(true).createInitializationOptions();
        PeerConnectionFactory.initialize(options);
    }

    private PeerConnectionFactory createPeerConnectionFactory() {
        Log.d("RaviKumar-WebRTC", "createPeerConnectionFactory");
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        options.disableEncryption = false;
        options.disableNetworkMonitor = false;
        return PeerConnectionFactory.builder()
                .setVideoEncoderFactory(new DefaultVideoEncoderFactory(eglBaseContext,true,true))
                .setVideoDecoderFactory(new DefaultVideoDecoderFactory(eglBaseContext))
                .setOptions(options)
                .createPeerConnectionFactory();
    }

    private PeerConnection createPeerConnection(PeerConnection.Observer observer){
        Log.d("RaviKumar-WebRTC", "createPeerConnection");
        PeerConnection.RTCConfiguration config = new PeerConnection.RTCConfiguration(iceServer);
        config.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        return peerConnectionFactory.createPeerConnection(config, observer);
    }

    public void resetConnection(Runnable onReady) {
        cleanupBeforeSwitchingStreamMode(() -> {
            closePeerConnection();

            // Recreate connection and only then proceed
            peerConnection = createPeerConnection(connectionObserver);

            if (onReady != null) onReady.run();
        });
    }

    public void startLocalVideoStreaming() {
        Log.d("WebRTCClient", "startLocalVideoStreaming");
        SurfaceTextureHelper helper = SurfaceTextureHelper.create("CaptureThread", eglBaseContext);
        videoCapturer = getVideoCapturer();
        if (videoCapturer == null) {
            Log.e("WebRTCClient", "VideoCapturer is null");
            return;
        }
        localVideoSource = peerConnectionFactory.createVideoSource(videoCapturer.isScreencast());
        videoCapturer.initialize(helper, context, localVideoSource.getCapturerObserver());

        try {
            videoCapturer.startCapture(1280, 720, 30);
        } catch (Exception e) {
            Log.e("WebRTCClient", "startCapture failed", e);
            return;
        }

        localVideoTrack = peerConnectionFactory.createVideoTrack(localTrackId + "_video", localVideoSource);
        localVideoTrack.setEnabled(true);
        isVideoEnabled = true;
        isAudioEnabled = false;
        MediaConstraints audioConstraints = new MediaConstraints();
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googAutoGainControl", "true"));
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googEchoCancellation", "true"));
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googNoiseSuppression", "true"));
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googHighpassFilter", "true"));
        localAudioSource = peerConnectionFactory.createAudioSource(audioConstraints);
        localAudioTrack = peerConnectionFactory.createAudioTrack(localTrackId + "_audio", localAudioSource);
        localAudioTrack.setEnabled(false);
        peerConnection.addTrack(localAudioTrack);
        peerConnection.addTrack(localVideoTrack);
    }


    private CameraVideoCapturer getVideoCapturer() {
        Camera2Enumerator enumerator = new Camera2Enumerator(context);

        String[] deviceNames = enumerator.getDeviceNames();

        for (String device: deviceNames){
            if (enumerator.isFrontFacing(device)){
                return enumerator.createCapturer(device,null);
            }
        }
        throw new IllegalStateException("front facing camera not found");
    }

    public void call(String target){
        try{
            peerConnection.createOffer(new MySdpObserver(){
                @Override
                public void onCreateSuccess(SessionDescription sessionDescription) {
                    super.onCreateSuccess(sessionDescription);
                    peerConnection.setLocalDescription(new MySdpObserver(){
                        @Override
                        public void onSetSuccess() {
                            super.onSetSuccess();
                            firebaseClient.sendMessageToOtherUser(new DataModel(target, username, sessionDescription.description, DataModelType.Offer),
                                    err -> Log.e("RaviKumar-WebRTC", "call error- data not sent, " + err));
                        }
                    },sessionDescription);
                }
            },mediaConstraints);
        }catch (Exception e){
            Log.e("WebRTCClient", "Error in call", e);
        }
    }

    public void answer(String target){
        try{
            peerConnection.createAnswer(new MySdpObserver(){
                @Override
                public void onCreateSuccess(SessionDescription sessionDescription) {
                    super.onCreateSuccess(sessionDescription);
                    peerConnection.setLocalDescription(new MySdpObserver(){
                        @Override
                        public void onSetSuccess() {
                            super.onSetSuccess();
                            //its time to transfer this sdp to other peer
                            firebaseClient.sendMessageToOtherUser(new DataModel(target, username, sessionDescription.description, DataModelType.Answer),
                                    err -> Log.e("RaviKumar-WebRTC", "call error- data not sent" + err));
                        }
                    },sessionDescription);
                }
            },mediaConstraints);
        }catch (Exception e){
            Log.e("WebRTCClient", "Error in answer", e);
        }
    }

    public void startScreenShareFromIntent(Intent mediaProjectionPermissionData) {
        Log.d("RaviKumar-WebRTC", "startScreenShareFromIntent");

        videoCapturer = new ScreenCapturerAndroid(mediaProjectionPermissionData, new MediaProjection.Callback() {
            @Override
            public void onStop() {
                Log.d("RaviKumar-WebRTC", "Screen sharing stopped by system");
                isScreenSharing = false;
            }
        });

        SurfaceTextureHelper helper = SurfaceTextureHelper.create("ScreenCaptureThread", eglBaseContext);
        localVideoSource = peerConnectionFactory.createVideoSource(true);
        videoCapturer.initialize(helper, context, localVideoSource.getCapturerObserver());

        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());

        boolean isWifi = caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);

        try {
            if (isWifi) {
                videoCapturer.startCapture(1920, 1080, 25);
            } else {
                videoCapturer.startCapture(1280, 720, 20);
            }
        } catch (Exception e) {
            Log.e("RaviKumar-WebRTC", "Screen capture start failed", e);
            return;
        }

        localVideoTrack = peerConnectionFactory.createVideoTrack(localTrackId + "_video", localVideoSource);
        localVideoTrack.setEnabled(true);

        MediaConstraints audioConstraints = new MediaConstraints();
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googAutoGainControl", "true"));
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googEchoCancellation", "true"));
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googNoiseSuppression", "true"));
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googHighpassFilter", "true"));
        localAudioSource = peerConnectionFactory.createAudioSource(audioConstraints);
        localAudioTrack = peerConnectionFactory.createAudioTrack(localTrackId + "_audio", localAudioSource);
        localAudioTrack.setEnabled(false);

        peerConnection.addTrack(localAudioTrack);
        peerConnection.addTrack(localVideoTrack);
        Log.d("RaviKumar-WebRTC", "Screen capture started");
        isScreenSharing = true;
        isVideoEnabled = true;
        isAudioEnabled = false;
    }


    public void stopScreenShare() {
        if (!isScreenSharing) return;

        if (videoCapturer != null && videoCapturer instanceof ScreenCapturerAndroid) {
            try {
                videoCapturer.stopCapture();
            } catch (InterruptedException e) {
                Log.e("WebRTCClient", "stopCapture failed", e);
            }
            videoCapturer.dispose();
            videoCapturer = null;
        }
        isScreenSharing = false;
    }



    public void onRemoteSessionReceived(SessionDescription sessionDescription){
        peerConnection.setRemoteDescription(new MySdpObserver(),sessionDescription);
    }

    public void addIceCandidate(IceCandidate iceCandidate){
        peerConnection.addIceCandidate(iceCandidate);
    }

    public void sendIceCandidate(IceCandidate iceCandidate, String target){
        addIceCandidate(iceCandidate);
        firebaseClient.sendMessageToOtherUser(new DataModel(target, username, gson.toJson(iceCandidate), DataModelType.IceCandidate),
                err -> Log.e("RaviKumar-WebRTC", "sendIceCandidate error- data not sent" + err));
    }

    public void switchCamera() {
        if (!isScreenSharing && videoCapturer instanceof CameraVideoCapturer) {
            ((CameraVideoCapturer) videoCapturer).switchCamera(null);
        } else {
            Log.w("WebRTCClient", "switchCamera() called but not in camera mode or capturer is not CameraVideoCapturer");
        }
        firebaseClient.resetTrigger("switchCamera", false);
    }

    public void toggleVideo(){
        isVideoEnabled = !isVideoEnabled;
        if (localVideoTrack!=null){
            localVideoTrack.setEnabled(isVideoEnabled);
        }
        firebaseClient.resetTrigger("toggleVideo", false);

    }

    public void toggleAudio(){
        isAudioEnabled = !isAudioEnabled;
        if (localAudioTrack!=null){
            localAudioTrack.setEnabled(isAudioEnabled);
        }
        firebaseClient.resetTrigger("toggleAudio", false);
    }

    public void cleanupBeforeSwitchingStreamMode(Runnable onReady) {
        Log.d("RaviKumar-WebRTC", "Cleaning up all tracks and sources before switching mode or after disconnection");

        // 1. Stop and dispose video capturer (camera or screen)
        if (videoCapturer != null) {
            try {
                videoCapturer.stopCapture();
            } catch (InterruptedException e) {
                Log.e("WebRTCClient", "stopCapture failed", e);
            }
            videoCapturer.dispose();
            videoCapturer = null;
        }

        // 2. Remove tracks from MediaStream BEFORE disposing them
        if (localStream != null) {
            try {
                if (localVideoTrack != null && localStream.videoTracks.contains(localVideoTrack)) {
                    localStream.removeTrack(localVideoTrack);
                }
                if (localAudioTrack != null && localStream.audioTracks.contains(localAudioTrack)) {
                    localStream.removeTrack(localAudioTrack);
                }
            } catch (IllegalStateException e) {
                Log.w("RaviKumar-WebRTC", "Track already disposed or invalid: " + e.getMessage());
            }
        }

        // 3. Remove localStream from PeerConnection
        if (peerConnection != null && localStream != null) {
            peerConnection.removeStream(localStream); // Safe to call even if not previously added
        }

        // 4. Dispose local tracks
        if (localVideoTrack != null) {
            localVideoTrack.setEnabled(false);
            localVideoTrack.dispose();
            localVideoTrack = null;
        }

        if (localAudioTrack != null) {
            localAudioTrack.setEnabled(false);
            localAudioTrack.dispose();
            localAudioTrack = null;
        }

        // 5. Dispose sources
        if (localVideoSource != null) {
            localVideoSource.dispose();
            localVideoSource = null;
        }

        if (localAudioSource != null) {
            localAudioSource.dispose();
            localAudioSource = null;
        }

        // 6. Null out local stream
        localStream = null;
        if (onReady!=null) onReady.run();
    }

    public void closePeerConnection(){
        if (peerConnection!=null){
            peerConnection.close();
            peerConnection = null;
            firebaseClient.showErrorRemotely("Peer connection closed", "MessageToParent");
        }
    }


    public void startAudioOnly() {
        Log.d("WebRTCClient", "startAudioOnlyStreaming");
        MediaConstraints audioConstraints = new MediaConstraints();
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googAutoGainControl", "true"));
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googEchoCancellation", "true"));

        localAudioSource = peerConnectionFactory.createAudioSource(audioConstraints);
        localAudioTrack = peerConnectionFactory.createAudioTrack(localTrackId + "_audio", localAudioSource);

        peerConnection.addTrack(localAudioTrack);
        isVideoEnabled = false;
        isAudioEnabled = true;
    }

    public static void destroyInstance() {
        if (instance != null) {
            instance.closePeerConnection();
            Log.d("RaviKumar-WebRTCClient", "peerConnection closed");
            instance = null;
        }
    }
}

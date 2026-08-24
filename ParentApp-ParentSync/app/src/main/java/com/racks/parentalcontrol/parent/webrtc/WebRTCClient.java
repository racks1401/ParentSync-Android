package com.racks.parentalcontrol.parent.webrtc;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.racks.parentalcontrol.parent.remote.FirebaseClient;
import com.racks.parentalcontrol.parent.models.DataModel;
import com.racks.parentalcontrol.parent.models.DataModelType;
import com.racks.parentalcontrol.parent.interfaces.ErrorCallBack;
import com.racks.parentalcontrol.parent.utils.MySharedPreferences;

import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceViewRenderer;

import java.util.ArrayList;
import java.util.List;

public class WebRTCClient {

    private final Gson gson = new Gson();
    private final Context context;
    private EglBase.Context eglBaseContext= EglBase.create().getEglBaseContext();
    private PeerConnectionFactory peerConnectionFactory;
    private PeerConnection peerConnection;
    private List<PeerConnection.IceServer> iceServer = new ArrayList<>();

    private final String username = "parent";
    private final MediaConstraints mediaConstraints = new MediaConstraints();
    private final FirebaseClient firebaseClient;
    private PeerConnection.Observer connectionObserver;
    public WebRTCClient(Context context, PeerConnection.Observer observer) {
        this.context = context;
        this.firebaseClient = new FirebaseClient(new MySharedPreferences(context));
        this.connectionObserver = observer;
        initPeerConnectionFactory();
        peerConnectionFactory = createPeerConnectionFactory();
        iceServer.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
        iceServer.add(PeerConnection.IceServer.builder("turn:a.relay.metered.ca:443?transport=tcp")
                .setUsername("83eebabf8b4cce9d5dbcb649")
                .setPassword("2D7JvfkOQtBdYW3R").createIceServer());
        peerConnection = createPeerConnection(connectionObserver);
        mediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo","true"));
        mediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));

    }

    //initializing peer connection section
    private void initPeerConnectionFactory() {
        PeerConnectionFactory.InitializationOptions options = PeerConnectionFactory.InitializationOptions.builder(context)
                .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
                .setEnableInternalTracer(true).createInitializationOptions();
        PeerConnectionFactory.initialize(options);
    }

    private PeerConnectionFactory createPeerConnectionFactory() {
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        options.disableEncryption = false;
        options.disableNetworkMonitor = false;
        return PeerConnectionFactory.builder()
                .setVideoEncoderFactory(new DefaultVideoEncoderFactory(eglBaseContext,true,true))
                .setVideoDecoderFactory(new DefaultVideoDecoderFactory(eglBaseContext))
                .setOptions(options)
                .createPeerConnectionFactory();
    }

    private PeerConnection createPeerConnection(PeerConnection.Observer observer) {
        PeerConnection.RTCConfiguration config = new PeerConnection.RTCConfiguration(iceServer);
        config.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        return peerConnectionFactory.createPeerConnection(config, observer);
    }
    public void createConnection() {
        peerConnection = createPeerConnection(connectionObserver);
    }

    public void initSurfaceViewRenderer(SurfaceViewRenderer viewRenderer) {
        if (viewRenderer == null) {
            Log.e("RaviKumar-WebRTCClient", "SurfaceViewRenderer is null");
            return;
        }

        viewRenderer.setEnableHardwareScaler(true);
        viewRenderer.setMirror(false); // Typically used for local camera preview
        if (eglBaseContext != null) {
            viewRenderer.init(eglBaseContext, null);
        } else {
            Log.e("RaviKumar-WebRTCClient", "eglBaseContext is null");
        }
    }


    public void initRemoteSurfaceView(SurfaceViewRenderer view){
        initSurfaceViewRenderer(view);
    }

    // for child side code
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
                            Log.d("RaviKumar-WebRTC", "call-onsetSuccess"+ sessionDescription.description);
                            //its time to transfer this sdp to other peer
                            firebaseClient.sendMessageToOtherUser(new DataModel(target,username,sessionDescription.description, DataModelType.Offer), new ErrorCallBack() {
                                @Override
                                public void onError(String err) {
                                    Log.e("RaviKumar-WebRTCClient", "sendIceCandidate-err"+err);
                                }
                            });
                        }
                    },sessionDescription);
                }
            },mediaConstraints);
        }catch (Exception e){
            e.printStackTrace();
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
                            firebaseClient.sendMessageToOtherUser(new DataModel(target,username,sessionDescription.description, DataModelType.Answer), new ErrorCallBack() {
                                @Override
                                public void onError(String err) {
                                    Log.e("RaviKumar-WebRTCClient", "sendIceCandidate-err"+err);
                                }
                            });
                        }
                    },sessionDescription);
                }
            },mediaConstraints);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public void onRemoteSessionReceived(SessionDescription sessionDescription){
        peerConnection.setRemoteDescription(new MySdpObserver(),sessionDescription);
    }

    public void addIceCandidate(IceCandidate iceCandidate){
        peerConnection.addIceCandidate(iceCandidate);
    }

    public void sendIceCandidate(IceCandidate iceCandidate, String target){
        addIceCandidate(iceCandidate);
        firebaseClient.sendMessageToOtherUser(new DataModel(target, username, gson.toJson(iceCandidate), DataModelType.IceCandidate), new ErrorCallBack() {
            @Override
            public void onError(String err) {
                Log.e("RaviKumar-WebRTCClient", "sendIceCandidate-err"+err);
            }
        });
    }
    public void closeConnection() {
        Log.d("RaviKumar-WebRTCClient", "Cleaning up WebRTCClient...");
        try {
            // Close peer connection
            if (peerConnection != null) {
                peerConnection.close();
                peerConnection = null;
                Log.d("RaviKumar-WebRTCClient", "Cleaning up done...");

            }

        } catch (Exception e) {
            Log.e("RaviKumar-WebRTCClient", "Error during cleanup: " + e.getMessage());
        }
    }
    public void releaseRenderer(SurfaceViewRenderer viewRenderer) {
        if (viewRenderer != null) {
            viewRenderer.release();
            viewRenderer.clearImage();
            Log.d("RaviKumar-WebRTCClient", "Renderer released...");
        }
    }
}

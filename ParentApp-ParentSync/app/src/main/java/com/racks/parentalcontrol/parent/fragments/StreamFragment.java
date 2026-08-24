package com.racks.parentalcontrol.parent.fragments;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.racks.parentalcontrol.parent.R;
import com.racks.parentalcontrol.parent.activities.MainActivity;
import com.racks.parentalcontrol.parent.databinding.FragmentStreamBinding;
import com.racks.parentalcontrol.parent.remote.FirebaseClient;
import com.racks.parentalcontrol.parent.services.MyForegroundService;
import com.racks.parentalcontrol.parent.utils.MySharedPreferences;
import com.racks.parentalcontrol.parent.models.StreamMode;

import org.webrtc.AudioTrack;
import org.webrtc.MediaStream;
import org.webrtc.RendererCommon;
import org.webrtc.VideoTrack;

public class StreamFragment extends Fragment {

    private FragmentStreamBinding binding;
    private MyForegroundService callService;
    private StreamMode streamMode;
    private boolean isVideoEnabled = false;
    private boolean isAudioEnabled = false;

    private VideoTrack lastVideoTrack = null;
    private AudioTrack lastAudioTrack = null;
    private DatabaseReference dbRef = FirebaseDatabase.getInstance().getReference();
    private DatabaseReference msgRef;
    private ValueEventListener msgListener;
    private boolean isSenderUsingFrontCamera = true;

    private static final String TAG = "RaviKumar-StreamFragment";

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            StreamFragmentArgs args = StreamFragmentArgs.fromBundle(getArguments());
            streamMode = args.getStreamMode();
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentStreamBinding.inflate(inflater, container, false);
        showConnectingView();
        listenForChildMessage();
        createConnection();
        initSurfaceViewRenderer();
        initViewListener();
        return binding.getRoot();
    }

    private void listenForChildMessage() {
        FirebaseClient firebaseClient = new FirebaseClient(new MySharedPreferences(requireActivity()));
        MySharedPreferences sharedPreferences = new MySharedPreferences(requireActivity());
        msgRef = dbRef.child(firebaseClient.getAuthUID()).child("Children").child(sharedPreferences.getDefaultDevice()).child("ConnectionError").child("message");
        msgListener = msgRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()){
                    String message = snapshot.getValue(String.class);
                    binding.connectingLottie.pauseAnimation();
                    binding.tvConnectingMsg.setText(message);
                    msgRef.setValue(null);
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });

    }

    private void initViewListener() {
        binding.speakerButtonStream.setOnClickListener(view->{
            if (callService!=null) {
                if (!isAudioEnabled){
                    binding.speakerButtonStream.setImageResource(R.drawable.speaker_on_24);
                }else{
                    binding.speakerButtonStream.setImageResource(R.drawable.speaker_off_24);
                }
                callService.toggleAudio();
                isAudioEnabled = !isAudioEnabled;
            }

        });
        binding.videoButtonStream.setOnClickListener(view->{
            if (callService!=null) {
                if (!isVideoEnabled){
                    binding.videoButtonStream.setImageResource(R.drawable.ic_baseline_videocam_24);
                }else{
                    binding.videoButtonStream.setImageResource(R.drawable.ic_baseline_videocam_off_24);
                }
                callService.toggleVideo();
                isVideoEnabled = !isVideoEnabled;
            }

        });

        binding.endCallButtonStream.setOnClickListener(view->{
            Log.d(TAG, "end called");
            if (callService!=null) {
                Log.d(TAG, "service is not null");
                callService.endCall();
            }
        });

        binding.switchCameraButtonStream.setOnClickListener(view->{
            if (callService!=null) {
                callService.switchCamera();
                isSenderUsingFrontCamera = !isSenderUsingFrontCamera;
                binding.remoteViewCallStream.setMirror(isSenderUsingFrontCamera);
            }
        });
    }


    private void createConnection() {
        if (callService!=null){
            callService.createConnection();
        }
    }

    private void initSurfaceViewRenderer() {
        if (streamMode.equals(StreamMode.CAMERA)){
            callService.initRemoteView(binding.remoteViewCallStream);
        } else if (streamMode.equals(StreamMode.SCREEN)) {
            callService.initRemoteView(binding.remoteViewCallStream);
            binding.videoButtonStream.setVisibility(View.GONE);
            binding.switchCameraButtonStream.setVisibility(View.GONE);

        } else if (streamMode.equals(StreamMode.AUDIO_ONLY)) {
            callService.releaseRenderer(binding.remoteViewCallStream);
            binding.videoButtonStream.setVisibility(View.GONE);
            binding.switchCameraButtonStream.setVisibility(View.GONE);
        }
        startCall();
    }
    private void startCall() {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (callService!=null) {
                callService.sendCallRequest("child", "parent", streamMode, err -> binding.tvConnectingMsg.setText(err));
            }
        }, 1000);
    }

    private void showConnectingView() {
        binding.llConnectingView.setVisibility(View.VISIBLE);
        binding.rlVideoCallLayout.setVisibility(View.GONE);
        binding.tvConnectingMsg.setText("Connecting please wait...");
        binding.connectingLottie.playAnimation();
    }

    @Override
    public void onResume() {
        super.onResume();
        MyForegroundService.setListener(new MyForegroundService.Listener() {

            @Override
            public void webrtcConnected() {
                Log.d(TAG, "webrtcConnected");
                requireActivity().runOnUiThread(() -> {
                    binding.rlVideoCallLayout.setVisibility(View.VISIBLE);
                    binding.llConnectingView.setVisibility(View.GONE);
                });
            }

            @Override
            public void webrtcClosed() {
                Log.d(TAG, "webrtcClosed");
                if (isAdded() && getContext() != null) {
                    requireActivity().runOnUiThread(() -> {
                        if (lastVideoTrack != null) {
                            lastVideoTrack.removeSink(binding.remoteViewCallStream);
                            Log.d(TAG, "Removed sink from video track");
                        }
                        binding.remoteViewCallStream.clearImage();
                        lastVideoTrack = null;
                        lastAudioTrack = null;
                        NavHostFragment.findNavController(StreamFragment.this).popBackStack();
                    });
                }
            }

            @Override
            public void onRemoteVideoTrack(VideoTrack videoTrack) {
                Log.d(TAG, "onRemoteVideoTrack");
                requireActivity().runOnUiThread(() -> {
                    lastVideoTrack = videoTrack;
                    isVideoEnabled = true;
                    isAudioEnabled = false;
                    videoTrack.addSink(binding.remoteViewCallStream);
                    configureRendererForMode(streamMode);
                    binding.rlVideoCallLayout.setVisibility(View.VISIBLE);
                    binding.llConnectingView.setVisibility(View.GONE);
                    updateToggleIconState(); // use this centralized method
                });
            }


            @Override
            public void onRemoteAudioTrack(AudioTrack audioTrack) {
                Log.d(TAG, "onRemoteAudioTrack");
                requireActivity().runOnUiThread(() -> {
                    lastAudioTrack = audioTrack;
                    isAudioEnabled = audioTrack.enabled();
                    configureRendererForMode(StreamMode.AUDIO_ONLY);
                    updateToggleIconState();
                });
            }

        });

    }
    private void updateToggleIconState() {
        binding.speakerButtonStream.setImageResource(isAudioEnabled ? R.drawable.speaker_on_24 : R.drawable.speaker_off_24);
        binding.videoButtonStream.setImageResource(isVideoEnabled ? R.drawable.ic_baseline_videocam_24 : R.drawable.ic_baseline_videocam_off_24);
    }

    private void configureRendererForMode(StreamMode mode) {
        switch (mode) {
            case CAMERA:
                Log.d("RaviKumar-StreamFragment", mode.toString());
                binding.audioWaveView.setVisibility(View.GONE);
                binding.remoteViewCallStream.setMirror(true);
                binding.remoteViewCallStream.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
                break;
            case SCREEN:
                Log.d("RaviKumar-StreamFragment", mode.toString());
                binding.audioWaveView.setVisibility(View.GONE);
                binding.remoteViewCallStream.setMirror(false);
                binding.remoteViewCallStream.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
                break;
            case AUDIO_ONLY:
                Log.d("RaviKumar-StreamFragment", mode.toString());
                binding.audioWaveView.setVisibility(View.VISIBLE);
                binding.remoteViewCallStream.clearImage();
                break;
        }
        binding.remoteViewCallStream.requestLayout();
    }


    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof MainActivity) {
            ((MainActivity) context).setOnServiceReadyListener(service -> {
                callService = service;
            });
        }
    }
    @Override
    public void onDetach() {
        super.onDetach();
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setOnServiceReadyListener(null);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        msgRef.removeEventListener(msgListener);
    }
}
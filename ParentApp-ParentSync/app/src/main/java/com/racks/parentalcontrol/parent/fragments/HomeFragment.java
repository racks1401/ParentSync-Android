package com.racks.parentalcontrol.parent.fragments;

import android.Manifest;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavDirections;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.permissionx.guolindev.PermissionX;
import com.racks.parentalcontrol.parent.R;
import com.racks.parentalcontrol.parent.adapters.ChildAdapter;
import com.racks.parentalcontrol.parent.databinding.FragmentHomeBinding;
import com.racks.parentalcontrol.parent.interfaces.ErrorCallBack;
import com.racks.parentalcontrol.parent.interfaces.LocationCallBack;
import com.racks.parentalcontrol.parent.interfaces.NewEventCallBack;
import com.racks.parentalcontrol.parent.models.DataModel;
import com.racks.parentalcontrol.parent.remote.FirebaseClient;
import com.racks.parentalcontrol.parent.models.ChildDetailModel;
import com.racks.parentalcontrol.parent.models.ChildDetailViewModel;
import com.racks.parentalcontrol.parent.models.LocationModel;
import com.racks.parentalcontrol.parent.utils.MySharedPreferences;
import com.racks.parentalcontrol.parent.interfaces.NewEventListCallback;
import com.racks.parentalcontrol.parent.models.StreamMode;

import java.util.ArrayList;

public class HomeFragment extends Fragment implements OnMapReadyCallback {
    private GoogleMap mMap;

    private FirebaseClient firebaseClient;
    private Marker marker;
    private MySharedPreferences mySharedPreferences;
    private Dialog snapshotdialog;
    private TextView tv_front_snap, tv_rearORscreen_snap;
    private View view_snapshot;
    private String fcmToken;
    private boolean isChildOnline = false;
    private FragmentHomeBinding binding;
    private ChildDetailViewModel childDetailViewModel;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        
        mySharedPreferences = new MySharedPreferences(requireContext());
        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager()
                .findFragmentById(R.id.map_view_map);
        firebaseClient = new FirebaseClient(mySharedPreferences);
        childDetailViewModel = new ViewModelProvider(requireActivity()).get(ChildDetailViewModel.class);
        checkForPermission();
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
        updateInitialUI();
        init();
        return binding.getRoot();

    }
    private void init(){
        createSnapshotDialog();
        setChildDetailObserver();
        binding.imgSelectChild.setOnClickListener(view->{
            showChildSelectionDialog();
        });
        binding.imgRemoteCameraBtn.setOnClickListener(view -> {
            if (isChildOnline){
                startCall(StreamMode.CAMERA);

            }else{
                showToast("Child is not online");
            }

        });
        binding.imgScreenMirrorBtn.setOnClickListener(view->{
            if (isChildOnline) {
                startCall(StreamMode.SCREEN);
            }else{
                showToast("Child is not online");
            }
        });
        binding.imgRemoteAudioBtn.setOnClickListener(view->{
            if (isChildOnline) {
                startCall(StreamMode.AUDIO_ONLY);
            }else {
                showToast("Child is not online");
            }

        });
        binding.imgCameraSnapBtn.setOnClickListener(view -> {
            tv_front_snap.setVisibility(View.VISIBLE);
            view_snapshot.setVisibility(View.VISIBLE);
            tv_front_snap.setText("Front Camera Snapshot");
            tv_rearORscreen_snap.setText("Rear Camera Snapshot");
            snapshotdialog.show();
        });

        binding.imgScreenSnapBtn.setOnClickListener(view -> {
            tv_front_snap.setVisibility(View.GONE);
            view_snapshot.setVisibility(View.GONE);
            tv_rearORscreen_snap.setText("Take Screen Snapshot");
            snapshotdialog.show();
        });
        binding.rlManageAdditionalSettings.setOnClickListener(view -> {
            NavDirections directions = HomeFragmentDirections.actionHomeFragmentToAdditionalSettingsFragment();
            NavHostFragment.findNavController(HomeFragment.this).navigate(directions);
        });

        binding.btnTurnDeviceOnOff.setOnClickListener(view -> {
            if (mySharedPreferences.getDefaultDevice() == null) {
                showToast("Please select a device first.");
                return;
            }

            if (fcmToken == null || fcmToken.isEmpty()) {
                showToast("Unable to find device token. Please make sure the child device is set up and online.");
                return;
            }

            if (!isChildOnline) {
                binding.btnTurnDeviceOnOff.setEnabled(false);
                binding.btnTurnDeviceOnOff.setText("Starting the service, please wait..");
                Log.d("Ravi Kumar Homefragment", fcmToken);
                firebaseClient.sendFcmToChild(fcmToken, "START_SERVICE", () -> {
                }, err -> requireActivity().runOnUiThread(() -> {
                    binding.btnTurnDeviceOnOff.setText("Error, Try again");
                    showToast(err);
                    binding.btnTurnDeviceOnOff.setEnabled(true);
                }));
            } else {
                binding.btnTurnDeviceOnOff.setEnabled(false);
                Log.d("Ravi Kumar Homefragment", fcmToken);
                binding.btnTurnDeviceOnOff.setText("Stoping the service, please wait..");
                firebaseClient.sendFcmToChild(fcmToken, "STOP_SERVICE", () -> {
                }, err -> requireActivity().runOnUiThread(() -> {
                    binding.btnTurnDeviceOnOff.setText("Error, Try again");
                    showToast(err);
                    binding.btnTurnDeviceOnOff.setEnabled(true);
                }));
            }
        });

    }
    private void startCall(StreamMode streamMode){
        NavDirections navDirections = HomeFragmentDirections.actionHomeFragmentToStreamFragment(streamMode);
        NavHostFragment.findNavController(this).navigate(navDirections);
    }
    private void createSnapshotDialog() {
        snapshotdialog = new Dialog(requireActivity());
        snapshotdialog.setContentView(R.layout.choose_camera_snapshot_dialog);
        if (snapshotdialog.getWindow() != null) {
            snapshotdialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        snapshotdialog.setCancelable(true);
        tv_front_snap = snapshotdialog.findViewById(R.id.tv_front_snap);
        tv_rearORscreen_snap = snapshotdialog.findViewById(R.id.tv_rear_snap);
        view_snapshot = snapshotdialog.findViewById(R.id.view_snapshot);

        tv_front_snap.setOnClickListener(view -> {
            if (isChildOnline) {
                tv_front_snap.setEnabled(false);
                tv_rearORscreen_snap.setEnabled(false);
                snapshotdialog.setCancelable(false);
                tv_front_snap.setText("Taking Snapshot, please wait..");
                firebaseClient.createTrigger("frontSnapshot", true, () -> {
                }, err -> {
                    showToast(err);
                    tv_front_snap.setEnabled(false);
                    tv_rearORscreen_snap.setEnabled(false);
                    snapshotdialog.setCancelable(false);
                });
            } else {
                showToast("Child is not online");
            }
        });

        tv_rearORscreen_snap.setOnClickListener(view -> {
            if (isChildOnline) {
                tv_front_snap.setEnabled(false);
                tv_rearORscreen_snap.setEnabled(false);
                snapshotdialog.setCancelable(false);
                String currentLabel = tv_rearORscreen_snap.getText().toString();
                if (currentLabel.contains("Rear")) {
                    tv_rearORscreen_snap.setText("Taking Snapshot, please wait..");
                    firebaseClient.createTrigger("rearSnapshot", true, () -> {
                    }, err -> {
                        showToast(err);
                        tv_front_snap.setEnabled(true);
                        tv_rearORscreen_snap.setEnabled(true);
                        snapshotdialog.setCancelable(true);
                    });
                } else if (currentLabel.contains("Screen")) {
                    tv_rearORscreen_snap.setText("Taking Snapshot, please wait..");
                    firebaseClient.createTrigger("screenSnapshot", true, () -> {
                    }, err->{
                        showToast(err);
                        tv_front_snap.setEnabled(true);
                        tv_rearORscreen_snap.setEnabled(true);
                        snapshotdialog.setCancelable(true);
                    });
                }
            } else {
                showToast("Child is not online");
            }
        });
    }
    private void setChildDetailObserver() {
            childDetailViewModel.getChildDetail().observe(getViewLifecycleOwner(), childDetailModel -> {
                if (childDetailModel != null) {
                    Log.d("RaviKumar-HomeFragment", "childDetailModel is not null");
                    binding.llStatusBattery.setVisibility(View.VISIBLE);
                    binding.deviceNameMain.setText(childDetailModel.getDevice_model());
                    if (childDetailModel.getLast_online().equals("online")) {
                        binding.onlineStatusMain.setText(childDetailModel.getLast_online());
                        isChildOnline = true;
                        binding.btnTurnDeviceOnOff.setText("Stop child location service");
                    } else {
                        String lastOnline = getLastOnlineString(childDetailModel.getLast_online());
                        binding.onlineStatusMain.setText(lastOnline);
                        isChildOnline = false;
                        binding.btnTurnDeviceOnOff.setText("Start child location service");
                    }
                    binding.btnTurnDeviceOnOff.setEnabled(true);
                    int battery_perc = 0;
                    try {
                        battery_perc = Integer.parseInt(childDetailModel.getBattery_percentage());
                        binding.tvBatteryStatusMain.setText(battery_perc + "%");
                    } catch (Exception e) {
                        e.printStackTrace();
                        binding.tvBatteryStatusMain.setText(battery_perc + "%");
                    }

                    if (battery_perc > 75) {
                        binding.igBatteryIconMain.setImageResource(R.drawable.battery_100_green);
                    } else if (battery_perc > 50) {
                        binding.igBatteryIconMain.setImageResource(R.drawable.battery_75_green);
                    } else if (battery_perc > 25) {
                        binding.igBatteryIconMain.setImageResource(R.drawable.battery_50_green);
                    } else {
                        binding.igBatteryIconMain.setImageResource(R.drawable.battery_25);
                    }
                    fcmToken = childDetailModel.getFcmToken();
                }else{
                    Log.d("RaviKumar-HomeFragment", "childDetailModel is null");
                    binding.llStatusBattery.setVisibility(View.GONE);
                    binding.deviceNameMain.setText("Please select child first");
                }
            });
    }
    private void updateInitialUI() {
        String defaultDevice = mySharedPreferences.getDefaultDevice();
        if (defaultDevice != null && !defaultDevice.trim().isEmpty()) {
            binding.llStatusBattery.setVisibility(View.VISIBLE);
            binding.deviceNameMain.setText("Fetching child info...");
        } else {
            binding.llStatusBattery.setVisibility(View.GONE);
            binding.deviceNameMain.setText("Please select child first");
        }
    }

    private void showChildSelectionDialog() {
        Dialog dialog = new Dialog(requireActivity());
        dialog.setContentView(R.layout.no_child_dialog);
        if (dialog.getWindow()!=null){
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.setCancelable(false);

        RecyclerView recyclerView = dialog.findViewById(R.id.recycler_view_select_child_dialog);
        TextView tv_cancel = dialog.findViewById(R.id.tv_cancel_select_child_dialog);
        TextView tv_fetching_child = dialog.findViewById(R.id.tv_fetching_child);
        tv_fetching_child.setVisibility(View.VISIBLE);
        tv_cancel.setOnClickListener(view->{
            dialog.dismiss();
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(requireActivity()));
        ArrayList<ChildDetailModel> dialogChildList = new ArrayList<>();
        ChildAdapter adapter = new ChildAdapter(dialogChildList, child -> {
            mySharedPreferences.setDefaultDevice(child.getDeviceId());
            fetchNewChildDetail();
            fetchNewChildLocation();
            dialog.dismiss();
        });
        recyclerView.setAdapter(adapter);
        firebaseClient.fetchAllChild(new NewEventListCallback() {
            @Override
            public void onNewEventListReceived(ArrayList<ChildDetailModel> list) {
                dialogChildList.clear();
                dialogChildList.addAll(list);
                adapter.notifyDataSetChanged();
                tv_fetching_child.setVisibility(View.GONE);
            }

            @Override
            public void onError(String errorMsg) {
                tv_fetching_child.setText(errorMsg+"\nPlease login in child app and wait");
            }
        });
        dialog.setOnDismissListener(dialogInterface -> {
            firebaseClient.removeFetchAllChildListener();
        });
        dialog.show();
    }

    private void fetchNewChildDetail() {
        firebaseClient.fetchChildDetail(new NewEventCallBack() {
            @Override
            public void onNewEventReceived(DataModel model) {
            }
            @Override
            public void onNewEvenReceived(ChildDetailModel childDetailModel) {
                childDetailViewModel.setChildDetail(childDetailModel);
            }
            @Override
            public void onError(String err) {
                Toast.makeText(requireContext(), err, Toast.LENGTH_SHORT).show();
            }
        });
    }

    public String getLastOnlineString(String lastOnlineMillisStr) {
        try {
            long lastOnlineMillis = Long.parseLong(lastOnlineMillisStr);
            long currentMillis = System.currentTimeMillis();
            long diff = currentMillis - lastOnlineMillis;

            long seconds = diff / 1000;
            long minutes = seconds / 60;
            long hours = minutes / 60;
            long days = hours / 24;

            if (seconds < 60) {
                return seconds+" sec ago";
            } else if (minutes < 60) {
                return minutes + " min ago";
            } else if (hours < 24) {
                return hours + " hr ago";
            } else if (days == 1) {
                return "24hr ago";
            } else {
                return days + " days ago";
            }

        } catch (NumberFormatException e) {
            return "Unknown";
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        childDetailViewModel.getChildLocationDetail().observe(getViewLifecycleOwner(), this::displayLocationOnMap);

        googleMap.setOnMapClickListener(latLng -> {
            NavHostFragment.findNavController(this)
                    .navigate(R.id.action_homeFragment_to_mapFragment);
        });
    }
    private void fetchNewChildLocation(){
        firebaseClient.fetchLocation(new LocationCallBack() {
            @Override
            public void onLocationChanged(LocationModel locationModel) {
                childDetailViewModel.setChildLocationDetail(locationModel);
            }
            @Override
            public void onError(String err) {

            }
        });
    }
    private void displayLocationOnMap(LocationModel locationModel) {
        if (mMap != null) {
            Log.d("RaviKumar-MapActivity", "displayLocationOnMap"+locationModel.getLatitude()+", "+locationModel.getLongitude());

            double latitude = locationModel.getLatitude();
            double longitude = locationModel.getLongitude();

            LatLng userLocation = new LatLng(latitude, longitude);
            BitmapDescriptor markerIcon = BitmapDescriptorFactory.fromBitmap(getResizedBitmap(R.drawable.ic_child_location, 85, 85));
            MarkerOptions markerOptions = new MarkerOptions()
                    .position(userLocation)
                    .icon(markerIcon)
                    .title("child");
            if (marker == null) {
                marker = mMap.addMarker(markerOptions);
            } else {
                marker.setPosition(userLocation);
            }
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLocation, 17));
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userLocation, 18f));


        } else {
            showToast("Something went wrong");
        }
    }
    public Bitmap getResizedBitmap(int resourceId, int newWidth, int newHeight) {
        Bitmap originalBitmap = BitmapFactory.decodeResource(getResources(), resourceId);
        return Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, false);

    }

    @Override
    public void onResume() {
        super.onResume();
        firebaseClient.listenForChildMessage(msg -> {
            if (msg.equals("Snapshot Captured") || msg.contains("Camera")){
                if (snapshotdialog.isShowing()){
                    tv_front_snap.setEnabled(true);
                    tv_rearORscreen_snap.setEnabled(true);
                    snapshotdialog.setCancelable(true);
                    snapshotdialog.dismiss();
                }
            }
            if (isAdded() && getContext()!=null){
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
            }
            new Handler(Looper.getMainLooper()).postDelayed(() -> firebaseClient.setMessageToNull(),1000);
        });
        if (mySharedPreferences.getDefaultDevice() != null && !mySharedPreferences.getDefaultDevice().isEmpty()){
            fetchNewChildDetail();
        }
        firebaseClient.resetCallData(err -> Toast.makeText(requireActivity(), err, Toast.LENGTH_SHORT).show());
        firebaseClient.createTrigger("resetConnection", true, ()->{}, err->{
            Toast.makeText(requireContext(), "Connection Reset failed"+err, Toast.LENGTH_SHORT).show();
        });

    }

    @Override
    public void onPause() {
        super.onPause();
        firebaseClient.removeChildMsgListener();
    }

    private void checkForPermission(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionX.init(this)
                    .permissions(Manifest.permission.POST_NOTIFICATIONS)
                    .request((allGranted, grantedList, deniedList) -> {
                        if (!allGranted) {
                            Toast.makeText(requireActivity(), "Notification permission is required", Toast.LENGTH_SHORT).show();
                        }
                    });
        }
    }
    private void showToast(String msg){
        Toast.makeText(requireActivity(), msg, Toast.LENGTH_SHORT).show();
    }
}
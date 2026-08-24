package com.racks.parentalcontrol.parent.activities;

import android.app.ActivityManager;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.airbnb.lottie.LottieAnimationView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.racks.parentalcontrol.parent.R;
import com.racks.parentalcontrol.parent.interfaces.ErrorCallBack;
import com.racks.parentalcontrol.parent.interfaces.LocationCallBack;
import com.racks.parentalcontrol.parent.interfaces.NewEventCallBack;
import com.racks.parentalcontrol.parent.interfaces.OnServiceReadyListener;
import com.racks.parentalcontrol.parent.interfaces.SuccessCallBack;
import com.racks.parentalcontrol.parent.remote.FirebaseClient;
import com.racks.parentalcontrol.parent.services.MyForegroundService;
import com.racks.parentalcontrol.parent.models.ChildDetailModel;
import com.racks.parentalcontrol.parent.models.ChildDetailViewModel;
import com.racks.parentalcontrol.parent.models.DataModel;
import com.racks.parentalcontrol.parent.models.LocationModel;
import com.racks.parentalcontrol.parent.utils.MySharedPreferences;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class MainActivity extends AppCompatActivity{

    private boolean isBound = false;
    private MyForegroundService callService;
    private final FirebaseAuth mAuth = FirebaseAuth.getInstance();
    private OnServiceReadyListener serviceReadyListener;
    private FirebaseClient firebaseClient;
    private Dialog connecting_dialog;
    private ChildDetailViewModel childDetailViewModel;
    private final Set<Integer> fragmentsToHideBottomNav = new HashSet<>(Arrays.asList(
            R.id.mapFragment,
            R.id.streamFragment,
            R.id.fullScreenSnapFragment,
            R.id.additionalSettingsFragment
    ));

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MyForegroundService.LocalBinder binder = (MyForegroundService.LocalBinder) service;
            callService = binder.getService();
            isBound = true;
            if (serviceReadyListener != null) {
                serviceReadyListener.onServiceReady(callService);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        BottomNavigationView bottomNav = findViewById(R.id.nav_bar_main);
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);

        assert navHostFragment != null;
        NavController navController = navHostFragment.getNavController();
        NavigationUI.setupWithNavController(bottomNav, navController);
        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            if (fragmentsToHideBottomNav.contains(destination.getId())) {
                bottomNav.setVisibility(View.GONE);
            } else {
                bottomNav.setVisibility(View.VISIBLE);
            }
        });
        firebaseClient = new FirebaseClient(new MySharedPreferences(this));
        childDetailViewModel = new ViewModelProvider(this).get(ChildDetailViewModel.class);
        createConnectingDialog();
        connecting_dialog.show();
        checkAuthenticity();
    }
    public void setOnServiceReadyListener(OnServiceReadyListener listener) {
        this.serviceReadyListener = listener;

        if (listener != null && isBound && callService != null) {
            listener.onServiceReady(callService);
        }
    }

    private void checkAuthenticity() {
        if (mAuth.getCurrentUser() ==null) {
            mAuth.signOut();
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }
        startAndBindService();
        fetchChildDetailAndLocation();

    }

    private void fetchChildDetailAndLocation() {
        firebaseClient.fetchChildDetail(new NewEventCallBack() {
            @Override
            public void onNewEventReceived(DataModel model) {
            }
            @Override
            public void onNewEvenReceived(ChildDetailModel childDetailModel) {
                childDetailViewModel.setChildDetail(childDetailModel);
                connecting_dialog.dismiss();
            }
            @Override
            public void onError(String err) {
                Toast.makeText(MainActivity.this, err, Toast.LENGTH_SHORT).show();
                connecting_dialog.dismiss();
            }
        });
        firebaseClient.fetchLocation(new LocationCallBack() {
            @Override
            public void onLocationChanged(LocationModel locationModel) {
                childDetailViewModel.setChildLocationDetail(locationModel);
            }

            @Override
            public void onError(String err) {
                Toast.makeText(MainActivity.this, err, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void createConnectingDialog() {
        connecting_dialog = new Dialog(MainActivity.this);
        connecting_dialog.setContentView(R.layout.connecting_dialog);
        if (connecting_dialog.getWindow() != null) {
            connecting_dialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
        connecting_dialog.setCancelable(false);
        LottieAnimationView lottie_connecting = connecting_dialog.findViewById(R.id.lottie_connecting);
        TextView tv_setting_up = connecting_dialog.findViewById(R.id.tv_connecting);
        lottie_connecting.setVisibility(View.GONE);
        tv_setting_up.setText("Connecting please wait...");

    }

    private void startAndBindService() {
        if (!isMyServiceRunning()) {
            Intent serviceIntent = new Intent(this, MyForegroundService.class);
            ContextCompat.startForegroundService(this, serviceIntent);
        }
        bindService(new Intent(this, MyForegroundService.class), connection, BIND_AUTO_CREATE);
    }
    private boolean isMyServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (MyForegroundService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onStart() {
        super.onStart();
    }


    @Override
    protected void onStop() {
        super.onStop();
        if (isBound) {
            unbindService(connection);
            isBound = false;
        }
    }

}
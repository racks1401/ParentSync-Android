package com.racks.parentalcontrol.child.activities;


import static com.racks.parentalcontrol.child.utils.PermissionHelper.isAccessibilityServiceEnabled;
import static com.racks.parentalcontrol.child.utils.PermissionHelper.isBatteryOptimizationIgnored;
import static com.racks.parentalcontrol.child.utils.PermissionHelper.isCallLogsPermissionGranted;
import static com.racks.parentalcontrol.child.utils.PermissionHelper.isCameraPermissionGranted;
import static com.racks.parentalcontrol.child.utils.PermissionHelper.isLocationPermissionGranted;
import static com.racks.parentalcontrol.child.utils.PermissionHelper.isMicrophonePermissionGranted;

import android.Manifest;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.MenuItem;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.FirebaseAuth;
import com.permissionx.guolindev.PermissionX;
import com.racks.parentalcontrol.child.R;
import com.racks.parentalcontrol.child.remote.FirebaseClient;
import com.racks.parentalcontrol.child.services.MyForegroundService;
import com.racks.parentalcontrol.child.utils.Helper;

public class MainActivity extends AppCompatActivity{

    private SwitchMaterial switch_accessibility_permission, switch_ignore_battery_optimization, switch_location_permission,
            switch_microphone_permission, switch_camera_permission, switch_notification_access_permission, switch_call_logs_permission;
    private FirebaseClient firebaseClient;
    private ImageView more_options_main;
    private ContentObserver accessibilityObserver;

    private MyForegroundService myService;
    private boolean isBound = false;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MyForegroundService.LocalBinder binder = (MyForegroundService.LocalBinder) service;
            myService = binder.getService();
            isBound = true;
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
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        firebaseClient = new FirebaseClient();
        checkAuthenticity();
        switch_accessibility_permission = findViewById(R.id.switch_accessibility_permission);
        switch_camera_permission = findViewById(R.id.switch_camera_permission);
        switch_microphone_permission = findViewById(R.id.switch_microphone_permission);
        switch_location_permission = findViewById(R.id.switch_location_permission);
        switch_ignore_battery_optimization = findViewById(R.id.switch_ignore_battery_optimization);
        switch_notification_access_permission = findViewById(R.id.switch_notification_access_permission);
        switch_call_logs_permission = findViewById(R.id.switch_call_logs_permission);
        more_options_main = findViewById(R.id.more_options_main);
        switch_accessibility_permission.setOnCheckedChangeListener((compoundButton, b) -> {
            if (b){
                if (!isAccessibilityServiceEnabled(this)){
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Enable Accessibility")
                            .setMessage("Please enable Accessibility Service for this app.")
                            .setPositiveButton("Go to Settings", (dialog, which) -> {
                                Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                                startActivity(intent);
                            })
                            .setNegativeButton("Cancel", (dialog, which)->{
                                switch_accessibility_permission.setChecked(false);
                            })
                            .show();
                }
            }
        });
        switch_camera_permission.setOnCheckedChangeListener((compoundButton, b) -> {
            if (b) {
                if (!isCameraPermissionGranted(this)){
                    askCameraPermission();
                }
            }
        });
        switch_microphone_permission.setOnCheckedChangeListener((compoundButton, b) -> {
            if (b) {
                if (!isMicrophonePermissionGranted(this)){
                    askMicrophonePermission();
                }
            }
        });
        switch_location_permission.setOnCheckedChangeListener((compoundButton, b) -> {
            if (b) {
                if (!isLocationPermissionGranted(this)){
                    askLocationPermission();
                }
            }
        });
        switch_ignore_battery_optimization.setOnCheckedChangeListener((compoundButton, b) -> {
            if (b) {
                if (!isBatteryOptimizationIgnored(this)){
                    askForIgnoreBatteryOptimization();
                }
            }
        });
        switch_notification_access_permission.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if (b){
                    if (!isNotificationServiceEnabled(MainActivity.this)) {
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle("Enable Notification Access")
                                .setMessage("This app needs notification access to read incoming notifications.")
                                .setPositiveButton("Go to Settings", (dialog, which) -> {
                                    startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                    }
                }
            }
        });
        switch_call_logs_permission.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if (b){
                    if (!isCallLogsPermissionGranted(MainActivity.this)){
                        askCallLogPermission();
                    }
                }
            }
        });


        more_options_main.setOnClickListener(v -> {
            PopupMenu popupMenu = new PopupMenu(MainActivity.this, more_options_main);
            popupMenu.getMenuInflater().inflate(R.menu.more_option_main, popupMenu.getMenu());
            MenuItem showHideIcon = popupMenu.getMenu().findItem(R.id.hide_show_icon);
            if (Helper.isAppIconVisible(MainActivity.this)) {
                showHideIcon.setTitle("Hide App Icon");
            } else {
                showHideIcon.setTitle("Show App Icon");
            }
            MenuItem stopServiceItem = popupMenu.getMenu().findItem(R.id.stop_service);
            if (isMyServiceRunning()) {
                stopServiceItem.setTitle("Stop Service");
            } else {
                stopServiceItem.setTitle("Start Service");
            }
            popupMenu.setOnMenuItemClickListener(menuItem -> {
                int id = menuItem.getItemId();
                if (id == R.id.stop_service){
                    if (isMyServiceRunning()){
                        stopService();
                    }else {
                        if (areAllRequiredPermissionsGranted()){
                            startService();
                        }
                    }
                    return true;
                }
                if (id == R.id.logout){
                    if (firebaseClient.getAuthUID()!=null){
                        stopService();
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            FirebaseAuth mAuth = FirebaseAuth.getInstance();
                            mAuth.signOut();
                            startActivity(new Intent(MainActivity.this, LoginActivity.class));
                            finish();
                        }, 1000);
                    }
                    return true;
                }
                if (id == R.id.hide_show_icon){
                    boolean isVisible = !Helper.isAppIconVisible(MainActivity.this);
                    Helper.setAppIconVisibility(MainActivity.this, isVisible);
                    firebaseClient.resetTrigger("showAppIcon", isVisible);
                    return true;
                }
                return false;
            });
            popupMenu.show();
        });
    }

    private void startService() {
        if (!isMyServiceRunning()) {
            Intent serviceIntent = new Intent(this, MyForegroundService.class);
            ContextCompat.startForegroundService(this, serviceIntent);
        }

        bindToService();
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
    private void stopService(){
        unbindFromService();
        if (isMyServiceRunning()){
            Intent stopIntent = new Intent(MainActivity.this, MyForegroundService.class);
            stopIntent.setAction("STOP_FOREGROUND_SERVICE");
            startService(stopIntent);
        }
    }
    private void bindToService() {
        if (!isBound) {
            Intent intent = new Intent(this, MyForegroundService.class);
            bindService(intent, connection, Context.BIND_AUTO_CREATE);
        }
    }
    private void unbindFromService() {
        if (isBound) {
            unbindService(connection);
            isBound = false;
        }
    }

    private void checkAuthenticity() {
        if (firebaseClient.getAuthUID() != null) {
            firebaseClient.fetchSelfData(isPresent -> {
                if (!isPresent) {
                    FirebaseAuth.getInstance().signOut();
                    startActivity(new Intent(MainActivity.this, LoginActivity.class));
                    finish();
                }
            });

        } else {
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
            finish();
        }
    }

    private void askForIgnoreBatteryOptimization() {
        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        startActivity(intent);

    }

    private void askLocationPermission() {
        PermissionX.init(this)
                .permissions(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
                .onExplainRequestReason((scope, deniedList) -> {
                    scope.showRequestReasonDialog(deniedList,
                            "Location permission is required for location service", "Allow", "Cancel");
                })
                .onForwardToSettings((scope, deniedList) -> {
                    scope.showForwardToSettingsDialog(deniedList,
                            "You need to allow location permission from settings manually",
                            "Go to Settings", "Cancel");
                })
                .request((allGranted, grantedList, deniedList) -> {
                    if (!allGranted) {
                        Toast.makeText(this, "Location permission is necessary", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void askMicrophonePermission() {
        PermissionX.init(this)
                .permissions(Manifest.permission.RECORD_AUDIO)
                .onExplainRequestReason((scope, deniedList) -> {
                    scope.showRequestReasonDialog(deniedList,
                            "Microphone permission is required for audio features", "Allow", "Cancel");
                })
                .onForwardToSettings((scope, deniedList) -> {
                    scope.showForwardToSettingsDialog(deniedList,
                            "You need to allow microphone permission from settings manually",
                            "Go to Settings", "Cancel");
                })
                .request((allGranted, grantedList, deniedList) -> {
                    if (!allGranted) {
                        Toast.makeText(this, "Microphone permission is necessary", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void askCameraPermission() {
        PermissionX.init(this)
                .permissions(Manifest.permission.CAMERA)
                .onExplainRequestReason((scope, deniedList) -> {
                    scope.showRequestReasonDialog(deniedList,
                            "Camera permission is required for video features", "Allow", "Cancel");
                })
                .onForwardToSettings((scope, deniedList) -> {
                    scope.showForwardToSettingsDialog(deniedList,
                            "You need to allow camera permission from settings manually",
                            "Go to Settings", "Cancel");
                })
                .request((allGranted, grantedList, deniedList) -> {
                    if (!allGranted) {
                        Toast.makeText(this, "Camera permission is necessary", Toast.LENGTH_SHORT).show();
                    }
                });
    }
    private void askCallLogPermission() {
        PermissionX.init(this)
                .permissions(Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_CONTACTS)
                .onExplainRequestReason((scope, deniedList) -> {
                    scope.showRequestReasonDialog(deniedList,
                            "Camera permission is required for video features", "Allow", "Cancel");
                })
                .onForwardToSettings((scope, deniedList) -> {
                    scope.showForwardToSettingsDialog(deniedList,
                            "You need to allow camera permission from settings manually",
                            "Go to Settings", "Cancel");
                })
                .request((allGranted, grantedList, deniedList) -> {
                    if (!allGranted) {
                        Toast.makeText(this, "Camera permission is necessary", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private boolean areAllRequiredPermissionsGranted() {
        return isAccessibilityServiceEnabled(this) &&
                isLocationPermissionGranted(this);
    }

    private void observeAccessibilitySetting(SwitchMaterial yourSwitch) {
        accessibilityObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange) {
                boolean isEnabled = isAccessibilityServiceEnabled(getApplicationContext());
                yourSwitch.setChecked(isEnabled);
            }
        };

        getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
                false,
                accessibilityObserver
        );
    }
    public boolean isNotificationServiceEnabled(Context context) {
        String pkgName = context.getPackageName();
        final String flat = Settings.Secure.getString(context.getContentResolver(),
                "enabled_notification_listeners");
        return flat != null && flat.contains(pkgName);
    }


    @Override
    protected void onResume() {
        super.onResume();
        switch_camera_permission.setChecked(isCameraPermissionGranted(this));
        switch_camera_permission.setEnabled(!isCameraPermissionGranted(this));
        switch_microphone_permission.setChecked(isMicrophonePermissionGranted(this));
        switch_microphone_permission.setEnabled(!isMicrophonePermissionGranted(this));
        switch_location_permission.setChecked(isLocationPermissionGranted(this));
        switch_location_permission.setEnabled(!isLocationPermissionGranted(this));
        switch_ignore_battery_optimization.setChecked(isBatteryOptimizationIgnored(this));
        switch_ignore_battery_optimization.setEnabled(!isBatteryOptimizationIgnored(this));
        boolean isEnabled = isAccessibilityServiceEnabled(this);
        switch_accessibility_permission.setChecked(isEnabled);
        switch_accessibility_permission.setEnabled(!isEnabled);
        switch_notification_access_permission.setChecked(isNotificationServiceEnabled(MainActivity.this));
        switch_notification_access_permission.setEnabled(!isNotificationServiceEnabled(MainActivity.this));
        switch_call_logs_permission.setChecked(isCallLogsPermissionGranted(this));
        switch_call_logs_permission.setEnabled(!isCallLogsPermissionGranted(this));
        if (areAllRequiredPermissionsGranted()) {
            startService();
        }
    }
    @Override
    protected void onStart() {
        super.onStart();
        observeAccessibilitySetting(switch_accessibility_permission);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (accessibilityObserver != null) {
            getContentResolver().unregisterContentObserver(accessibilityObserver);
        }
    }

}
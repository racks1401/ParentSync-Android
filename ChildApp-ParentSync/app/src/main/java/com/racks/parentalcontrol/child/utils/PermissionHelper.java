package com.racks.parentalcontrol.child.utils;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.PowerManager;
import android.provider.Settings;

import androidx.core.content.ContextCompat;

import com.racks.parentalcontrol.child.services.AutoClickAccessibilityService;

public class PermissionHelper {

    public static boolean isCameraPermissionGranted(Context context){
        return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED;

    }

    public static boolean isMicrophonePermissionGranted(Context context){
        return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean isLocationPermissionGranted(Context context){
        return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;
    }
    public static boolean isCallLogsPermissionGranted(Context context){
        return ContextCompat.checkSelfPermission(context,Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context,Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean isBatteryOptimizationIgnored(Context context) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(context.getPackageName());
    }

    public static boolean isAccessibilityServiceEnabled(Context context) {
        String service = context.getPackageName() + "/" + AutoClickAccessibilityService.class.getName();
        String enabledServices = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        return enabledServices != null && enabledServices.toLowerCase().contains(service.toLowerCase());
    }
}

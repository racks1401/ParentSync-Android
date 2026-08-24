package com.racks.parentalcontrol.child.activities;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;

import androidx.core.content.ContextCompat;

import com.racks.parentalcontrol.child.services.ScreenCaptureService;

public class TransparentPermissionActivity extends Activity {
    private static final int REQUEST_CODE = 1001;
    private boolean isScreenShare = false;
    private String target;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        isScreenShare = getIntent().getBooleanExtra("isScreenShare", false);
        target = getIntent().getStringExtra("target");
        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        Intent intent = mpm.createScreenCaptureIntent();
        startActivityForResult(intent, REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            Intent screenIntent = new Intent(this, ScreenCaptureService.class);
            if (isScreenShare){
                screenIntent.putExtra("resultCode", resultCode);
                screenIntent.putExtra("data", data);
                screenIntent.putExtra("target", target);
                ContextCompat.startForegroundService(this, screenIntent);
            }else{
                screenIntent.putExtra("resultCode", resultCode);
                screenIntent.putExtra("data", data);
                screenIntent.putExtra("isScreenshot", true);
                ContextCompat.startForegroundService(this, screenIntent);
            }
        }
        finish();
    }
}


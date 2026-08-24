package com.racks.parentalcontrol.child.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class MySharedPreferences {
    private SharedPreferences sharedPref;
    private SharedPreferences.Editor editor;

    public MySharedPreferences(Context context) {
        sharedPref = context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
        editor = sharedPref.edit();
    }

    public void setUserName(String username){
        editor.putString("userName", username);
        editor.apply();
    }
    public String getUserName() {
        return sharedPref.getString("userName", "");
    }
    public void setFgServiceRunningFlag(boolean running) {
                editor.putBoolean("fg_service_running", running)
                .apply();
    }
    public boolean getFgServiceRunningFlag() {
        return sharedPref.getBoolean("fg_service_running", false);
    }

    public void setLastUploadTime(long lastUploadTime) {
        editor.putLong("last_upload_time", lastUploadTime)
                .apply();
    }
    public long getLastUploadTime() {
        final long ONE_MONTH_MILLIS = 30L * 24 * 60 * 60 * 1000;
        long now = System.currentTimeMillis();
        return sharedPref.getLong("last_upload_time", now - ONE_MONTH_MILLIS);
    }

}

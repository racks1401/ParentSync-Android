package com.racks.parentalcontrol.parent.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class MySharedPreferences {
    private final SharedPreferences sharedPref;
    private final SharedPreferences.Editor editor;

    public MySharedPreferences(Context context) {
        sharedPref = context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
        editor = sharedPref.edit();
    }

    public void setUserName(String username){
        editor.putString("userName", username);
        editor.apply();
    }
    public String getUserName(){
        return sharedPref.getString("userName", null);
    }
    public String getDefaultChild(){
        return sharedPref.getString("default_child", null);
    }
    public void setDefaultChild(String childName){
        editor.putString("default_child", childName);
        editor.apply();
    }

    public void setDefaultDevice(String deviceModel) {
        editor.putString("default_device", deviceModel);
        editor.apply();
    }
    public String getDefaultDevice() {
        return sharedPref.getString("default_device", null);
    }

    public void enableDarkMode(Boolean isDark) {
        editor.putBoolean("dark_mode", isDark);
        editor.apply();
    }
    public Boolean isDarkModeEnabled() {
        return sharedPref.getBoolean("dark_mode", false);
    }

    public void enableRoute(Boolean routeEnabled) {
        editor.putBoolean("route_enabled", routeEnabled);
        editor.apply();
    }
    public Boolean isRouteEnabled() {
        return sharedPref.getBoolean("route_enabled", false);
    }

    public void enableShowLiveRoute(Boolean routeEnabled) {
        editor.putBoolean("show_live_route", routeEnabled);
        editor.apply();
    }
    public Boolean isShowLiveRouteEnabled() {
        return sharedPref.getBoolean("show_live_route", false);
    }

}

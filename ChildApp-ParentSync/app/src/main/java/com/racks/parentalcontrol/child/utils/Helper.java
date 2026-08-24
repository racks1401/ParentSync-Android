package com.racks.parentalcontrol.child.utils;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;

public class Helper {
    public static void setAppIconVisibility(Context context, boolean visible) {
        PackageManager pm = context.getPackageManager();
        ComponentName alias = new ComponentName(context.getPackageName(), "com.racks.parentalcontrol.child.LauncherAlias");

        pm.setComponentEnabledSetting(
                alias,
                visible ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
        );
    }
    public static boolean isAppIconVisible(Context context) {
        PackageManager pm = context.getPackageManager();
        ComponentName alias = new ComponentName(context.getPackageName(), "com.racks.parentalcontrol.child.LauncherAlias");
        int state = pm.getComponentEnabledSetting(alias);

        if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
            return true;
        } else if (state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
            return false;
        } else {
            try {
                ActivityInfo info = pm.getActivityInfo(alias, 0);
                return info.enabled;
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
                return false;
            }
        }
    }
}

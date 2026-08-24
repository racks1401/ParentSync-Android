# ============================================================
# ParentSync - Parent App
# R8 / ProGuard rules
# ============================================================


# ------------------------------------------------------------
# Firebase callback interfaces
# ------------------------------------------------------------

-keep interface com.racks.parentalcontrol.parent.interfaces.** { *; }


# ------------------------------------------------------------
# Firebase / Gson models
# ------------------------------------------------------------

-keep class com.racks.parentalcontrol.parent.models.** { *; }


# ------------------------------------------------------------
# WebRTC
# ------------------------------------------------------------

-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

# Keep WebRTC JNI Zero classes
-keep class org.jni_zero.** { *; }


# ------------------------------------------------------------
# Parent foreground service
# ------------------------------------------------------------

-keepnames class com.racks.parentalcontrol.parent.services.MyForegroundService

-keepclassmembers class com.racks.parentalcontrol.parent.services.MyForegroundService {
    public <init>();
    public void onCreate();
    public int onStartCommand(android.content.Intent, int, int);
    public void onDestroy();
}


# ------------------------------------------------------------
# Native methods
# ------------------------------------------------------------

-keepclasseswithmembernames class * {
    native <methods>;
}
# Firebase callbacks/interfaces
-keep interface com.racks.parentalcontrol.child.interfaces.** { *; }

# Firebase/Gson models
-keep class com.racks.parentalcontrol.child.models.** { *; }

# WebRTC
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

# Keep WebRTC JNI Zero classes
-keep class org.jni_zero.** { *; }

# Foreground service
-keepnames class com.racks.parentalcontrol.child.services.MyForegroundService

-keepclassmembers class com.racks.parentalcontrol.child.services.MyForegroundService {
    public <init>();
    public onCreate();
    public int onStartCommand(android.content.Intent, int, int);
    public onDestroy();
}
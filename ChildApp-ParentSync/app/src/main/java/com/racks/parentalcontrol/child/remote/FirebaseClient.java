package com.racks.parentalcontrol.child.remote;

import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.gson.Gson;
import com.racks.parentalcontrol.child.models.ChildDetailModel;
import com.racks.parentalcontrol.child.models.DataModel;
import com.racks.parentalcontrol.child.interfaces.ErrorCallBack;
import com.racks.parentalcontrol.child.interfaces.NewEventCallBack;
import com.racks.parentalcontrol.child.interfaces.NewTriggerCallBack;
import com.racks.parentalcontrol.child.interfaces.SelfDataCallback;
import com.racks.parentalcontrol.child.interfaces.SuccessCallBack;
import com.racks.parentalcontrol.child.models.TriggerModel;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class FirebaseClient {

    private final Gson gson = new Gson();
    private final DatabaseReference dbRef = FirebaseDatabase.getInstance().getReference("users");
    private final FirebaseAuth mAuth = FirebaseAuth.getInstance();
    private DatabaseReference incomingCallRef;
    private ValueEventListener incomingCallListener;

    private DatabaseReference triggerRef;
    private ValueEventListener triggerListener;
    private static final String LATEST_EVENT_FIELD_NAME = "latest_event";

    public void login(String name, String email, String password, SuccessCallBack successCallBack, ErrorCallBack errorCallBack){
        mAuth.signInWithEmailAndPassword(email, password).addOnCompleteListener(task -> {
            if (task.isSuccessful()){
                setupChildDetail(name, email, successCallBack, errorCallBack);
            }else{
                Log.e("FirebaseClient", Objects.requireNonNull(task.getException().getMessage()));
                errorCallBack.onError(task.getException().getMessage());
            }
        });
    }

    public void setupChildDetail(String name, String email, SuccessCallBack successCallBack, ErrorCallBack errorCallBack) {
        getFCMToken(token -> {
            if (token != null) {
                Log.d("RaviKumar-FirebaseClient", token);
                String deviceId = getDeviceId();
                if (getAuthUID()!=null){
                    ChildDetailModel childInfo = new ChildDetailModel(name, email,deviceId, getDeviceModel(), "unknown", "unknown", token);
                    dbRef.child(getAuthUID()).child("Children").child(getDeviceId()).child("Child_Detail").setValue(childInfo).addOnCompleteListener(task -> {
                        if (task.isSuccessful()){
                            successCallBack.onSuccess();
                        }else {
                            Log.e("FirebaseClient", Objects.requireNonNull(task.getException().getMessage()));
                            errorCallBack.onError(task.getException().getMessage());
                        }
                    });
                }else{
                    errorCallBack.onError("Auth is null");
                }
            }else {
                Log.d("RaviKumar-FirebaseClient", "Fetch token error");
            }
        });

    }
    public void updateBatteryStatus(String battery, SuccessCallBack successCallBack, ErrorCallBack errorCallBack){
        if (getAuthUID()!=null){
            dbRef.child(getAuthUID()).child("Children").child(getDeviceId()).child("Child_Detail").child("battery_percentage").setValue(battery).addOnCompleteListener(task -> {
                if (task.isSuccessful()){
                    successCallBack.onSuccess();
                }else {
                    Log.e("RaviKumarFirebaseClient", Objects.requireNonNull(task.getException().getMessage()));
                    errorCallBack.onError(task.getException().getMessage());
                }
            });
        }
    }
    public void updateOnlineStatus(String last_online, SuccessCallBack successCallBack, ErrorCallBack errorCallBack){
        if (getAuthUID()!=null){
            dbRef.child(getAuthUID()).child("Children").child(getDeviceId()).child("Child_Detail").child("last_online").setValue(last_online).addOnCompleteListener(task -> {
                if (task.isSuccessful()){
                    successCallBack.onSuccess();
                }else {
                    Log.e("FirebaseClient", Objects.requireNonNull(task.getException().getMessage()));
                    errorCallBack.onError(task.getException().getMessage());
                }
            });
        }
    }
    public String getDeviceModel() {
        String model = android.os.Build.MODEL;
        String manufacturer = Build.MANUFACTURER;
        return capitalize(manufacturer) + " " + model;
    }
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return "";
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }
    public String getDeviceId() {
        String rawId = Build.BOARD + Build.BRAND + Build.DEVICE +
                Build.HARDWARE + Build.ID + Build.MANUFACTURER +
                Build.MODEL + Build.PRODUCT;

        UUID deviceUUID = UUID.nameUUIDFromBytes(rawId.getBytes(StandardCharsets.UTF_8));
        return deviceUUID.toString();
    }

    public void sendMessageToOtherUser(DataModel dataModel, ErrorCallBack errorCallBack){
        if (getAuthUID()!=null){
            DatabaseReference callRef = dbRef
                    .child(getAuthUID())
                    .child("Children")
                    .child(getDeviceId())
                    .child("Call_Signal");
            callRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.child(dataModel.getTarget()).exists()){
                        //send the signal to other user
                        callRef.child(dataModel.getTarget()).child(LATEST_EVENT_FIELD_NAME)
                                .setValue(gson.toJson(dataModel));

                    }else {
                        errorCallBack.onError("Child not found!!");
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {errorCallBack.onError("Child not found!!");
                }
            });

        }else{
            Log.d("FirebaseClient", "Auth is null");
        }
    }

    public void observeIncomingLatestEvent(NewEventCallBack callBack){
        if (getAuthUID()!=null){
            incomingCallRef = dbRef
                    .child(getAuthUID())
                    .child("Children")
                    .child(getDeviceId())
                    .child("Call_Signal")
                    .child("child")
                    .child(LATEST_EVENT_FIELD_NAME);

            if (incomingCallListener != null) {
                incomingCallRef.removeEventListener(incomingCallListener);
            }
            incomingCallListener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    try{
                        if (snapshot.exists()){
                            String data= snapshot.getValue(String.class);
                            DataModel dataModel = gson.fromJson(data,DataModel.class);
                            callBack.onNewEventReceived(dataModel);
                        }
                    }catch (Exception e){
                        e.printStackTrace();
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {

                }
            };
            incomingCallRef.addValueEventListener(incomingCallListener);
        }else{
            Log.d("FirebaseClient", "Auth is null");
        }

    }
    public void removeIncomingCallListener() {
        if (incomingCallRef != null && incomingCallListener != null) {
            incomingCallRef.removeEventListener(incomingCallListener);
            incomingCallListener = null;
        }
    }
    public void listenForTriggers(NewTriggerCallBack callBack){
        if (getAuthUID()!=null){
            triggerRef = dbRef.child(getAuthUID()).child("Children").child(getDeviceId()).child("Triggers");
            if (triggerListener != null) {
                triggerRef.removeEventListener(triggerListener);
            }
            triggerListener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        TriggerModel triggerModel = snapshot.getValue(TriggerModel.class);
                        callBack.onNewTriggerReceived(triggerModel);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {

                }
            };
            triggerRef.addValueEventListener(triggerListener);
        }else{
            Log.d("FirebaseClient", "Auth is null");
        }

    }
    public void removeTriggerListener() {
        if (triggerRef != null && triggerListener != null) {
            triggerRef.removeEventListener(triggerListener);
            triggerListener = null;
        }
    }


    public String getAuthUID(){
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            return user.getUid();
            // safe to use uid
        } else {
            return null;
        }
    }

    public void fetchSelfData(SelfDataCallback callback) {
        if (getAuthUID() != null) {
            dbRef.child(getAuthUID()).child("Children").child(getDeviceId())
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            boolean isPresent = snapshot.exists();
                            callback.onResult(isPresent);
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            Log.e("RaviKumarFirebaseClient", "Error fetching self data: " + error.getMessage());
                            callback.onResult(false);
                        }
                    });
        } else {
            Log.d("RaviKumarFirebaseClient", "Auth is null");
            callback.onResult(false);
        }
    }

    public void uploadCaptureToFirebase(byte[] imageData, String snap_type) {
        StorageReference ref = FirebaseStorage.getInstance().getReference("/users/"+getAuthUID()+"/"+getDeviceId()+"/snapshots/" + snap_type +"_"+System.currentTimeMillis() + ".jpg");
        ref.putBytes(imageData)
                .addOnSuccessListener(taskSnapshot -> {
                    ref.getDownloadUrl().addOnSuccessListener(uri -> {
                        updateItToRealtimeDatabase(uri, snap_type);
                    });
                    Log.d("RaviKumarFirebaseClient", "Uploaded to Firebase");
                })
                .addOnFailureListener(e -> {
                    showErrorRemotely(e.getMessage(), "MessageToParent");
                    Log.e("RaviKumarFirebaseClient", "Firebase upload failed", e);
                });
    }

    private void updateItToRealtimeDatabase(Uri uri, String snap_type) {
        DatabaseReference snapshotsRef = dbRef.child(getAuthUID())
                .child("Children")
                .child(getDeviceId());

        Map<String, Object> snapshotMap = new HashMap<>();
        snapshotMap.put("snap_url", uri.toString()); // Convert URI to string
        snapshotMap.put("snap_type", snap_type);
        snapshotMap.put("upload_time", System.currentTimeMillis());

        snapshotsRef.child("Snapshots").push().setValue(snapshotMap)
                .addOnSuccessListener(unused -> {
                        showErrorRemotely("Snapshot Captured", "MessageToParent");
                })
                .addOnFailureListener(e -> {
                    showErrorRemotely(e.getMessage(),"MessageToParent");
                });
    }
    public void resetTrigger(String triggerName, boolean triggerValue){
        DatabaseReference triggerRef = dbRef.child(getAuthUID())
                .child("Children")
                .child(getDeviceId())
                .child("Triggers");
        triggerRef.child(triggerName).setValue(triggerValue).addOnSuccessListener(unused -> {
            if (triggerName.equals("resetConnection")) {
                showErrorRemotely("Connection is reset successfully", "MessageToParent");
            }
        }).addOnFailureListener(e -> showErrorRemotely(e.getMessage(), "MessageToParent"));
    }
    public void showErrorRemotely(String msg, String errChildRef){
        DatabaseReference errorRef = dbRef.child(getAuthUID())
                .child("Children")
                .child(getDeviceId())
                .child(errChildRef);

        errorRef.child("message").setValue(msg);
    }

    public void getFCMToken(TokenCallback callback) {
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        String token = task.getResult();
                        Log.d("RaviKumar-FirebaseClient", "Token: " + token);
                        callback.onTokenReceived(token);
                    } else {
                        Log.w("RaviKumar-FirebaseClient", "Fetching FCM token failed", task.getException());
                        callback.onTokenReceived(null);
                    }
                });
    }

    public void updateCallLogs(Map<String, Object> callLogs) {
        if (getAuthUID() != null) {
            dbRef.child(getAuthUID()).child("Children").child(getDeviceId()).child("Call_Logs").push().updateChildren(callLogs).addOnSuccessListener(unused -> {
                showErrorRemotely("Call Logs Refreshed", "MessageToParent");
            }).addOnFailureListener(e -> showErrorRemotely(e.getLocalizedMessage(), "MessageToParent"));
        }
    }

    public void updateNotifications(Map<String, Object> notificationData) {
        if (getAuthUID() != null) {
            dbRef.child(getAuthUID()).child("Children").child(getDeviceId()).child("Notifications").push().updateChildren(notificationData).addOnSuccessListener(unused -> {
            }).addOnFailureListener(e -> showErrorRemotely(e.getLocalizedMessage(), "MessageToParent"));
        }
    }

    public interface TokenCallback {
        void onTokenReceived(String token);
    }

}

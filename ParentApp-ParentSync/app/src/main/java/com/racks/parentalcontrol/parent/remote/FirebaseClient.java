package com.racks.parentalcontrol.parent.remote;

import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.gson.Gson;
import com.racks.parentalcontrol.parent.models.ChildDetailModel;
import com.racks.parentalcontrol.parent.models.DataModel;
import com.racks.parentalcontrol.parent.interfaces.ErrorCallBack;
import com.racks.parentalcontrol.parent.interfaces.LocationCallBack;
import com.racks.parentalcontrol.parent.models.LocationModel;
import com.racks.parentalcontrol.parent.utils.MySharedPreferences;
import com.racks.parentalcontrol.parent.interfaces.NewEventCallBack;
import com.racks.parentalcontrol.parent.interfaces.NewEventListCallback;
import com.racks.parentalcontrol.parent.interfaces.NewMsgCallback;
import com.racks.parentalcontrol.parent.interfaces.SuccessCallBack;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class FirebaseClient {

    private final Gson gson = new Gson();
    private final DatabaseReference dbRef = FirebaseDatabase.getInstance().getReference("users");
    private static FirebaseClient instance;
    private final FirebaseAuth mAuth = FirebaseAuth.getInstance();
    private static final String LATEST_EVENT_FIELD_NAME = "latest_event";
    private DatabaseReference incomingEvenRef;
    private ValueEventListener incomingEvenListener;
    private DatabaseReference childLocationRef;
    private ValueEventListener childLocationListener;
    private DatabaseReference childDetailRef;
    private ValueEventListener childDetailListener;
    private DatabaseReference fetchAllChildRef;
    private ValueEventListener fetchAllChildListener;
    private DatabaseReference childMessageRef;
    private ValueEventListener childMsgListener;
    private MySharedPreferences mySharedPreferences;

    public FirebaseClient(MySharedPreferences mySharedPreferences) {
        this.mySharedPreferences = mySharedPreferences;
    }

    public void login(String email, String password, SuccessCallBack successCallBack, ErrorCallBack errorCallBack){
        mAuth.signInWithEmailAndPassword(email, password).addOnCompleteListener(task -> {
            if (task.isSuccessful()){
                successCallBack.onSuccess();
            }else{
                errorCallBack.onError(task.getException().getMessage());
            }
        });
    }
    public void register(String name, String email, String password, SuccessCallBack successCallBack, ErrorCallBack errorCallBack) {
        mAuth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(task -> {
            if (task.isSuccessful()){
                Map<String, Object> updateMap = new HashMap<>();
                updateMap.put("name", name);
                updateMap.put("email", email);
                updateMap.put("device_model", getDeviceModel());
                updateMap.put("deviceId", getDeviceId());
                dbRef.child(getAuthUID()).child("Parent_Detail").updateChildren(updateMap).addOnCompleteListener(task1 -> {
                    if (task1.isSuccessful()){
                        successCallBack.onSuccess();
                    }else {
                        Log.e("FirebaseClient", Objects.requireNonNull(task.getException().getMessage()));
                        errorCallBack.onError(task1.getException().getMessage());
                    }
                });
            }else{
                errorCallBack.onError(task.getException().getMessage());
            }
        });


    }
    public void resetCallData(ErrorCallBack errorCallBack) {
        String uid = getAuthUID();
        String defaultDevice = mySharedPreferences.getDefaultDevice();
        if (uid == null) {
            errorCallBack.onError("User not authenticated");
            return;
        }
        if (defaultDevice == null || defaultDevice.isEmpty()){
            errorCallBack.onError("Default device not set.");
            return;
        }
        dbRef.child(getAuthUID()).child("Children").child(mySharedPreferences.getDefaultDevice()).child("Call_Signal").child("parent").setValue("").addOnCompleteListener(task -> {
            if (task.isSuccessful()){
                dbRef.child(getAuthUID()).child("Children").child(mySharedPreferences.getDefaultDevice()).child("Call_Signal").child("child").setValue("").addOnCompleteListener(task1 -> {
                    if (!task1.isSuccessful()) {
                        errorCallBack.onError(task1.getException().getMessage());
                    }
                });
            }else{
                errorCallBack.onError(task.getException().getMessage());
            }
        });
        Log.d("RaviKumar-FirebaseClient", "reseting call data"+mySharedPreferences.getDefaultDevice());
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
        String uid = getAuthUID();
        String defaultDevice = mySharedPreferences.getDefaultDevice();
        if (uid == null) {
            errorCallBack.onError("User not authenticated");
            return;
        }
        if (defaultDevice == null || defaultDevice.isEmpty()){
            errorCallBack.onError("Default device not set.");
            return;
        }
        DatabaseReference callSignalRef = dbRef
                .child(getAuthUID())
                .child("Children")
                .child(defaultDevice)
                .child("Call_Signal");
        if (getAuthUID()!=null) {
            callSignalRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.child(dataModel.getTarget()).exists()) {
                        //send the signal to other user
                        Log.d("FirebaseClient", dataModel.getTarget());
                        callSignalRef.child(dataModel.getTarget()).child(LATEST_EVENT_FIELD_NAME)
                                .setValue(gson.toJson(dataModel));

                    } else {
                        errorCallBack.onError("No Target found");
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    errorCallBack.onError(error.getMessage());
                }
            });
        }
    }

    public void observeIncomingLatestEvent(NewEventCallBack callBack){
        String uid = getAuthUID();
        String defaultDevice = mySharedPreferences.getDefaultDevice();
        if (uid == null) {
            callBack.onError("User not authenticated");
            return;
        }
        if (defaultDevice == null || defaultDevice.isEmpty()){
            callBack.onError("Default device not set.");
            return;
        }
        if (incomingEvenListener != null && incomingEvenRef != null) {
            incomingEvenRef.removeEventListener(incomingEvenListener);
        }
        incomingEvenRef = dbRef
                .child(uid)
                .child("Children")
                .child(defaultDevice)
                .child("Call_Signal")
                .child("parent")
                .child(LATEST_EVENT_FIELD_NAME);

        incomingEvenListener = incomingEvenRef.addValueEventListener(
                new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        try {
                            if (snapshot.exists()){
                                String data = Objects.requireNonNull(snapshot.getValue()).toString();
                                DataModel dataModel = gson.fromJson(data, DataModel.class);
                                callBack.onNewEventReceived(dataModel);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {

                    }
                }
        );
    }
    public void removeIncomingEventListener() {
        if (incomingEvenListener != null && incomingEvenRef != null) {
            incomingEvenRef.removeEventListener(incomingEvenListener);
            childLocationListener = null;
            incomingEvenRef = null;
        }
    }

    public void fetchLocation(LocationCallBack callBack){
        String uid = getAuthUID();
        String defaultDevice = mySharedPreferences.getDefaultDevice();
        if (uid == null) {
            callBack.onError("User not authenticated");
            return;
        }
        if (defaultDevice == null || defaultDevice.isEmpty()){
            callBack.onError("Default device not set.");
            return;
        }
        if (childLocationListener != null && childLocationRef != null) {
            childLocationRef.removeEventListener(childLocationListener);
        }
        childLocationRef = dbRef
                .child(uid)
                .child("Children")
                .child(defaultDevice)
                .child("Location")
                .child("current_location");
        childLocationListener = childLocationRef.addValueEventListener(
                new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        try{
                            if (snapshot.exists()){
                                LocationModel locationModel = snapshot.getValue(LocationModel.class);
                                callBack.onLocationChanged(locationModel);
                            }else {
                                callBack.onError("No location found at this time");
                            }
                        }catch (Exception e){
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        callBack.onError(error.getMessage());
                    }
                }
        );
    }
    public void deleteRouteHistory(String selectedDate, SuccessCallBack successCallBack, ErrorCallBack errorCallBack){
        String uid = getAuthUID();
        String deviceId = mySharedPreferences.getDefaultDevice();
        if (uid == null) {
            errorCallBack.onError("User not authenticated");
            return;
        }
        DatabaseReference historyRef = dbRef
                .child(uid)
                .child("Children")
                .child(deviceId)
                .child("Location")
                .child("location_history");

        historyRef.child(selectedDate).removeValue().addOnSuccessListener(unused -> successCallBack.onSuccess()).addOnFailureListener(e -> errorCallBack.onError(e.getMessage()));
    }
    public void removeChildLocationListener() {
        if (childLocationListener != null && childLocationRef != null) {
            childLocationRef.removeEventListener(childLocationListener);
            childLocationListener = null;
            childLocationRef = null;
        }
    }

    public void fetchChildDetail(NewEventCallBack eventCallback) {
        String uid = getAuthUID();
        String defaultDevice = mySharedPreferences.getDefaultDevice();
        if (uid == null) {
            eventCallback.onError("User not authenticated");
            return;
        }
        if (defaultDevice == null || defaultDevice.isEmpty()){
            eventCallback.onError("Default device not set.");
            return;
        }
        if (childDetailListener != null && childDetailRef != null) {
            childDetailRef.removeEventListener(childDetailListener);
        }
        childDetailRef = dbRef
                .child(uid)
                .child("Children")
                .child(defaultDevice)
                .child("Child_Detail");
        childDetailListener = childDetailRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                try {
                    if (snapshot.exists()) {
                        ChildDetailModel childDetailModel = snapshot.getValue(ChildDetailModel.class);
                        if (childDetailModel != null) {
                            eventCallback.onNewEvenReceived(childDetailModel);
                        } else {
                            eventCallback.onNewEvenReceived(null);
                            eventCallback.onError("Child detail is null.");
                        }
                    } else {
                        mySharedPreferences.setDefaultDevice(""); // reset invalid device
                        eventCallback.onNewEvenReceived(null);
                        eventCallback.onError("No child found!");
                    }
                } catch (Exception e) {
                    eventCallback.onError("Failed to parse child data: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                eventCallback.onError("Firebase error: " + error.getMessage());
            }
        });
    }
    public void removeChildDetailListener() {
        if (childDetailListener != null && childDetailRef != null) {
            childDetailRef.removeEventListener(childDetailListener);
            childDetailListener = null;
            childDetailRef = null;
        }
    }
    public void fetchAllChild(NewEventListCallback callback) {
        String uid = getAuthUID();
        if (uid == null) {
            return;
        }
        if (fetchAllChildListener != null && fetchAllChildRef != null) {
            fetchAllChildRef.removeEventListener(fetchAllChildListener);
        }
        fetchAllChildRef = dbRef.child(getAuthUID()).child("Children");

        fetchAllChildListener = fetchAllChildRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                try {
                    if (snapshot.exists()){
                        ArrayList<ChildDetailModel> childList = new ArrayList<>();
                        for (DataSnapshot childSnapshot : snapshot.getChildren()){
                            DataSnapshot detailSnapshot = childSnapshot.child("Child_Detail");
                            ChildDetailModel model = detailSnapshot.getValue(ChildDetailModel.class);
                            if (model != null) {
                                childList.add(model);
                            }
                        }
                        if (!childList.isEmpty()){
                            callback.onNewEventListReceived(childList);

                        }else{
                            callback.onError("Child not found!!");
                        }

                    }else{
                        callback.onError("Child not found!!");
                    }

                }catch (Exception e){
                    e.printStackTrace();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError(error.getMessage());
            }
        });
    }
    public void removeFetchAllChildListener() {
        if (fetchAllChildListener != null && fetchAllChildRef != null) {
            fetchAllChildRef.removeEventListener(fetchAllChildListener);
            fetchAllChildListener = null;
            fetchAllChildRef = null;
        }
    }
    public interface TriggerEventCallback{
        void onNewEvent(Boolean routeEnabled);
    }
    public void getRouteEnabledData(TriggerEventCallback triggerEventCallback){
        String uid = getAuthUID();
        String defaultDevice = mySharedPreferences.getDefaultDevice();
        if (uid == null || defaultDevice == null || defaultDevice.isEmpty()) {
            return;
        }
        dbRef.child(getAuthUID()).child("Children").child(defaultDevice).child("Triggers").child("enableRoute").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()){
                    triggerEventCallback.onNewEvent(snapshot.getValue(Boolean.class));
                }else {
                    triggerEventCallback.onNewEvent(false);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                triggerEventCallback.onNewEvent(false);

            }
        });
    }

    public void createTrigger(String triggerName, boolean triggerValue,SuccessCallBack successCallBack, ErrorCallBack errorCallBack){
        String uid = getAuthUID();
        String defaultDevice = mySharedPreferences.getDefaultDevice();
        if (uid == null || defaultDevice == null || defaultDevice.isEmpty()) {
            return;
        }
        dbRef.child(getAuthUID()).child("Children").child(defaultDevice).child("Triggers").child(triggerName).setValue(triggerValue).addOnCompleteListener(task -> {
            if (task.isSuccessful()){
                successCallBack.onSuccess();
            }else{
                errorCallBack.onError(task.getException().getMessage());
            }
        });

    }
    public void listenForChildMessage(NewMsgCallback newMsgCallback){
        String uid = getAuthUID();
        String defaultDevice = mySharedPreferences.getDefaultDevice();
        if (uid == null || defaultDevice == null || defaultDevice.isEmpty()) {
            return;
        }
        if (childMsgListener != null && childMessageRef != null) {
            childMessageRef.removeEventListener(childMsgListener);
        }
        childMessageRef = dbRef.child(getAuthUID()).child("Children").child(mySharedPreferences.getDefaultDevice()).child("MessageToParent").child("message");

        childMsgListener = childMessageRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()){
                    newMsgCallback.onNewMessage(snapshot.getValue(String.class));
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });

    }
    public void removeChildMsgListener() {
        if (childMsgListener != null && childMessageRef != null) {
            childMessageRef.removeEventListener(childMsgListener);
            childMsgListener = null;
            childMessageRef = null;
        }
    }
    public void setMessageToNull(){
        dbRef.child(getAuthUID()).child("Children").child(mySharedPreferences.getDefaultDevice()).child("MessageToParent").child("message")
                .setValue(null);

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

    public void sendFcmToChild(String childFcmToken, String action, SuccessCallBack successCallBack, ErrorCallBack errorCallBack) {
        OkHttpClient client = new OkHttpClient();

        JSONObject payload = new JSONObject();
        try {
            payload.put("token", childFcmToken);
            payload.put("data", new JSONObject()
                    .put("title", "Start Location")
                    .put("body", "Triggering location service")
                    .put("action", action)
            );
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }

        RequestBody requestBody = RequestBody.create(
                payload.toString(),
                MediaType.parse("application/json")
        );

        Request request = new Request.Builder()
                .url("https://us-central1-parentsync-parental-control.cloudfunctions.net/sendFcm")
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("FCM_TRIGGER", "Failed to send FCM", e);
                errorCallBack.onError("Failed to send FCM " + e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String body = response.body().string();
                Log.d("FCM_TRIGGER", "Code: " + response.code() + " Body: " + body);
                if (!response.isSuccessful()) {
                    errorCallBack.onError("Server error: " + response.code());
                    return;
                }
                successCallBack.onSuccess();
            }
        });
    }

}

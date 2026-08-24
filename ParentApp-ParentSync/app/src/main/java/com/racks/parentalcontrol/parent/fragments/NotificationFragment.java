package com.racks.parentalcontrol.parent.fragments;

import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.racks.parentalcontrol.parent.R;
import com.racks.parentalcontrol.parent.adapters.NotificationAdapter;
import com.racks.parentalcontrol.parent.databinding.FragmentNotificationBinding;
import com.racks.parentalcontrol.parent.models.LocationModel;
import com.racks.parentalcontrol.parent.models.NotificationModel;
import com.racks.parentalcontrol.parent.remote.FirebaseClient;
import com.racks.parentalcontrol.parent.utils.MySharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

public class NotificationFragment extends Fragment {
    private FragmentNotificationBinding binding;
    private ArrayList<NotificationModel> allNotificationArrayList = new ArrayList<>();
    private DatabaseReference dbRef = FirebaseDatabase.getInstance().getReference();
    private DatabaseReference notificationRef;
    private ValueEventListener notificationListener;
    private FirebaseClient firebaseClient;
    private NotificationAdapter notificationAdapter;
    private MySharedPreferences sharedPreferences;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentNotificationBinding.inflate(inflater, container, false);
        sharedPreferences = new MySharedPreferences(requireContext());
        firebaseClient = new FirebaseClient(sharedPreferences);
        notificationAdapter = new NotificationAdapter(requireContext(), allNotificationArrayList);
        binding.recyclerViewNotification.setAdapter(notificationAdapter);
        binding.checkboxSelectAll.setOnCheckedChangeListener((compoundButton, b) -> {
            if (b){
                notificationAdapter.selectAll();
            }else{
                notificationAdapter.clearSelection();
            }
        });
        return binding.getRoot();
    }
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        if (!notificationAdapter.getSelectedNotifications().isEmpty()) {
                            notificationAdapter.clearSelection();
                        } else {
                            setEnabled(false);
                            requireActivity().onBackPressed();
                        }
                    }
                }
        );
    }


    private void fetchAllNotification() {
        String uid = firebaseClient.getAuthUID();
        String defaultDevice = sharedPreferences.getDefaultDevice();
        if (uid == null || defaultDevice == null || defaultDevice.isEmpty()) {
            return;
        }
        if (notificationListener!=null && notificationRef!=null){
            notificationRef.removeEventListener(notificationListener);
        }
        notificationRef = dbRef.child(uid).child("Children").child(defaultDevice).child("Notifications");
        notificationListener = notificationRef.orderByChild("timestamp").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                allNotificationArrayList.clear();

                if (snapshot.exists()) {
                    for (DataSnapshot dataSnapshot: snapshot.getChildren()){
                        NotificationModel notificationModel = dataSnapshot.getValue(NotificationModel.class);
                        if (notificationModel != null){
                            notificationModel.setNotification_key(dataSnapshot.getKey());
                            allNotificationArrayList.add(notificationModel);
                        }
                    }
                    binding.tvNotificationTitle.setText("Notification(" + allNotificationArrayList.size() + ")");
                    Collections.reverse(allNotificationArrayList);
                } else {
                    binding.tvNotificationTitle.setText("Notification(0)");
                }

                notificationAdapter.notifyDataSetChanged();
            }


            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
        notificationAdapter.setSelectionListener(selectedCount -> {
            if (selectedCount > 0) {
                binding.deleteBtn.setVisibility(View.VISIBLE);
                binding.checkboxSelectAll.setVisibility(View.VISIBLE);
                binding.tvNotificationTitle.setVisibility(View.GONE);
            } else {
                binding.deleteBtn.setVisibility(View.GONE);
                binding.checkboxSelectAll.setVisibility(View.GONE);
                binding.tvNotificationTitle.setVisibility(View.VISIBLE);
            }
        });
        binding.deleteBtn.setOnClickListener(view -> {
            ArrayList<NotificationModel> selectedNotification = notificationAdapter.getSelectedNotifications();
            deleteNotificationFromServer(selectedNotification);
        });

    }

    private void deleteNotificationFromServer(ArrayList<NotificationModel> selectedNotification) {
        String uid = firebaseClient.getAuthUID();
        String defaultDevice = sharedPreferences.getDefaultDevice();
        if (uid == null || defaultDevice == null || defaultDevice.isEmpty()) {
            return;
        }
        removeListener();
        notificationRef = dbRef.child(uid).child("Children").child(defaultDevice).child("Notifications");
        if (binding.checkboxSelectAll.isChecked()){
            notificationRef = dbRef.child(uid).child("Children").child(defaultDevice).child("Notifications");
            notificationRef.removeValue().addOnSuccessListener(unused -> Toast.makeText(requireContext(), "All Notification deleted!!", Toast.LENGTH_SHORT).show()).addOnFailureListener(e -> {
                Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
            });
            return;
        }
        int total = selectedNotification.size();
        AtomicInteger completed = new AtomicInteger(0);
        for (NotificationModel model :  selectedNotification){
            String key = model.getNotification_key();
            if (key!=null){
                notificationRef.child(key).removeValue().addOnCompleteListener(task -> {
                    if (completed.incrementAndGet() == total) {
                        notificationAdapter.notifyDataSetChanged();
                        binding.deleteBtn.setVisibility(View.GONE);
                        fetchAllNotification();
                    }
                });
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        fetchAllNotification();
    }

    private void removeListener(){
        if (notificationListener!=null && notificationRef!=null){
            notificationRef.removeEventListener(notificationListener);
            notificationListener = null;
            notificationRef = null;
        }
    }
}
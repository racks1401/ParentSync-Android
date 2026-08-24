package com.racks.parentalcontrol.parent.fragments;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.airbnb.lottie.LottieAnimationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.racks.parentalcontrol.parent.R;
import com.racks.parentalcontrol.parent.activities.LoginActivity;
import com.racks.parentalcontrol.parent.activities.MainActivity;
import com.racks.parentalcontrol.parent.databinding.FragmentAdditionalSettingsBinding;
import com.racks.parentalcontrol.parent.models.RouteHistoryModel;
import com.racks.parentalcontrol.parent.remote.FirebaseClient;
import com.racks.parentalcontrol.parent.services.MyForegroundService;
import com.racks.parentalcontrol.parent.utils.MySharedPreferences;

import java.util.ArrayList;

public class AdditionalSettingsFragment extends Fragment {

    private FragmentAdditionalSettingsBinding binding;
    private final DatabaseReference dbRef = FirebaseDatabase.getInstance().getReference("users");
    private DatabaseReference routeRef;
    private ValueEventListener routeEventListener;
    private DatabaseReference triggerRef;
    private ValueEventListener triggerEventListener;
    private String deviceId;
    private final ArrayList<RouteHistoryModel> routeList = new ArrayList<>();
    private RecyclerView.Adapter<RecyclerView.ViewHolder> adapter;
    private FirebaseClient firebaseClient;
    private Dialog loadingDialog;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentAdditionalSettingsBinding.inflate(inflater, container, false);
        MySharedPreferences sharedPreferences = new MySharedPreferences(requireActivity());
        deviceId = sharedPreferences.getDefaultDevice();
        firebaseClient = new FirebaseClient(sharedPreferences);
        createLoadingDialog();
        adapter = new RecyclerView.Adapter<>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.list_item_manage_route_history, parent, false);
                return new RecyclerView.ViewHolder(view) {
                };
            }

            @SuppressLint("DefaultLocale")
            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                RouteHistoryModel routeHistoryModel = routeList.get(position);
                TextView tv_history_date = holder.itemView.findViewById(R.id.tv_route_history_date);
                tv_history_date.setText(String.format("%s(%d)",
                        routeHistoryModel.getRouteDate(),
                        routeHistoryModel.getLocationPoints()));

                holder.itemView.setOnLongClickListener(view -> {
                    String selectedDate = routeHistoryModel.getRouteDate();
                    new AlertDialog.Builder(view.getContext())
                            .setTitle("Delete Route")
                            .setMessage("Are you sure you want to delete " + selectedDate + "?")
                            .setPositiveButton("Yes", (dialog, which) -> {
                                deleteRoute(selectedDate);
                            })
                            .setNegativeButton("No", null)
                            .show();
                    return true;
                });
            }

            @Override
            public int getItemCount() {
                return routeList.size();
            }
        };

        binding.recyclerViewManageHistory.setAdapter(adapter);
        binding.switchHideAppIcon.setOnCheckedChangeListener((compoundButton, b) -> firebaseClient.createTrigger("showHideIcon", b, () -> {
        }, err -> Toast.makeText(requireContext(), "Failed to set the icon visibility", Toast.LENGTH_SHORT).show()));
        binding.switchEnableRouteHistory.setOnCheckedChangeListener((compoundButton, b) -> firebaseClient.createTrigger("enableRoute", b, () -> {
        }, err -> Toast.makeText(requireContext(), "Failed to enable/disable route history setting", Toast.LENGTH_SHORT).show()));

        binding.logoutBtn.setOnClickListener(view -> {
            removeAllListeners();

            LocalBroadcastManager.getInstance(requireActivity())
                    .registerReceiver(serviceStoppedReceiver, new IntentFilter("SERVICE_STOPPED"));

            Intent stopIntent = new Intent(requireActivity(), MyForegroundService.class);
            stopIntent.setAction("STOP_FOREGROUND_SERVICE");
            requireActivity().startService(stopIntent);
        });
        return binding.getRoot();
    }
    private final BroadcastReceiver serviceStoppedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            FirebaseAuth.getInstance().signOut();
            startActivity(new Intent(requireActivity(), LoginActivity.class));
            requireActivity().finish();
        }
    };

    private void createLoadingDialog() {
        loadingDialog = new Dialog(requireContext());
        loadingDialog.setContentView(R.layout.connecting_dialog);
        if (loadingDialog.getWindow() != null) {
            loadingDialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
        loadingDialog.setCancelable(false);
        LottieAnimationView lottie_connecting = loadingDialog.findViewById(R.id.lottie_connecting);
        TextView tv_setting_up = loadingDialog.findViewById(R.id.tv_connecting);
        lottie_connecting.setVisibility(View.GONE);
        tv_setting_up.setText("Please wait...");
        loadingDialog.show();
    }

    private void deleteRoute(String selectedDate){
        firebaseClient.deleteRouteHistory(selectedDate, () -> {
        }, err -> Toast.makeText(requireContext(), "Deletion failed!!", Toast.LENGTH_SHORT).show());

    }

    private void fetchRouteHistory() {
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        if (deviceId == null || deviceId.isEmpty()) {
            loadingDialog.dismiss();
            Toast.makeText(requireContext(), "Select Child first", Toast.LENGTH_SHORT).show();
            return;
        }

        if (routeEventListener!=null && routeRef!=null){
            routeRef.removeEventListener(routeEventListener);
        }
        routeRef = dbRef
                .child(uid)
                .child("Children")
                .child(deviceId)
                .child("Location")
                .child("location_history");
        routeEventListener = routeRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists() && snapshot.hasChildren()){
//                    binding.tvNoRouteFound.setVisibility(View.GONE);
                    routeList.clear();
                    for (DataSnapshot dataSnapshot : snapshot.getChildren()) {
                        RouteHistoryModel model = new RouteHistoryModel(dataSnapshot.getKey(), (int) dataSnapshot.getChildrenCount());
                        routeList.add(model);
                    }
                    adapter.notifyDataSetChanged();
                }else {
//                    binding.tvNoRouteFound.setVisibility(View.VISIBLE);
                }
                loadingDialog.dismiss();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(requireActivity(), error.getMessage(), Toast.LENGTH_SHORT).show();
                loadingDialog.dismiss();
            }
        });
    }


    private void fetchTriggers() {
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        if (deviceId == null || deviceId.isEmpty()) {
            return;
        }

        if (triggerEventListener!=null && triggerRef!=null){
            triggerRef.removeEventListener(triggerEventListener);
        }
        triggerRef = dbRef
                .child(uid)
                .child("Children")
                .child(deviceId)
                .child("Triggers");

        triggerEventListener = triggerRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()){
                    binding.switchHideAppIcon.setChecked(Boolean.TRUE.equals(snapshot.child("showAppIcon").getValue(Boolean.class)));
                    binding.switchEnableRouteHistory.setChecked(Boolean.TRUE.equals(snapshot.child("enableRoute").getValue(Boolean.class)));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }
    @Override
    public void onStart() {
        super.onStart();
        fetchRouteHistory();
        fetchTriggers();
    }

    @Override
    public void onStop() {
        super.onStop();
        removeAllListeners();
    }

    private void removeAllListeners() {
        if (routeEventListener != null && routeRef != null) {
            routeRef.removeEventListener(routeEventListener);
            routeEventListener = null;
            routeRef = null;
        }
        if (triggerEventListener != null && triggerRef != null) {
            triggerRef.removeEventListener(triggerEventListener);
            triggerEventListener = null;
            triggerRef = null;
        }
    }
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (loadingDialog != null && loadingDialog.isShowing()) {
            loadingDialog.dismiss();
        }
        binding = null;
    }
}
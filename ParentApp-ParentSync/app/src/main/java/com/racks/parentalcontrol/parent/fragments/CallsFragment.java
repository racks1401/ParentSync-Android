package com.racks.parentalcontrol.parent.fragments;

import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.racks.parentalcontrol.parent.R;
import com.racks.parentalcontrol.parent.adapters.CallAdapter;
import com.racks.parentalcontrol.parent.databinding.FragmentCallsBinding;
import com.racks.parentalcontrol.parent.interfaces.ErrorCallBack;
import com.racks.parentalcontrol.parent.interfaces.SuccessCallBack;
import com.racks.parentalcontrol.parent.models.CallModel;
import com.racks.parentalcontrol.parent.models.SnapshotModel;
import com.racks.parentalcontrol.parent.remote.FirebaseClient;
import com.racks.parentalcontrol.parent.utils.MySharedPreferences;

import java.lang.reflect.Array;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;


public class CallsFragment extends Fragment {

    private FragmentCallsBinding binding;
    private CallAdapter callAdapter;
    private final DatabaseReference dbRef = FirebaseDatabase.getInstance().getReference();
    private DatabaseReference callRef;
    private ValueEventListener callLogListener;
    private final ArrayList<CallModel> allCallList = new ArrayList<>();
    private final ArrayList<CallModel> filteredList = new ArrayList<>();
    private ArrayList<CallModel> selectedCallList = new ArrayList<>();
    private MySharedPreferences sharedPreferences;
    private static final String TAG = "RaviKumar-CallsFragment";
    private FirebaseClient firebaseClient;
    private boolean isDeletionMode = false;
    private String lastSelectedFilter = "all";
    private int noOfCalls = 0;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentCallsBinding.inflate(inflater, container, false);
        sharedPreferences = new MySharedPreferences(requireActivity());
        firebaseClient = new FirebaseClient(sharedPreferences);
        callAdapter = new CallAdapter(requireContext(), filteredList);
        binding.recyclerViewCalls.setAdapter(callAdapter);
        binding.tvFilterCalls.setOnClickListener(view -> {
            if (!isDeletionMode) {
                PopupMenu popupMenu = new PopupMenu(requireContext(), binding.tvFilterCalls);
                popupMenu.getMenu().add("all");
                popupMenu.getMenu().add("last 24hr");
                popupMenu.getMenu().add("last 3days");
                popupMenu.getMenu().add("last week");

                popupMenu.setOnMenuItemClickListener(menuItem -> {
                    String selected = menuItem.getTitle().toString();
                    applyFilter(selected);
                    return true;
                });
                popupMenu.show();
            } else {
                selectedCallList = callAdapter.getSelectedCallList();
                showDeleteDialog(selectedCallList);
            }
        });
        binding.fabRefreshBtnCalls.setOnClickListener(view -> firebaseClient.createTrigger("getCallLogs", true, () -> {

        }, err -> Toast.makeText(requireContext(), err, Toast.LENGTH_SHORT).show()));
        callAdapter.setSelectionListener(selectedCount -> {
            if (selectedCount > 0) {
                isDeletionMode = true;
                Drawable icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_delete_24);
                binding.tvFilterCalls.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null);
                binding.tvFilterCalls.setText("Delete(" + selectedCount + ")");
                binding.checkboxSelectAll.setVisibility(View.VISIBLE);
            } else {
                isDeletionMode = false;
                Drawable icon = ContextCompat.getDrawable(requireContext(), R.drawable.filter_list_24px);
                binding.tvFilterCalls.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null);
                binding.tvFilterCalls.setText("Filter: "+lastSelectedFilter+"("+noOfCalls+")");
                binding.checkboxSelectAll.setVisibility(View.GONE);
            }
        });
        binding.checkboxSelectAll.setOnCheckedChangeListener((compoundButton, b) -> {
            if (b){
                callAdapter.selectAll();
            }else{
                callAdapter.clearSelection();
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
                        if (!callAdapter.getSelectedCallList().isEmpty()) {
                            callAdapter.clearSelection();
                        } else {
                            setEnabled(false);
                            requireActivity().onBackPressed();
                        }
                    }
                }
        );
    }

    private void showDeleteDialog(ArrayList<CallModel> selectedCallList) {
        Dialog deleteCallsDialog = new Dialog(requireActivity());
        deleteCallsDialog.setContentView(R.layout.delete_dialog);
        if (deleteCallsDialog.getWindow()!=null){
            deleteCallsDialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            deleteCallsDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        deleteCallsDialog.setCancelable(false);
        TextView title = deleteCallsDialog.findViewById(R.id.delete_dialog_title);
        TextView message = deleteCallsDialog.findViewById(R.id.delete_dialog_message);
        TextView cancelBtn = deleteCallsDialog.findViewById(R.id.delete_dialog_cancel_button);
        TextView confirmBtn = deleteCallsDialog.findViewById(R.id.delete_dialog_confirm_button);

        title.setText("Delete Calls");
        message.setText("Calls will be deleted from database only, not from the child device\n\nDo you want to proceed?");
        cancelBtn.setOnClickListener(view -> {
            callAdapter.clearSelection();
            deleteCallsDialog.dismiss();
        });
        confirmBtn.setOnClickListener(view -> {
            if (confirmBtn.getText().equals("Done")){
                deleteCallsDialog.dismiss();
            }else{
                deleteCallLogs(selectedCallList, cancelBtn, confirmBtn, message);

            }
        });
        deleteCallsDialog.show();
    }

    private void deleteCallLogs(ArrayList<CallModel> selectedCallList, TextView cancelBtn, TextView confirmBtn, TextView message) {
        String uid = firebaseClient.getAuthUID();
        String defaultDevice = sharedPreferences.getDefaultDevice();
        if (uid == null || defaultDevice == null || defaultDevice.isEmpty()) {
            return;
        }
        removeCallListener();
        callRef = dbRef.child(uid).child("Children").child(defaultDevice).child("Call_Logs");
        if (binding.checkboxSelectAll.isChecked()){
            callRef.removeValue().addOnSuccessListener(unused -> Toast.makeText(requireContext(), "All Call logs deleted!!", Toast.LENGTH_SHORT).show()).addOnFailureListener(e -> Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_SHORT).show());
            return;
        }
        int total = selectedCallList.size();
        AtomicInteger processed = new AtomicInteger(0);
        for (CallModel callModel : selectedCallList) {
            String call_key = callModel.getCall_key();
            callRef.child(call_key).removeValue().addOnCompleteListener(task -> {
                int done = processed.incrementAndGet();
                message.setText("Deleting in progress, please wait... " + done + "/" + total);
                if (done == total) {
                    applyFilter(lastSelectedFilter);
                    message.setText("Deletion successful ✅");
                    isDeletionMode = false;
                    cancelBtn.setEnabled(true);
                    confirmBtn.setEnabled(true);
                    confirmBtn.setText("Done");
                    binding.checkboxSelectAll.setVisibility(View.GONE);
                    binding.checkboxSelectAll.setChecked(false);
                    Drawable icon = ContextCompat.getDrawable(requireContext(), R.drawable.filter_list_24px);
                    binding.tvFilterCalls.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null);
                    fetchCallLogs();
                }
            });
        }
    }

    private void fetchCallLogs(){
        String uid = firebaseClient.getAuthUID();
        String defaultDevice = sharedPreferences.getDefaultDevice();
        if (uid == null || defaultDevice == null || defaultDevice.isEmpty()) {
            return;
        }
        if (callLogListener != null && callRef != null) {
            callRef.removeEventListener(callLogListener);
        }
        Log.d(TAG, "fetch triggered");
        callRef = dbRef.child(uid).child("Children").child(defaultDevice).child("Call_Logs");
        callLogListener = callRef.orderByChild("date").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()){
                    allCallList.clear();
                    for (DataSnapshot dataSnapshot:snapshot.getChildren()){
                        CallModel callModel = dataSnapshot.getValue(CallModel.class);
                        if (callModel!=null){
                            allCallList.add(callModel);
                            callModel.setCall_key(dataSnapshot.getKey());
                        }
                    }
                    Collections.reverse(allCallList);
                    applyFilter(lastSelectedFilter);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    private void applyFilter(String selected) {
        long range;
        long now = System.currentTimeMillis();
        long last24Hours = now - (24 * 60 * 60 * 1000L);
        long last3Days = now - (3 * 24 * 60 * 60 * 1000L);
        long last7Days = now - (7 * 24 * 60 * 60 * 1000L);
        if (selected.equals("last week")){
            range = last7Days;
        }else if (selected.equals("last 3days")){
            range = last3Days;
        }else {
            range = last24Hours;
        }
        filteredList.clear();
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        String lastDate = "";
        for (CallModel calls : allCallList) {
            if ((calls.getDate()!=null)) {
                if ("all".equals(selected) || calls.getDate()>=range){
                    String callDate = sdf.format(new Date(calls.getDate()));
                    if (!callDate.equals(lastDate)) {
                        CallModel header = new CallModel();
                        header.setHeader(true);
                        header.setType("HEADER");
                        header.setDate(calls.getDate());
                        filteredList.add(header);
                        lastDate = callDate;
                    }
                    filteredList.add(calls);
                }
            }else {
                Log.d(TAG, "applyfilter getDate is null calls");
            }
        }
        noOfCalls = 0;
        for (CallModel model : filteredList) {
            if (!model.isHeader()) {
                noOfCalls++;
            }
        }
        lastSelectedFilter = selected;
        binding.tvFilterCalls.setText("Filter: " + selected+"("+noOfCalls+")");
        callAdapter.notifyDataSetChanged();
    }

    @Override
    public void onStart() {
        super.onStart();
        fetchCallLogs();
    }

    @Override
    public void onStop() {
        super.onStop();
        removeCallListener();
    }
    private void removeCallListener(){
        if (callLogListener!=null && callRef!=null){
            callRef.removeEventListener(callLogListener);
            callLogListener = null;
            callRef = null;
        }
    }
}
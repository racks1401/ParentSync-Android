package com.racks.parentalcontrol.parent.fragments;

import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.fragment.NavHostFragment;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.PopupMenu;
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
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.racks.parentalcontrol.parent.R;
import com.racks.parentalcontrol.parent.adapters.SnapshotAdapter;
import com.racks.parentalcontrol.parent.databinding.FragmentSnapshotBinding;
import com.racks.parentalcontrol.parent.models.NotificationModel;
import com.racks.parentalcontrol.parent.models.SnapshotModel;
import com.racks.parentalcontrol.parent.remote.FirebaseClient;
import com.racks.parentalcontrol.parent.utils.MySharedPreferences;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

public class SnapshotFragment extends Fragment {

    private ArrayList<SnapshotModel> allSnapList = new ArrayList<>();
    private ArrayList<SnapshotModel> filteredList = new ArrayList<>();
    private SnapshotAdapter snapshotAdapter;
    private FragmentSnapshotBinding binding;
    private DatabaseReference dbRef = FirebaseDatabase.getInstance().getReference("users");
    private FirebaseClient firebaseClient;
    private ValueEventListener snapListener;
    private MySharedPreferences sharedPreferences;
    private DatabaseReference snapshotRef;
    private String selectedFilter = "all";

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentSnapshotBinding.inflate(inflater, container, false);
        sharedPreferences = new MySharedPreferences(requireActivity());
        firebaseClient = new FirebaseClient(sharedPreferences);
        snapshotAdapter = new SnapshotAdapter(filteredList, requireContext(), new SnapshotAdapter.OnSnapClickListener() {
            @Override
            public void onSnapClick(String snapUrl) {
                NavDirections navDirections = SnapshotFragmentDirections.actionSnapshotFragmentToFullScreenSnapFragment(snapUrl);
                NavHostFragment.findNavController(SnapshotFragment.this).navigate(navDirections);
            }
        });
        binding.recyclerViewSnapshot.setAdapter(snapshotAdapter);
        binding.snapFilterBtn.setOnClickListener(view -> {
            PopupMenu popupMenu = new PopupMenu(requireContext(), binding.snapFilterBtn);
            popupMenu.getMenu().add("all");
            popupMenu.getMenu().add("camera");
            popupMenu.getMenu().add("screen");

            popupMenu.setOnMenuItemClickListener(menuItem -> {
                String selected = menuItem.getTitle().toString().toLowerCase();
                binding.snapFilterBtn.setText("Filter: " + menuItem.getTitle());
                applyFilter(selected);
                return true;
            });
            popupMenu.show();
        });

        CompoundButton.OnCheckedChangeListener selectAllListener = (compoundButton, isChecked) -> {
            if (isChecked) {
                snapshotAdapter.selectAll();
            } else {
                snapshotAdapter.clearSelection();
            }
        };

        snapshotAdapter.setSelectionListener(selectedCount -> {
            if (selectedCount > 0) {
                binding.deleteBtnSnapshot.setVisibility(View.VISIBLE);
                binding.deleteBtnSnapshot.setText("Delete(" + selectedCount + ")");
                binding.checkboxSelectAll.setVisibility(View.VISIBLE);

                binding.checkboxSelectAll.setOnCheckedChangeListener(null);
                binding.checkboxSelectAll.setChecked(selectedCount == allSnapList.size());
                binding.checkboxSelectAll.setOnCheckedChangeListener(selectAllListener);

            } else {
                binding.deleteBtnSnapshot.setVisibility(View.GONE);
                binding.checkboxSelectAll.setVisibility(View.GONE);
            }
        });

        binding.deleteBtnSnapshot.setOnClickListener(view -> {
            ArrayList<SnapshotModel> selectedSnapshots = snapshotAdapter.getSelectedSnapshots();
            deleteSnapshot(selectedSnapshots);
        });
        binding.checkboxSelectAll.setOnCheckedChangeListener(selectAllListener);
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
                        if (!snapshotAdapter.getSelectedSnapshots().isEmpty()) {
                            snapshotAdapter.clearSelection();
                        } else {
                            setEnabled(false);
                            requireActivity().onBackPressed();
                        }
                    }
                }
        );
    }

    private void deleteSnapshot(ArrayList<SnapshotModel> selectedSnapshots) {
        String uid = firebaseClient.getAuthUID();
        String defaultDevice = sharedPreferences.getDefaultDevice();
        if (uid == null || defaultDevice == null || defaultDevice.isEmpty()) {
            return;
        }
        removeListener();
        binding.deleteBtnSnapshot.setText("Deleting Please wait...");
        snapshotRef = dbRef.child(uid).child("Children").child(defaultDevice).child("Snapshots");

        if (binding.checkboxSelectAll.isChecked()) {
            snapshotRef.removeValue()
                    .addOnSuccessListener(unused -> {
                        Toast.makeText(requireContext(), "All Snapshots deleted!!", Toast.LENGTH_SHORT).show();
                        allSnapList.clear();
                        snapshotAdapter.notifyDataSetChanged();
                        binding.deleteBtnSnapshot.setVisibility(View.GONE);
                        binding.checkboxSelectAll.setVisibility(View.GONE);
                        binding.checkboxSelectAll.setChecked(false);
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_SHORT).show()
                    );
            return;
        }

        int total = selectedSnapshots.size();
        AtomicInteger processed = new AtomicInteger(0);
        binding.deleteBtnSnapshot.setEnabled(false);

        for (SnapshotModel model : selectedSnapshots) {
            String snap_key = model.getSnapshot_key();
            FirebaseStorage storage = FirebaseStorage.getInstance();
            StorageReference photoRef = storage.getReferenceFromUrl(model.getSnap_url());

            photoRef.delete()
                    .addOnSuccessListener(unused -> snapshotRef.child(snap_key).removeValue().addOnCompleteListener(task -> {
                        int done = processed.incrementAndGet();
                        binding.deleteBtnSnapshot.setText("Deleting " + done + "/" + total);

                        if (done == total) {
                            applyFilter(selectedFilter);
                            binding.deleteBtnSnapshot.setVisibility(View.GONE);
                            binding.deleteBtnSnapshot.setEnabled(true);
                            binding.checkboxSelectAll.setVisibility(View.GONE);
                            binding.checkboxSelectAll.setChecked(false);
                            fetchSnapshots();
                        }
                    }))
                    .addOnFailureListener(e -> {
                        int done = processed.incrementAndGet();
                        Toast.makeText(requireContext(), e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                        if (done == total) {
                            binding.deleteBtnSnapshot.setVisibility(View.GONE);
                            binding.deleteBtnSnapshot.setEnabled(true);
                            binding.checkboxSelectAll.setVisibility(View.GONE);
                            binding.checkboxSelectAll.setChecked(false);
                            fetchSnapshots();
                        }
                    });
        }
    }

    private void fetchSnapshots() {
        String uid = firebaseClient.getAuthUID();
        String defaultDevice = sharedPreferences.getDefaultDevice();
        if (uid == null || defaultDevice == null || defaultDevice.isEmpty()) {
            return;
        }
        if (snapListener != null && snapshotRef != null) {
            snapshotRef.removeEventListener(snapListener);
        }
        snapshotRef = dbRef.child(uid).child("Children").child(defaultDevice).child("Snapshots");
        snapListener = snapshotRef.orderByChild("upload_time").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Log.d("RaviKumar-SnapshotFragment", "Firebase returned snapshot count: " + snapshot.getChildrenCount());
                if (snapshot.exists()) {
                    allSnapList.clear();
                    for (DataSnapshot dataSnapshot : snapshot.getChildren()) {
                        SnapshotModel snapshotModel = dataSnapshot.getValue(SnapshotModel.class);
                        if (snapshotModel != null) {
                            allSnapList.add(snapshotModel);
                            snapshotModel.setSnapshot_key(dataSnapshot.getKey());
                        }
                    }
                    Collections.reverse(allSnapList);
                    applyFilter(selectedFilter);

                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    private void applyFilter(String type) {
        filteredList.clear();
        for (SnapshotModel snap : allSnapList) {
            if ("all".equals(type) || snap.getSnap_type().equalsIgnoreCase(type)) {
                filteredList.add(snap);
            }
        }
        selectedFilter = type;
        snapshotAdapter.notifyDataSetChanged();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        fetchSnapshots();
    }

    private void removeListener() {
        if (snapListener != null && snapshotRef != null) {
            snapshotRef.removeEventListener(snapListener);
            snapListener = null;
            snapshotRef = null;
        }
    }
}
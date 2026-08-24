package com.racks.parentalcontrol.parent.fragments;

import android.app.TimePickerDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.RoundCap;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.maps.android.SphericalUtil;
import com.racks.parentalcontrol.parent.R;
import com.racks.parentalcontrol.parent.databinding.FragmentMapBinding;
import com.racks.parentalcontrol.parent.models.ChildDetailViewModel;
import com.racks.parentalcontrol.parent.models.LocationModel;
import com.racks.parentalcontrol.parent.remote.FirebaseClient;
import com.racks.parentalcontrol.parent.utils.MySharedPreferences;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;


public class MapFragment extends Fragment implements OnMapReadyCallback {
    private FragmentMapBinding binding;
    private ChildDetailViewModel childDetailViewModel;
    private LatLng latestLocation = null;
    private GoogleMap nMap;
    private Marker marker;
    private Marker startMarker, endMarker;
    private boolean isFirstUpdate = true;
    private boolean showLiveRoute = false;
    private boolean isShowingRouteHistory = false;
    private String deviceId;
    private Observer<LocationModel> locationObserver;
    private Polyline historyPolyline;
    private Polyline livePolyline;
    private final List<LatLng> historyPolylinePoints = new ArrayList<>();
    private final List<LatLng> livePolylinePoints = new ArrayList<>();
    private final List<Marker> arrowMarkers = new ArrayList<>();
    private BitmapDescriptor arrowIcon = null;
    private BitmapDescriptor liveMarkerIcon = null;
    private MySharedPreferences sharedPreferences;
    private int selectedYear = -1, selectedMonth = -1, selectedDay = -1;
    private int startHour = -1, startMinute = -1, endHour = -1, endMinute = -1;


    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentMapBinding.inflate(inflater, container, false);

        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager()
                .findFragmentById(R.id.map_view_map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
        childDetailViewModel = new ViewModelProvider(requireActivity()).get(ChildDetailViewModel.class);
        sharedPreferences = new MySharedPreferences(requireActivity());
        deviceId = sharedPreferences.getDefaultDevice();
        setUpChildData();

        return binding.getRoot();
    }

    private void setUpChildData() {
        childDetailViewModel.getChildDetail().observe(getViewLifecycleOwner(), childDetailModel -> {
            if (childDetailModel!=null){
                binding.tvUsernameMap.setText(childDetailModel.getName());
                int battery_perc = 0;
                try {
                    battery_perc = Integer.parseInt(childDetailModel.getBattery_percentage());
                    binding.tvBatterStatusMap.setText(battery_perc + "%");
                } catch (Exception e) {
                    e.printStackTrace();
                    binding.tvBatterStatusMap.setText(battery_perc + "%");
                }

                if (battery_perc > 75) {
                    binding.igBatteryIconMap.setImageResource(R.drawable.battery_100_green);
                } else if (battery_perc > 50) {
                    binding.igBatteryIconMap.setImageResource(R.drawable.battery_75_green);
                } else if (battery_perc > 25) {
                    binding.igBatteryIconMap.setImageResource(R.drawable.battery_50_green);
                } else {
                    binding.igBatteryIconMap.setImageResource(R.drawable.battery_25);
                }

            }
        });
    }
    public String getLastUpdateString(Long lastOnlineMillis) {
        try {
            long currentMillis = System.currentTimeMillis();
            long diff = currentMillis - lastOnlineMillis;

            long seconds = diff / 1000;
            long minutes = seconds / 60;
            long hours = minutes / 60;
            long days = hours / 24;

            if (seconds < 60) {
                return seconds+" sec ago";
            } else if (minutes < 60) {
                return minutes + " min ago";
            } else if (hours < 24) {
                return hours + " hr ago";
            } else if (days == 1) {
                return "24hr ago";
            } else {
                return days + " days ago";
            }

        } catch (NumberFormatException e) {
            return "Unknown";
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        nMap = googleMap;
        nMap.setPadding(0, 0, 0, 170);
        nMap.getUiSettings().setZoomControlsEnabled(true);
        arrowIcon = BitmapDescriptorFactory.fromBitmap(getResizedBitmap(R.drawable.ic_right_arrow, 16, 16));
        liveMarkerIcon = BitmapDescriptorFactory.fromBitmap(getResizedBitmap(R.drawable.ic_child_location, 120, 120));
        boolean isDarkModeEnabled = sharedPreferences.isDarkModeEnabled();
        if (isDarkModeEnabled){
            googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(requireContext(), R.raw.map_style_dark));
        }
        FirebaseClient firebaseClient = new FirebaseClient(sharedPreferences);
        firebaseClient.getRouteEnabledData(sharedPreferences::enableRoute);
        showLiveRoute = sharedPreferences.isShowLiveRouteEnabled();
        observeLiveLocation();
        nMap.setOnCameraIdleListener(() -> {
            float zoomLevel = nMap.getCameraPosition().zoom;
            updatePolylineWidth(zoomLevel);
        });
        binding.moreOptions.setOnClickListener(view -> {
            PopupMenu popupMenu = new PopupMenu(requireActivity(), binding.moreOptions);
            popupMenu.getMenuInflater().inflate(R.menu.map_menu, popupMenu.getMenu());
            boolean isDarkModeEnabledNow = sharedPreferences.isDarkModeEnabled();
            popupMenu.getMenu().findItem(R.id.item_dark_mode).setChecked(isDarkModeEnabledNow);
            boolean isRouteEnabled = sharedPreferences.isRouteEnabled();
            popupMenu.getMenu().findItem(R.id.item_enable_route_history).setChecked(isRouteEnabled);
            boolean isShowLiveRouteEnabled = sharedPreferences.isShowLiveRouteEnabled();
            popupMenu.getMenu().findItem(R.id.item_show_live_route).setChecked(isShowLiveRouteEnabled);
            popupMenu.getMenu().findItem(R.id.item_show_live_route).setEnabled(!isShowingRouteHistory);
            popupMenu.setOnMenuItemClickListener(menuItem -> {
                int id = menuItem.getItemId();
                if (id == R.id.item_map_view) {
                    nMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
                    return true;

                } else if (id == R.id.item_hybrid_view) {
                    nMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
                    return true;

                } else if (id == R.id.item_dark_mode) {
                    boolean currState = !menuItem.isChecked();
                    menuItem.setChecked(currState);
                    sharedPreferences.enableDarkMode(currState);
                    if (currState){
                        googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(requireContext(), R.raw.map_style_dark));
                    }else{
                        nMap.setMapStyle(null);
                    }
                    return true;
                } else if (id == R.id.item_enable_route_history) {
                    boolean currState = !menuItem.isChecked();
                    menuItem.setChecked(currState);
                    sharedPreferences.enableRoute(currState);
                    firebaseClient.createTrigger("enableRoute", currState, () -> {
                        Toast.makeText(requireContext(), "Child Route tracking activated", Toast.LENGTH_SHORT).show();
                    }, err -> {
                    });
                    return true;
                } else if (id == R.id.item_show_live_route) {
                    boolean newState = !menuItem.isChecked();
                    menuItem.setChecked(newState);
                    showLiveRoute = newState;
                    if (!showLiveRoute) {
                        if (livePolyline != null) {
                            livePolyline.remove();
                            livePolyline = null;
                        }
                        livePolylinePoints.clear();
                    }
                    sharedPreferences.enableShowLiveRoute(newState);
                    return true;
                }
                return false;
            });
            popupMenu.show();
        });
        binding.recenterCamera.setOnClickListener(view -> {
            if (isShowingRouteHistory){
                if (nMap != null){
                    nMap.animateCamera(CameraUpdateFactory.newLatLngZoom(historyPolylinePoints.get(0),15f));
                }
            }else{
                if (latestLocation != null && nMap != null) {
                    nMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latestLocation, 17f));
                } else {
                    Toast.makeText(requireContext(), "Location not available yet", Toast.LENGTH_SHORT).show();
                }
            }
        });
        binding.showRouteHistory.setOnClickListener(view -> {
            if (!isShowingRouteHistory){
                showDateTimeBottomSheet();
            }else{
                stopHistoryTracking();
                observeLiveLocation();
            }

        });

    }

    private void updateLivePolyline() {
        if (livePolylinePoints.size() < 2) return;

        if (historyPolyline != null) {
            historyPolyline.remove();
            historyPolyline = null;
        }
        if (livePolyline == null) {
            PolylineOptions polylineOptions = new PolylineOptions()
                    .addAll(livePolylinePoints)
                    .width(12f)
                    .color(Color.RED)
                    .startCap(new RoundCap())
                    .endCap(new RoundCap())
                    .geodesic(true);

            livePolyline = nMap.addPolyline(polylineOptions);
        } else {
            livePolyline.setPoints(livePolylinePoints);
        }
    }

    private void observeLiveLocation() {
        if (locationObserver != null) return;

        binding.showRouteHistory.setImageResource(R.drawable.ic_route_24px);
        isShowingRouteHistory = false;

        locationObserver = locationModel -> {
            double lat = locationModel.getLatitude();
            double lng = locationModel.getLongitude();
            latestLocation = new LatLng(lat, lng);
            binding.tvLastUpdate.setText(getLastUpdateString(locationModel.getLastUpdate()));
            updateLiveMarker(latestLocation, locationModel.getAccuracy());

            if (showLiveRoute) {
                livePolylinePoints.add(latestLocation);
                updateLivePolyline();
            }
        };

        childDetailViewModel.getChildLocationDetail().observe(getViewLifecycleOwner(), locationObserver);
    }

    private void stopLocationObserver() {
        if (locationObserver != null) {
            childDetailViewModel.getChildLocationDetail().removeObserver(locationObserver);
            locationObserver = null;
        }

        if (marker != null) {
            marker.remove();
            marker = null;
        }
        if (livePolyline != null) {
            livePolyline.remove();
            livePolyline = null;
        }
        livePolylinePoints.clear();
    }
    private void stopHistoryTracking() {
        if (historyPolyline != null) {
            historyPolyline.remove();
            historyPolyline = null;
        }
        if (marker != null) {
            marker.remove();
            marker = null;
        }

        if (startMarker!=null){
            startMarker.remove();
            startMarker = null;
        }
        if (endMarker!=null){
            endMarker.remove();
            endMarker = null;
        }

        for (Marker m : arrowMarkers) {
            m.remove();
        }
        arrowMarkers.clear();
        historyPolylinePoints.clear();
        isFirstUpdate = true;
    }


    private void showRouteHistory(String date,long startTime, long endTime) {
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        if (deviceId == null || deviceId.isEmpty()) return;
        DatabaseReference historyRef = FirebaseDatabase.getInstance().getReference("users")
                .child(uid)
                .child("Children")
                .child(deviceId)
                .child("Location")
                .child("location_history")
                .child(date);
        historyRef.orderByChild("lastUpdate").startAt(startTime).endAt(endTime).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    Toast.makeText(requireContext(), "Route not found for selected date and time", Toast.LENGTH_SHORT).show();
                    return;
                }
                stopLocationObserver();
                binding.showRouteHistory.setImageResource(R.drawable.location_marker_24px);
                isShowingRouteHistory = true;
                List<LatLng> rawPoints = new ArrayList<>();
                List<Float> accuracyList = new ArrayList<>();
                for (DataSnapshot pointSnap : snapshot.getChildren()) {
                    LocationModel loc = pointSnap.getValue(LocationModel.class);
                    if (loc != null) {
                        rawPoints.add(new LatLng(loc.getLatitude(), loc.getLongitude()));
                        accuracyList.add(loc.getAccuracy());

                    }
                }
                historyPolylinePoints.clear();
                for (int i = 1; i < rawPoints.size(); i++) {
                    LatLng prev = rawPoints.get(i - 1);
                    LatLng curr = rawPoints.get(i);

                    float[] result = new float[1];
                    Location.distanceBetween(prev.latitude, prev.longitude, curr.latitude, curr.longitude, result);
                    float distance = result[0];

                    float accuracy = accuracyList.get(i);

                    int windowSize = getDynamicWindowSize(distance, accuracy);
                    Log.d("RaviKumar-MapFragment", windowSize+"");

                    int half = windowSize / 2;
                    int fromIndex = Math.max(0, i - half);
                    int toIndex = Math.min(rawPoints.size(), i + half + 1);

                    List<LatLng> temp = rawPoints.subList(fromIndex, toIndex);
                    LatLng smoothed = getSmoothedLatLng(temp, temp.size());

                    historyPolylinePoints.add(smoothed);

                    if (historyPolylinePoints.size() >= 2 && i % 10 == 0) {
                        LatLng from = historyPolylinePoints.get(historyPolylinePoints.size() - 2);
                        LatLng to = historyPolylinePoints.get(historyPolylinePoints.size() - 1);
                        addArrowMarker(from, to);
                    }
                }
                drawHistoryPolyline();
                updatePolylineWidth(nMap.getCameraPosition().zoom);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(requireContext(), "Failed to load initial route: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

    }
    int getDynamicWindowSize(float distance, float accuracy) {
        int distanceFactor = (int) (distance / 5.0f);
        int accuracyFactor = (int) (accuracy / 2.0f);

        int windowSize = distanceFactor + accuracyFactor;
        return Math.min(Math.max(windowSize, 3), 10);
    }

    private LatLng getSmoothedLatLng(List<LatLng> points, int windowSize) {
        if (points == null || points.isEmpty()) return new LatLng(0, 0);

        int size = points.size();
        windowSize = Math.min(windowSize, size);

        double latSum = 0, lngSum = 0;
        for (int i = size - windowSize; i < size; i++) {
            latSum += points.get(i).latitude;
            lngSum += points.get(i).longitude;
        }

        return new LatLng(latSum / windowSize, lngSum / windowSize);
    }
    private void drawHistoryPolyline() {
        if (historyPolylinePoints.size() < 2) return;

        if (livePolyline != null) {
            livePolyline.remove();
            livePolyline = null;
        }

        if (historyPolyline != null) {
            historyPolyline.remove();
        }
        if (startMarker != null) {
            startMarker.remove();
            startMarker = null;
        }

        if (endMarker != null) {
            endMarker.remove();
            endMarker = null;
        }

        PolylineOptions polylineOptions = new PolylineOptions()
                .addAll(historyPolylinePoints)
                .width(12f)
                .color(Color.BLUE)
                .startCap(new RoundCap())
                .endCap(new RoundCap())
                .geodesic(true);

        historyPolyline = nMap.addPolyline(polylineOptions);

        MarkerOptions startMarkerOptions = new MarkerOptions()
                .position(historyPolylinePoints.get(0))
                .title("Start")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN));

        startMarker = nMap.addMarker(startMarkerOptions);

        MarkerOptions endMarkerOptions = new MarkerOptions()
                .position(historyPolylinePoints.get(historyPolylinePoints.size() - 1))
                .title("End")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED));

        endMarker = nMap.addMarker(endMarkerOptions);
        nMap.animateCamera(CameraUpdateFactory.newLatLngZoom(historyPolylinePoints.get(0), 15f));
    }


    private void updateLiveMarker(LatLng latestPoint, float accuracy) {
        if (marker == null) {
            MarkerOptions options = new MarkerOptions()
                    .position(latestPoint)
                    .title("Live Location")
                    .snippet("Accuracy: " + String.format(Locale.US, "%.1f", accuracy) + " m")
                    .icon(liveMarkerIcon);
            marker = nMap.addMarker(options);
        } else {
            marker.setPosition(latestPoint);
            marker.setSnippet("Accuracy: " + String.format(Locale.US, "%.1f", accuracy) + " m");
            marker.showInfoWindow();
        }
        if (isFirstUpdate) {
            nMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latestLocation, 17f));
            isFirstUpdate = false;
        }
    }

    private void updatePolylineWidth(float zoomLevel) {
        if (livePolyline == null && historyPolyline == null) return;

        float width = (zoomLevel < 10) ? 6f :
                (zoomLevel < 14) ? 8f :
                        (zoomLevel < 18) ? 10f : 14f;

        if (livePolyline != null && historyPolyline == null) {
            livePolyline.setWidth(width);
        } else if (livePolyline == null) {
            historyPolyline.setWidth(width);
        }
    }

    private void addArrowMarker(LatLng from, LatLng to) {
        float rotation = (float) SphericalUtil.computeHeading(from, to)-90f;

        MarkerOptions arrowMarker = new MarkerOptions()
                .position(to)
                .anchor(0.5f, 0.5f)
                .flat(true)
                .rotation(rotation)
                .icon(arrowIcon);

        Marker marker1 = nMap.addMarker(arrowMarker);
        arrowMarkers.add(marker1);

    }

    public Bitmap getResizedBitmap(int resourceId, int newWidth, int newHeight) {
        Bitmap originalBitmap = BitmapFactory.decodeResource(getResources(), resourceId);
        return Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, false);

    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }

    private void showDateTimeBottomSheet() {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(requireActivity());
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_date_time_picker, null);
        bottomSheetDialog.setContentView(view);
        setupHistoryLayout(view, bottomSheetDialog);
        bottomSheetDialog.show();
    }
    private void setupHistoryLayout(View view, BottomSheetDialog bottomSheetDialog) {
        TextView dateText = view.findViewById(R.id.dateText);
        TextView startTimeText = view.findViewById(R.id.startTimeText);
        TextView endTimeText = view.findViewById(R.id.endTimeText);
        CheckBox useCurrentTimeCheck = view.findViewById(R.id.useCurrentTimeCheck);
        Button btnConfirm = view.findViewById(R.id.btnConfirm);
        dateText.setOnClickListener(v -> {
            MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                    .setTitleText("Select Date")
                    .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                    .build();

            datePicker.show(getChildFragmentManager(), "DATE_PICKER");

            datePicker.addOnPositiveButtonClickListener(selection -> {
                Calendar selectedCal = Calendar.getInstance();
                selectedCal.setTimeInMillis(selection);
                selectedCal.set(Calendar.HOUR_OF_DAY, 0);
                selectedCal.set(Calendar.MINUTE, 0);
                selectedCal.set(Calendar.SECOND, 0);
                selectedCal.set(Calendar.MILLISECOND, 0);

                Calendar today = Calendar.getInstance();
                today.set(Calendar.HOUR_OF_DAY, 0);
                today.set(Calendar.MINUTE, 0);
                today.set(Calendar.SECOND, 0);
                today.set(Calendar.MILLISECOND, 0);

                if (selectedCal.after(today)) {
                    Toast.makeText(requireContext(), "You cannot select a future date", Toast.LENGTH_SHORT).show();
                    return;
                }

                selectedYear = selectedCal.get(Calendar.YEAR);
                selectedMonth = selectedCal.get(Calendar.MONTH);
                selectedDay = selectedCal.get(Calendar.DAY_OF_MONTH);

                SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
                dateText.setText(sdf.format(selectedCal.getTime()));
            });
        });

        startTimeText.setOnClickListener(v -> {
            TimePickerDialog dialog = new TimePickerDialog(requireContext(),
                    (view1, hourOfDay, minute) -> {
                        startHour = hourOfDay;
                        startMinute = minute;
                        Calendar cal = Calendar.getInstance();
                        cal.set(Calendar.HOUR_OF_DAY, hourOfDay);
                        cal.set(Calendar.MINUTE, minute);
                        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
                        startTimeText.setText(sdf.format(cal.getTime()));
                    },
                    Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
                    Calendar.getInstance().get(Calendar.MINUTE),
                    false);
            dialog.show();
        });


        endTimeText.setOnClickListener(v -> {
            if (useCurrentTimeCheck.isChecked()) {
                Toast.makeText(requireContext(), "Uncheck 'Use Current Time' to pick manually", Toast.LENGTH_SHORT).show();
                return;
            }

            TimePickerDialog dialog = new TimePickerDialog(requireContext(),
                    (view12, hourOfDay, minute) -> {
                        endHour = hourOfDay;
                        endMinute = minute;
                        Calendar cal = Calendar.getInstance();
                        cal.set(Calendar.HOUR_OF_DAY, hourOfDay);
                        cal.set(Calendar.MINUTE, minute);
                        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
                        endTimeText.setText(sdf.format(cal.getTime()));
                    },
                    Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
                    Calendar.getInstance().get(Calendar.MINUTE),
                    false);
            dialog.show();
        });

        useCurrentTimeCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
            endTimeText.setEnabled(!isChecked);
            if (isChecked) {
                Calendar now = Calendar.getInstance();
                endHour = now.get(Calendar.HOUR_OF_DAY);
                endMinute = now.get(Calendar.MINUTE);
                SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
                endTimeText.setText("Now (" + sdf.format(now.getTime()) + ")");
            }
        });

        btnConfirm.setOnClickListener(v -> {
            if (selectedYear == -1 || startHour == -1 || startMinute == -1 || (endHour == -1 && !useCurrentTimeCheck.isChecked())) {
                Toast.makeText(requireContext(), "Please select date, start and end time", Toast.LENGTH_SHORT).show();
                return;
            }

            Calendar startCal = Calendar.getInstance();
            startCal.set(selectedYear, selectedMonth, selectedDay, startHour, startMinute, 0);

            Calendar endCal = Calendar.getInstance();
            if (!useCurrentTimeCheck.isChecked()) {
                endCal.set(selectedYear, selectedMonth, selectedDay, endHour, endMinute, 0);
            }

            if (!endCal.after(startCal)) {
                Toast.makeText(requireContext(), "End time must be after start time", Toast.LENGTH_SHORT).show();
                return;
            }

            long startTimestamp = startCal.getTimeInMillis();
            long endTimestamp = endCal.getTimeInMillis();
            showRouteHistory(dateText.getText().toString().trim(), startTimestamp, endTimestamp);
            bottomSheetDialog.dismiss();
        });

    }
}
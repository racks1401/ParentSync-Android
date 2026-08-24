package com.racks.parentalcontrol.parent.models;

public class SnapshotModel {
    private String snapshot_key, snap_type, snap_url;
    private Long upload_time;

    public SnapshotModel() {
    }

    public String getSnapshot_key() {
        return snapshot_key;
    }

    private boolean isSelected = false;

    public boolean isSelected() {
        return isSelected;
    }

    public void setSelected(boolean selected) {
        isSelected = selected;
    }

    public void setSnapshot_key(String snapshot_key) {
        this.snapshot_key = snapshot_key;
    }

    public String getSnap_type() {
        return snap_type;
    }

    public void setSnap_type(String snap_type) {
        this.snap_type = snap_type;
    }

    public String getSnap_url() {
        return snap_url;
    }

    public void setSnap_url(String snap_url) {
        this.snap_url = snap_url;
    }

    public Long getUpload_time() {
        return upload_time;
    }

    public void setUpload_time(Long upload_time) {
        this.upload_time = upload_time;
    }
}

package com.racks.parentalcontrol.parent.models;

public class UserDetailModel {
    public String name;
    public String email;
    public String deviceId;

    public UserDetailModel() {
    }

    public UserDetailModel(String name, String email, String deviceId) {
        this.name = name;
        this.email = email;
        this.deviceId = deviceId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }
}

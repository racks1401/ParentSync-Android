package com.racks.parentalcontrol.parent.models;

public class ChildDetailModel {
    public String name;
    public String email;
    public String deviceId;
    public String device_model;
    private String battery_percentage;
    private String last_online;
    private String fcmToken;

    public ChildDetailModel() {
    }

    public ChildDetailModel(String name, String email, String deviceId, String battery_percentage, String last_online, String fcmToken) {
        this.name = name;
        this.email = email;
        this.deviceId = deviceId;
        this.battery_percentage = battery_percentage;
        this.last_online = last_online;
        this.fcmToken = fcmToken;
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
    public String getDevice_model() {
        return device_model;
    }

    public void setDevice_model(String device_model) {
        this.device_model = device_model;
    }

    public String getBattery_percentage() {
        return battery_percentage;
    }

    public void setBattery_percentage(String battery_percentage) {
        this.battery_percentage = battery_percentage;
    }

    public String getLast_online() {
        return last_online;
    }

    public void setLast_online(String last_online) {
        this.last_online = last_online;
    }

    public String getFcmToken() {
        return fcmToken;
    }

    public void setFcmToken(String fcmToken) {
        this.fcmToken = fcmToken;
    }
}

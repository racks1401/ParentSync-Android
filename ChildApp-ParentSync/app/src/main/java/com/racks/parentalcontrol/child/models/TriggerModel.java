package com.racks.parentalcontrol.child.models;

public class TriggerModel {
    private Boolean switchCamera, toggleAudio,toggleVideo, frontSnapshot, rearSnapshot,
            resetConnection, screenSnapshot, getCallLogs,enableRoute, showAppIcon;


    public Boolean getSwitchCamera() {
        return switchCamera;
    }

    public void setSwitchCamera(Boolean switchCamera) {
        this.switchCamera = switchCamera;
    }

    public Boolean getToggleAudio() {
        return toggleAudio;
    }

    public void setToggleAudio(Boolean toggleAudio) {
        this.toggleAudio = toggleAudio;
    }

    public Boolean getToggleVideo() {
        return toggleVideo;
    }

    public void setToggleVideo(Boolean toggleVideo) {
        this.toggleVideo = toggleVideo;
    }

    public Boolean getFrontSnapshot() {
        return frontSnapshot;
    }

    public void setFrontSnapshot(Boolean frontSnapshot) {
        this.frontSnapshot = frontSnapshot;
    }

    public Boolean getRearSnapshot() {
        return rearSnapshot;
    }

    public void setRearSnapshot(Boolean rearSnapshot) {
        this.rearSnapshot = rearSnapshot;
    }

    public Boolean getResetConnection() {
        return resetConnection;
    }

    public void setResetConnection(Boolean resetConnection) {
        this.resetConnection = resetConnection;
    }

    public Boolean getScreenSnapshot() {
        return screenSnapshot;
    }

    public void setScreenSnapshot(Boolean screenSnapshot) {
        this.screenSnapshot = screenSnapshot;
    }

    public Boolean getGetCallLogs() {
        return getCallLogs;
    }

    public void setGetCallLogs(Boolean getCallLogs) {
        this.getCallLogs = getCallLogs;
    }

    public Boolean getEnableRoute() {
        return enableRoute;
    }

    public void setEnableRoute(Boolean enableRoute) {
        this.enableRoute = enableRoute;
    }

    public Boolean getShowAppIcon() {
        return showAppIcon;
    }

    public void setShowAppIcon(Boolean showAppIcon) {
        this.showAppIcon = showAppIcon;
    }
}

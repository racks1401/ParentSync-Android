package com.racks.parentalcontrol.parent.models;

public class NotificationModel {
    String notification_key, package_name, app_name, title, text, sub_text, info_text, big_text,summary_text;
    Long timestamp;
    private boolean isSelected = false;

    public boolean isSelected() {
        return isSelected;
    }

    public void setSelected(boolean selected) {
        isSelected = selected;
    }

    public String getNotification_key() {
        return notification_key;
    }

    public void setNotification_key(String notification_key) {
        this.notification_key = notification_key;
    }

    public String getPackage_name() {
        return package_name;
    }

    public void setPackage_name(String package_name) {
        this.package_name = package_name;
    }

    public String getApp_name() {
        return app_name;
    }

    public void setApp_name(String app_name) {
        this.app_name = app_name;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getSub_text() {
        return sub_text;
    }

    public void setSub_text(String sub_text) {
        this.sub_text = sub_text;
    }

    public String getInfo_text() {
        return info_text;
    }

    public void setInfo_text(String info_text) {
        this.info_text = info_text;
    }

    public String getBig_text() {
        return big_text;
    }

    public void setBig_text(String big_text) {
        this.big_text = big_text;
    }

    public String getSummary_text() {
        return summary_text;
    }

    public void setSummary_text(String summary_text) {
        this.summary_text = summary_text;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }
}

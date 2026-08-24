package com.racks.parentalcontrol.parent.models;

public class RouteHistoryModel {
    String routeDate;
    int locationPoints;

    public RouteHistoryModel() {
    }

    public String getRouteDate() {
        return routeDate;
    }

    public RouteHistoryModel(String routeDate, int locationPoints) {
        this.routeDate = routeDate;
        this.locationPoints = locationPoints;
    }

    public void setRouteDate(String routeDate) {
        this.routeDate = routeDate;
    }

    public int getLocationPoints() {
        return locationPoints;
    }

    public void setLocationPoints(int locationPoints) {
        this.locationPoints = locationPoints;
    }
}

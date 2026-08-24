package com.racks.parentalcontrol.parent.interfaces;

import com.racks.parentalcontrol.parent.models.ChildDetailModel;

import java.util.ArrayList;

public interface NewEventListCallback {
    void onNewEventListReceived(ArrayList<ChildDetailModel> list);
    void onError(String errorMsg);
}
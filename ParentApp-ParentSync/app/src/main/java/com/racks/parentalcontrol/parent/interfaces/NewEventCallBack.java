package com.racks.parentalcontrol.parent.interfaces;

import androidx.annotation.Nullable;

import com.racks.parentalcontrol.parent.models.ChildDetailModel;
import com.racks.parentalcontrol.parent.models.DataModel;

public interface NewEventCallBack {
    void onNewEventReceived(DataModel model);
    void onNewEvenReceived(@Nullable ChildDetailModel childDetailModel);
    void onError(String err);
}

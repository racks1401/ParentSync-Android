package com.racks.parentalcontrol.parent.interfaces;

import com.racks.parentalcontrol.parent.models.LocationModel;

public interface LocationCallBack {
    void onLocationChanged(LocationModel locationModel);
    void onError(String err);
}

package com.racks.parentalcontrol.parent.interfaces;

import com.racks.parentalcontrol.parent.services.MyForegroundService;

public interface OnServiceReadyListener {
    void onServiceReady(MyForegroundService service);
}

package com.racks.parentalcontrol.parent.models;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class ChildDetailViewModel extends ViewModel {

    private final MutableLiveData<ChildDetailModel> childDetailModelLiveData = new MutableLiveData<>();
    private final MutableLiveData<LocationModel> childLocationModelLiveData = new MutableLiveData<>();

    public LiveData<ChildDetailModel> getChildDetail(){
        return childDetailModelLiveData;
    }

    public LiveData<LocationModel> getChildLocationDetail(){
        return childLocationModelLiveData;
    }

    public void setChildDetail(ChildDetailModel childDetail){
        childDetailModelLiveData.setValue(childDetail);
    }
    public void setChildLocationDetail(LocationModel locationModel){
        childLocationModelLiveData.setValue(locationModel);
    }


}

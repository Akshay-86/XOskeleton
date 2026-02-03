package com.example.XOskeleton;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class ExoViewModel extends ViewModel {
    // Live Data containers that Fragments can watch
    public final MutableLiveData<Float> voltage = new MutableLiveData<>();
    public final MutableLiveData<Float> current = new MutableLiveData<>();
    public final MutableLiveData<Float> speed = new MutableLiveData<>();
}
package com.example.facedetectionapp.activities.viewModels

import android.app.Application
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class CameraXViewModel(application: Application) : AndroidViewModel(application) {
    private val cameraProviderLiveData: MutableLiveData<ProcessCameraProvider> = MutableLiveData()
    val processCameraProvider: LiveData<ProcessCameraProvider>
        get() {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(getApplication())
            cameraProviderFuture.addListener(
                {
                    try {
                        cameraProviderLiveData.setValue(cameraProviderFuture.get())
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, ContextCompat.getMainExecutor(getApplication())
            )
            return cameraProviderLiveData
        }
}
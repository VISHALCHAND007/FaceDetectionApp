package com.example.facedetectionapp

import androidx.lifecycle.ViewModel

class PermissionViewModel: ViewModel() {
    val visiblePermissionDialogQueue = mutableListOf<String>()

    fun dialogDismiss() {
        visiblePermissionDialogQueue.removeFirst()
    }

    fun onPermissionResult(
        permission: String,
        isGranted: Boolean
    ) {
        if(!isGranted && !visiblePermissionDialogQueue.contains(permission)) {
            visiblePermissionDialogQueue.add(permission)
        }
    }
}
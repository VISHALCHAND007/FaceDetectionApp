package com.example.facedetectionapp.utils

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
import androidx.core.content.ContextCompat

fun Context.isPermissionGranted(permission: String): Boolean {
    return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
inline fun Context.customPermissionRequest(title: String,message: String, crossinline positive: () -> Unit,) {
    AlertDialog.Builder(this)
        .setTitle(title)
        .setMessage("")
        .setPositiveButton("Allow") {dialog, which ->
            positive.invoke()
        }
        .setNegativeButton("Cancel") {_,_  ->

        }.show()
}
fun Context.openPermissionSetting() {
    Intent(ACTION_APPLICATION_DETAILS_SETTINGS).also {
        val uri = Uri.fromParts("package", packageName, null)
        it.data = uri
        startActivity(it)
    }
}
package com.example.facedetectionapp.utils

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
import androidx.core.content.ContextCompat

fun Context.isPermissionGranted(permission: String): Boolean {
    return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}

inline fun customPermissionRequest(
    context: Context,
    permissionTextProvider: PermissionTextProvider,
    isPermanentlyDeclined: Boolean,
    crossinline onDismiss: () -> Unit,
    crossinline onOkClick: () -> Unit,
    crossinline onGoToAppSettingsClick: () -> Unit
) {
    val alertDialogBuilder = AlertDialog.Builder(context)
    alertDialogBuilder.apply {
        setMessage(permissionTextProvider.getDescription(isPermanentlyDeclined))
        setTitle("Permission Required")
        setPositiveButton(if (isPermanentlyDeclined) "Grant Permission" else "OK") { _, _ ->
            if (isPermanentlyDeclined) {
                onGoToAppSettingsClick()
            } else {
                onOkClick()
            }
        }
        setNegativeButton("Cancel") { dialog: DialogInterface?, _: Int ->
            dialog?.dismiss()
            onDismiss()
        }
    }

    val alertDialog = alertDialogBuilder.create()
    alertDialog.show()
}

class CameraPermissionTextProvider : PermissionTextProvider {
    override fun getDescription(isPermanentlyDeclined: Boolean): String {
        return if (isPermanentlyDeclined) {
            "\"It seems like you have permanently declined the camera permission.\" +\n" +
                    "                    \"You can go to the app settings to grant it now.\""
        } else {
            "The camera permission is required so that your friends can see you over the video call."
        }
    }
}

class ReadStoragePermissionTextProvider : PermissionTextProvider {
    override fun getDescription(isPermanentlyDeclined: Boolean): String {
        return if (isPermanentlyDeclined) {
            "It seems like you have permanently declined the read storage permission." +
                    "You can go to the app settings to grant it now."
        } else {
            "The read storage permission is required to read the images file so that we can verify your face properly."
        }
    }
}

class WriteStoragePermissionTextProvider : PermissionTextProvider {
    override fun getDescription(isPermanentlyDeclined: Boolean): String {
        return if (isPermanentlyDeclined) {
            "It seems like you have permanently declined the write storage permission." +
                    "You can go to the app settings to grant it now."
        } else {
            "The write storage permission is required to write the image file into you device."
        }
    }

}
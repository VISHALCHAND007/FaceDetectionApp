package com.example.facedetectionapp.activities

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.example.facedetectionapp.PermissionViewModel
import com.example.facedetectionapp.databinding.ActivityMainBinding
import com.example.facedetectionapp.utils.CameraPermissionTextProvider
import com.example.facedetectionapp.utils.Constants
import com.example.facedetectionapp.utils.ReadStoragePermissionTextProvider
import com.example.facedetectionapp.utils.WriteStoragePermissionTextProvider
import com.example.facedetectionapp.utils.customPermissionRequest
import com.example.facedetectionapp.utils.isPermissionGranted

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val cameraPermission = android.Manifest.permission.CAMERA
    private val readPermission = android.Manifest.permission.READ_EXTERNAL_STORAGE
    private val storagePermission = android.Manifest.permission.WRITE_EXTERNAL_STORAGE
    private lateinit var permsViewModel: PermissionViewModel
    private lateinit var dialogQueue: MutableList<String>
    private val permissionToRequest = arrayOf(
        readPermission,
        storagePermission,
        cameraPermission
    )

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
            permissionToRequest.forEach { permission ->
                permsViewModel.onPermissionResult(
                    permission = permission,
                    isGranted = perms[permission] == true
                )
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        init()
    }

    private fun init() {
        initTasks()
        initListeners()
    }

    private fun initTasks() {
        permsViewModel = ViewModelProvider(this@MainActivity)[PermissionViewModel::class.java]
        dialogQueue = permsViewModel.visiblePermissionDialogQueue

        checkPermissionsGranted()

        startCamera()
    }

    private fun checkPermissionsGranted() {
        if (ContextCompat.checkSelfPermission(this, cameraPermission) != PackageManager.PERMISSION_GRANTED
            && ContextCompat.checkSelfPermission(this, readPermission) != PackageManager.PERMISSION_GRANTED
            && ContextCompat.checkSelfPermission(this, storagePermission) != PackageManager.PERMISSION_GRANTED)
         {
//            requestPermission()
             requestPermissionLauncher.launch(permissionToRequest)
        }
    }

    private fun startCamera() {
        if (isPermissionGranted(cameraPermission)) {
            //start camera
            startCameraActivity()
        } else {
            requestPermission()
        }
    }

    private fun startCameraActivity() {
        CameraActivity.start(this)
    }

    private fun requestPermission() {
        dialogQueue
            .reversed()
            .forEach { permission ->
                customPermissionRequest(
                    context = this@MainActivity,
                    permissionTextProvider = when (permission) {
                        android.Manifest.permission.CAMERA -> {
                            CameraPermissionTextProvider()
                        }

                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE -> {
                            WriteStoragePermissionTextProvider()
                        }

                        android.Manifest.permission.READ_EXTERNAL_STORAGE -> {
                            ReadStoragePermissionTextProvider()
                        }

                        else -> return@forEach
                    },
                    isPermanentlyDeclined = shouldShowRequestPermissionRationale(permission),
                    onDismiss = {
                        permsViewModel.dialogDismiss()
                    },
                    onOkClick = {
                        permsViewModel.dialogDismiss()
                        requestPermissionLauncher.launch(arrayOf(permission))
                    }
                ) {
                    //open settings
                    openAppSettings()
                }
            }
    }


    private fun initListeners() {

    }

    override fun onResume() {
        startCamera()
        super.onResume()
    }
}

fun Activity.openAppSettings() {
    Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null)
    ).also(::startActivity)
}

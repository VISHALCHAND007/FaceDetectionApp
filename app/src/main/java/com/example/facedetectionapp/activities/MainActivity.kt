package com.example.facedetectionapp.activities

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.facedetectionapp.databinding.ActivityMainBinding
import com.example.facedetectionapp.utils.customPermissionRequest
import com.example.facedetectionapp.utils.isPermissionGranted
import com.example.facedetectionapp.utils.openPermissionSetting

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val cameraPermission = android.Manifest.permission.CAMERA
    private val readPermission = android.Manifest.permission.READ_EXTERNAL_STORAGE


    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                //open camera
                startCameraActivity()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        if(ContextCompat.checkSelfPermission(this, readPermission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(readPermission), 10)
        }
        init()
    }

    private fun init() {
        initTasks()
        initListeners()
    }

    private fun initTasks() {
        startCamera()
    }

    private fun startCamera() {
        if (isPermissionGranted(cameraPermission)) {
            //start camera
            startCameraActivity()
        } else {
            requestCameraPermission()
        }
    }

    private fun startCameraActivity() {
        CameraActivity.start(this)
    }

    private fun requestCameraPermission() {
        when {
            shouldShowRequestPermissionRationale(cameraPermission) -> {
                customPermissionRequest(
                    "Camera Permission Required",
                    "Without giving permission we can't open the camera!!"
                ) {
                    openPermissionSetting()
                }
            }

            else -> {
                requestPermissionLauncher.launch(cameraPermission)
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

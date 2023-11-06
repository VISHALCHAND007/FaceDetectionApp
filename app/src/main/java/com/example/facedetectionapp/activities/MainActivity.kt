package com.example.facedetectionapp.activities

import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.facedetectionapp.databinding.ActivityMainBinding
import utils.Constants.Companion.currentPhotoPath
import utils.cameraPermissionRequest
import utils.isPermissionGranted
import utils.openPermissionSetting
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val cameraPermission = android.Manifest.permission.CAMERA
    private val writePermission = android.Manifest.permission.WRITE_EXTERNAL_STORAGE

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                //open camera
                startCameraActivity()
            }
        }

    private val takePictureContract =
        registerForActivityResult(ActivityResultContracts.TakePicture()) {
//        val imageFile = File(currentPhotoPath)
//        // Save the image to the gallery
//        MediaStore.Images.Media.insertImage(contentResolver, imageFile.absolutePath, imageFile.name, "")
            startCamera()
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
        startCamera()
    }

    private fun startCamera() {
        if(isPermissionGranted(cameraPermission)) {
            //start camera
            startCameraActivity()
        } else {
            requestCameraPermission()
        }
    }
    private fun startCameraActivity() {
        CameraActivity.start(this) {

        }
    }
    private fun requestCameraPermission() {
        when {
            shouldShowRequestPermissionRationale(cameraPermission) -> {
                cameraPermissionRequest {
                    openPermissionSetting()
                }
            } else -> {
                requestPermissionLauncher.launch(cameraPermission)
            }
        }
    }

    private fun bindCamera() {

    }



    private fun initListeners() {

    }
}

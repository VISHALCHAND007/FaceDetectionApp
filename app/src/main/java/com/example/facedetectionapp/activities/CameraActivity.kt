package com.example.facedetectionapp.activities

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import android.window.OnBackInvokedDispatcher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import com.example.facedetectionapp.databinding.ActivityCameraBinding
import com.example.facedetectionapp.utils.customPermissionRequest
import com.example.facedetectionapp.utils.faceDetection.FaceBox
import com.example.facedetectionapp.utils.faceDetection.FaceDetectorHelper
import com.example.facedetectionapp.utils.isPermissionGranted
import com.example.facedetectionapp.utils.openPermissionSetting
import com.example.facedetectionapp.viewModels.CameraXViewModel
import com.google.mediapipe.tasks.vision.core.RunningMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCameraBinding
    private lateinit var cameraSelector: CameraSelector
    private lateinit var processCameraProvider: ProcessCameraProvider
    private lateinit var cameraPreview: Preview
    private lateinit var imageAnalysis: ImageAnalysis
    private val cameraxViewModel = viewModels<CameraXViewModel>()
    private lateinit var faceDetectorHelper: FaceDetectorHelper
    private lateinit var imgProxy: ImageProxy
    private lateinit var imageCapture: ImageCapture
    private val storagePermission = android.Manifest.permission.WRITE_EXTERNAL_STORAGE

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                //open camera
                requestStoragePermission()
            }
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)
        init()
    }

    private fun init() {
        initTasks()
    }


    private fun initTasks() {
        cameraSelector =
            CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_FRONT).build()
        cameraxViewModel.value.processCameraProvider.observe(this) { provider ->
            processCameraProvider = provider
            bindCameraPreview()
            bindInputAnalyser()
        }
    }

    private fun bindCameraPreview() {
        cameraPreview = Preview.Builder()
            .setTargetRotation(binding.cameraPreview.display.rotation)
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .build()
        cameraPreview.setSurfaceProvider(binding.cameraPreview.surfaceProvider)
        try {
            processCameraProvider.bindToLifecycle(this, cameraSelector, cameraPreview)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun bindInputAnalyser() {
        //initialize the face detector
        val backgroundExecutor = Executors.newSingleThreadExecutor()
        backgroundExecutor.execute {
            faceDetectorHelper = FaceDetectorHelper(
                context = this,
                threshold = FaceDetectorHelper.THRESHOLD_DEFAULT,
                currentDelegate = FaceDetectorHelper.DELEGATE_CPU,
                runningMode = RunningMode.LIVE_STREAM,
                faceDetectorListener = object : FaceDetectorHelper.DetectorListener {
                    override fun onError(error: String, errorCode: Int) {

                    }

                    override fun onResults(resultBundle: FaceDetectorHelper.ResultBundle) {
                        var imageCount = 0
                        //first clear the existing boxes
                        binding.faceBoxOverlay.clear()

                        //drawing the rectangles
                        val detections = resultBundle.results[0].detections()
                        imageCount++
                        detections?.forEach {
                            val box = FaceBox(
                                binding.faceBoxOverlay,
                                imgProxy.cropRect,
                                it.boundingBox()
                            )
                            binding.faceBoxOverlay.add(box)
                        }
                        if (imageCount == 1) {
//                            clickImage()
                            imageCount--
                        }
                    }
                }
            )
        }
// ImageAnalysis. Using RGBA 8888 to match how our models work
        imageAnalysis = ImageAnalysis.Builder()
            .setTargetRotation(binding.cameraPreview.display.rotation)
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()

        var cameraExecutor = Executors.newSingleThreadExecutor()
        imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
            imgProxy = imageProxy
            detectFace(imgProxy)
        }
        try {
            processCameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun detectFace(imageProxy: ImageProxy) {
        faceDetectorHelper.detectLivestreamFrame(
            imageProxy = imageProxy,
        )
    }

    companion object {
        fun start(context: Context) {
            Intent(context, CameraActivity::class.java).also {
                context.startActivity(it)
            }
        }
    }

    private fun clickImage() {
        CoroutineScope(Dispatchers.IO).launch {
            if (isPermissionGranted(storagePermission)) {
                val name =
                    "${Environment.getExternalStorageDirectory()} + ${System.currentTimeMillis()}"
                val contentValues = ContentValues()
                contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "images/png")

                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P)
                    contentValues.put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        "pictures/FaceDetector"
                    )

                val outputOptions = ImageCapture.OutputFileOptions.Builder(
                    contentResolver,
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                ).build()
                imageCapture.takePicture(
                    outputOptions,
                    Executors.newSingleThreadExecutor(),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            runOnUiThread {
                                Toast.makeText(this@CameraActivity, "", Toast.LENGTH_SHORT).show()
                            }
                        }

                        override fun onError(exception: ImageCaptureException) {
                            exception.printStackTrace()
                        }
                    })
            } else {
                requestStoragePermission()
            }
        }
    }

    private fun requestStoragePermission() {
        when {
            shouldShowRequestPermissionRationale(storagePermission) -> {
                customPermissionRequest(
                    "Storage Permission Required",
                    "To store the image we require this permission."
                ) {
                    openPermissionSetting()
                }
            }

            else -> {
                requestPermissionLauncher.launch(storagePermission)
            }
        }
    }
}
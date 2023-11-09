package com.example.facedetectionapp.activities

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
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
import androidx.core.content.ContextCompat
import com.example.facedetectionapp.databinding.ActivityCameraBinding
import com.example.facedetectionapp.utils.customPermissionRequest
import com.example.facedetectionapp.utils.faceDetection.FaceBox
import com.example.facedetectionapp.utils.faceDetection.FaceDetectorHelper
import com.example.facedetectionapp.utils.isPermissionGranted
import com.example.facedetectionapp.utils.openPermissionSetting
import com.example.facedetectionapp.viewModels.CameraXViewModel
import com.google.mediapipe.tasks.vision.core.RunningMode
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
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
    private lateinit var mediaProjectionManager: MediaProjectionManager

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
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
        cameraPreview = Preview.Builder()
            .setTargetRotation(binding.cameraPreview.display.rotation)
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .build()
        cameraPreview.setSurfaceProvider(binding.cameraPreview.surfaceProvider)
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
                        Log.e("Error Saving", error)
                    }

                    override fun onResults(resultBundle: FaceDetectorHelper.ResultBundle) {
                        setFaceBoxedAndCapture(resultBundle)
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
            processCameraProvider.unbindAll()
            processCameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                imageCapture,
                cameraPreview,
                imageAnalysis,
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setFaceBoxedAndCapture(resultBundle: FaceDetectorHelper.ResultBundle) {
        //first clear the existing boxes
        binding.faceBoxOverlay.clear()

        //drawing the rectangles
        val detections = resultBundle.results[0].detections()
        detections?.forEach {
            val box = FaceBox(
                binding.faceBoxOverlay,
                imgProxy.cropRect,
                it.boundingBox()
            )
            binding.faceBoxOverlay.add(box)
        }
        //capture
        if (!detections.isNullOrEmpty()) {
//            clickImage()
//            takeSS()
        }
    }

    @SuppressLint("ServiceCast")
    private fun takeSS() {
        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val screenCaptureIntent = mediaProjectionManager.createScreenCaptureIntent()
        startActivityForResult(screenCaptureIntent, SCREEN_CAPTURE_REQUEST_CODE)

        // Step 2 and 3: Handle the result in onActivityResult
    }
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SCREEN_CAPTURE_REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null) {
            val mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)

            // Create a virtual display
            val metrics = resources.displayMetrics
            val screenWidth = metrics.widthPixels
            val screenHeight = metrics.heightPixels
            val screenDensity = metrics.densityDpi

            val imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
            val virtualDisplay = mediaProjection.createVirtualDisplay(
                "ScreenCapture",
                screenWidth,
                screenHeight,
                screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.surface,
                null,
                null
            )

            // Step 4: Capture the screen content
            imageReader.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage()
                // Process and save the image as needed
                saveScreenshot(image)
                image.close()
            }, null)
        }
    }

    private fun saveScreenshot(image: Image) {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        val screenshotBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

        // Save the bitmap or do further processing
        // Example: Save to the device's Pictures directory
        val filename = "screenshot_${System.currentTimeMillis()}.png"
        val screenshotFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), filename)

        try {
            FileOutputStream(screenshotFile).use { fos ->
                screenshotBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
            // Notify the user or perform any necessary actions
            Toast.makeText(this, "Screenshot saved to Pictures directory", Toast.LENGTH_SHORT).show()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }


    private fun detectFace(imageProxy: ImageProxy) {
        faceDetectorHelper.detectLivestreamFrame(
            imageProxy = imageProxy,
        )
    }

    companion object {
        const val SCREEN_CAPTURE_REQUEST_CODE = 101
        fun start(context: Context) {
            Intent(context, CameraActivity::class.java).also {
                context.startActivity(it)
            }
        }
    }

    private fun clickImage() {
        if (isPermissionGranted(storagePermission)) {
            val name =
                "${Environment.getExternalStorageDirectory()} + ${System.currentTimeMillis()}"
            val contentValues = ContentValues()
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")

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
            //taking picture
            imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(this@CameraActivity),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        runOnUiThread {
                            Toast.makeText(this@CameraActivity, "Image Saved.", Toast.LENGTH_SHORT)
                                .show()
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.e("Saving error==", exception.toString())
                    }
                })
        } else {
            requestStoragePermission()
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
package com.example.facedetectionapp.activities

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.View
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
import com.example.facedetectionapp.utils.faceDetection.domain.FaceBox
import com.example.facedetectionapp.utils.faceDetection.data.FaceDetectorHelper
import com.example.facedetectionapp.utils.isPermissionGranted
import com.example.facedetectionapp.utils.openPermissionSetting
import com.example.facedetectionapp.viewModels.CameraXViewModel
import com.google.mediapipe.tasks.components.containers.Detection
import com.google.mediapipe.tasks.vision.core.RunningMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
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
    private lateinit var uri: Uri

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

    @SuppressLint("RestrictedApi")
    private fun bindCameraPreview() {
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setDefaultResolution(Size(DEFAULT_WIDTH, DEFAULT_HEIGHT))
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
                        setFaceBoxesAndCapture(resultBundle)
                    }
                }
            )
        }
// ImageAnalysis. Using RGBA 8888 to match how our models work
        imageAnalysis = ImageAnalysis.Builder()
            .setTargetRotation(binding.cameraPreview.display.rotation)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()

        val cameraExecutor = Executors.newSingleThreadExecutor()
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

    private fun setFaceBoxesAndCapture(resultBundle: FaceDetectorHelper.ResultBundle) {
        //first clear the existing boxes
        binding.faceBoxOverlay.clear()

        //drawing the rectangles
        val detections = resultBundle.results[0].detections()
        setBoxes(detections)
        //capture
        if (!detections.isNullOrEmpty()) {
            if (takePictureJob == null || takePictureJob?.isCompleted == true) {
                // Start a new coroutine to take a picture every 5 seconds
                takePictureJob = CoroutineScope(Dispatchers.IO).launch {
                    while (isActive) {
                        // Take a picture
                        withContext(Dispatchers.Main) {
                            clickImage()
                        }
                        // Delay for 5 seconds before taking the next picture
                        delay(5000)
                    }
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setBoxes(detections: MutableList<Detection>) {
        //drawing the rectangles
        detections.forEach {
            val box = FaceBox(
                binding.faceBoxOverlay,
                imgProxy.cropRect,
                it.boundingBox()
            )
            binding.coordinateTv.text = "Left: ${it.boundingBox().left}," +
                    "Right: ${it.boundingBox().right}," +
                    "Top: ${it.boundingBox().top}," +
                    "Bottom: ${it.boundingBox().bottom},"
            binding.faceBoxOverlay.add(box)
        }
    }

    private fun detectFace(imageProxy: ImageProxy) {
        faceDetectorHelper.detectLivestreamFrame(
            imageProxy = imageProxy,
        )
    }

    companion object {
        const val DEFAULT_WIDTH = 1280
        const val DEFAULT_HEIGHT = 720
        var takePictureJob: Job? = null
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
                        CoroutineScope(Dispatchers.IO).launch {
                            saveImageWithOverlay(outputFileResults)
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

    private fun saveImageWithOverlay(outputFileResults: ImageCapture.OutputFileResults) {
        try {
            uri = outputFileResults.savedUri!!
            val bitmap = getBitmapFromUri(uri)
            val finalBitmap = getBitmapFromView(bitmap, binding.cameraView)
            if (finalBitmap != null) {
                saveMediaToStorage(finalBitmap)
            }
        } catch (e: Exception) {
            Log.e("Error saving: ", e.toString())
        }
    }

    private fun saveMediaToStorage(bitmap: Bitmap) {
        // Generating a file name
        val filename = "${System.currentTimeMillis()}.jpeg"

        // Output stream
        var fos: OutputStream? = null

        // For devices running android >= Q
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // getting the contentResolver
            this@CameraActivity.contentResolver?.also { resolver ->

                // Content resolver will process the content-values
                val contentValues = ContentValues().apply {

                    // putting file information in content values
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                }

                // Inserting the contentValues to
                // contentResolver and getting the Uri
                val imageUri: Uri? =
                    resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

                // Opening an outputstream with the Uri that we got
                fos = imageUri?.let { resolver.openOutputStream(it) }
            }
        } else {
            // These for devices running on android < Q
            val imagesDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val image = File(imagesDir, filename)
            fos = FileOutputStream(image)
        }

        fos?.use {
            // Finally writing the bitmap to the output stream that we opened
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
            runOnUiThread {
                Toast.makeText(
                    this@CameraActivity,
                    "Captured.",
                    Toast.LENGTH_SHORT
                ).show()
            }
            deleteFileWithUri(uri)
        }
    }

    private fun deleteFileWithUri(uri: Uri) {
        try {
            val contentResolver: ContentResolver = applicationContext.contentResolver

            // Delete the file using the content resolver
            contentResolver.delete(uri, null, null)

        } catch (e: Exception) {
            Log.e("Delete File Error", e.toString())
        }
    }

    private fun getBitmapFromView(bitmap: Bitmap?, overlay: View): Bitmap? {
        var combinedBitmap: Bitmap? = null
        try {
            if (bitmap != null) {
                val overlayBitmap =
                    Bitmap.createBitmap(overlay.width, overlay.height, Bitmap.Config.ARGB_8888)
                val overlayCanvas = Canvas(overlayBitmap)
                flipCanvasHorizontally(overlayCanvas)
                overlay.draw(overlayCanvas)

                // Create a new bitmap with the same size as the original bitmap
                combinedBitmap =
                    Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(combinedBitmap!!)

                // Draw the original bitmap
                canvas.drawBitmap(bitmap, 0f, 0f, null)

                val overlayX = (bitmap.width - overlay.width) / 2f
                val overlayY = (bitmap.height - overlay.height) / 2f

                canvas.drawBitmap(overlayBitmap, overlayX, overlayY, null)
            }
        } catch (e: Exception) {
            Log.e("Bitmap can't be generated", e.toString())
        }
        return combinedBitmap
    }

    fun flipCanvasHorizontally(canvas: Canvas) {
        val matrix = Matrix()
        matrix.setScale(-1f, 1f, canvas.width / 2f, 0f) // Flip horizontally around the center
        canvas.concat(matrix)
    }

    private fun getBitmapFromUri(uri: Uri): Bitmap? {
        var inputStream: InputStream?
        return try {

            // Use content resolver to open an input stream from the URI
            inputStream = contentResolver.openInputStream(uri)

            // Decode the input stream into a Bitmap
            val bitmap = BitmapFactory.decodeStream(inputStream)

//             Close the input stream
            inputStream?.close()

            bitmap // Return the decoded Bitmap
        } catch (e: Exception) {
            Log.e("Error Occurred==", e.toString())
            null
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

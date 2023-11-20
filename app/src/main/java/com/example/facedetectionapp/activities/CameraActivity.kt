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
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.View
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
import com.example.facedetectionapp.caching.MyLRUCache
import com.example.facedetectionapp.caching.OnBitmapAddedListener
import com.example.facedetectionapp.databinding.ActivityCameraBinding
import com.example.facedetectionapp.utils.Constants
import com.example.facedetectionapp.utils.blurFaceDetection.data.BlurFaceHelper
import com.example.facedetectionapp.utils.blurFaceDetection.model.BlurModel
import com.example.facedetectionapp.utils.blurFaceDetection.presentation.log
import com.example.facedetectionapp.utils.customPermissionRequest
import com.example.facedetectionapp.utils.faceDetection.data.FaceDetectorHelper
import com.example.facedetectionapp.utils.faceDetection.domain.FaceBox
import com.example.facedetectionapp.utils.isPermissionGranted
import com.example.facedetectionapp.utils.openPermissionSetting
import com.example.facedetectionapp.viewModels.CameraXViewModel
import com.google.android.gms.tflite.java.TfLite
import com.google.mediapipe.tasks.components.containers.Detection
import com.google.mediapipe.tasks.vision.core.RunningMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCameraBinding
    private lateinit var cameraSelector: CameraSelector
    private lateinit var processCameraProvider: ProcessCameraProvider
    private lateinit var cameraPreview: Preview
    private lateinit var imageAnalysis: ImageAnalysis
    private val cameraxViewModel = viewModels<CameraXViewModel>()
    private lateinit var faceDetectorHelper: FaceDetectorHelper
    private var imgProxy: ImageProxy? = null
    private lateinit var imageCapture: ImageCapture
    private val storagePermission = android.Manifest.permission.WRITE_EXTERNAL_STORAGE
    private lateinit var boundingBox: RectF
    private var timer = ""
    var sec = 0
    private lateinit var statusText: String
    private lateinit var myLRUCache: MyLRUCache
    private var clickImg: Boolean = true
    private lateinit var uri: Uri
    private lateinit var overlayBitmap: Bitmap
    private lateinit var imgList: ArrayList<Bitmap>
    private lateinit var blurHelper: BlurFaceHelper

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


        TfLite.initialize(this@CameraActivity)

        CoroutineScope(Dispatchers.Main).launch {
            while (true) {
                val seconds = sec % 60
                timer = String.format(Locale.getDefault(), "%02d", seconds)
                sec++
                delay(1000)
            }
        }

        init()

    }

    private fun init() {
        initElements()
        initTasks()
        initListeners()
    }

    private fun initElements() {
        blurHelper = BlurFaceHelper(this@CameraActivity)
        blurHelper.setUpInterpreter()
        //initializing elements used in caching
        // Create an instance of your custom LRU cache with a specified max size
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        val cacheSize = maxMemory / 8 // Use 1/8th of the available memory
        myLRUCache = MyLRUCache(cacheSize)
    }

    @SuppressLint("SetTextI18n")
    private fun initListeners() {
        myLRUCache.setOnBitmapAddedListener(object : OnBitmapAddedListener {
            override fun onBitmapAdded(key: String, bitmap: Bitmap) {
                //here bitmap is the cached bitmap
                log("Image Cached $sec")
                overlayBitmap = bitmap

                //clearing cache
                myLRUCache.removeBitmapFromMemoryCache(OVERLAY_IMG)

                deleteFileWithUri(uri)
                checkBlurriness(bitmap)
            }
        })
    }

    @SuppressLint("SetTextI18n")
    private fun analyseBlurriness(results: List<BlurModel>) {
        /*
                 Flow will change now first, we will check for the blur strength of the cropped image
                 and base no that action will be took:
                 i. If image is blurred -> retake the next frame with start
                 ii. If image is non-blurred -> save it to gallery and close the image proxy
        */
        val blurStrength = results[0].blurStrength
        val nonBlurStrength = results[0].nonBlurStrength
        binding.thresholdTv.text = "blur: $blurStrength===> nonBlur: $nonBlurStrength"

        statusText = "Received Results of blurriness $sec"
        log(statusText)
        if (blurStrength > nonBlurStrength) {
            //non-blur image
            log("Non-blur image")
            // Save the cropped face with overlay image from cache
            saveImage()
//            imgProxy = null
//            faceDetectorHelper.clearFaceDetector()
        } else {
            //image is blur
            log("Blur image")
            clickImg = true
            createImageProxy()
        }
    }

    private fun saveImage() {
        imgList = ArrayList()
        imgList.add(overlayBitmap)
        //cropping and saving face as well
        CoroutineScope(Dispatchers.IO).launch {
            cropAndSave(overlayBitmap)
        }
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
//                        statusText = "Face detected: $sec"
//                        log(statusText)
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

        createImageProxy()

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

    private fun createImageProxy() {
        val cameraExecutor = Executors.newSingleThreadExecutor()
        imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
            imgProxy = imageProxy
            detectFace(imageProxy)
        }
    }

    private fun setFaceBoxesAndCapture(resultBundle: FaceDetectorHelper.ResultBundle) {
        //first clear the existing boxes
        binding.faceBoxOverlay.clear()

        //drawing the rectangles
        val detections = resultBundle.results[0].detections()
        setBoxes(detections)
        //capture
        if (!detections.isNullOrEmpty() && clickImg) {
            clickImage()
            clickImg = false
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setBoxes(detections: MutableList<Detection>) {
        //drawing the rectangles
        detections.forEach {
            val box = FaceBox(
                binding.faceBoxOverlay,
                imgProxy!!.cropRect,
                it.boundingBox()
            )
            boundingBox = it.boundingBox()
//            binding.statusTv.text = "Left: ${it.boundingBox().left}," +
//                    "Right: ${it.boundingBox().right}," +
//                    "Top: ${it.boundingBox().top}," +
//                    "Bottom: ${it.boundingBox().bottom},"
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
        const val OVERLAY_IMG = "overlayImg"
        fun start(context: Context) {
            Intent(context, CameraActivity::class.java).also {
                context.startActivity(it)
            }
        }
    }

    private fun clickImage() {
        statusText = "Clicking Image $sec"
        log(statusText)

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
//                            statusText = "Image Saved $sec"
                            log(statusText)
                            cacheImageWithOverlay(outputFileResults)
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

    private fun cacheImageWithOverlay(outputFileResults: ImageCapture.OutputFileResults) {
        statusText = "Caching with facebox $sec"
        log(statusText)
        try {
            uri = outputFileResults.savedUri!!
            val bitmap = getBitmapFromUri(uri) //image from gallery
            val finalBitmap = getBitmapFromView(bitmap, binding.cameraView)
            if (finalBitmap != null) {
                //new flow save everything in cache until we get the non-blur image classified
                myLRUCache.addBitmapToMemoryCache(OVERLAY_IMG, finalBitmap)
                //continue with the listener from here
            }
        } catch (e: Exception) {
            Log.e("Error saving: ", e.toString())
        }
    }

    private suspend fun cropAndSave(originalBitmap: Bitmap) = coroutineScope {
        statusText = "Cropping face $sec"
        log(statusText)

        val width = originalBitmap.width
        val height = originalBitmap.height

        var left = width
        var right = 0
        var top = height
        var bottom = 0

        val numChunks = 2 // Adjust the number of chunks based on your requirements

        val chunkSizeX = width / numChunks
        val chunkSizeY = height / numChunks

        val deferredList = mutableListOf<Deferred<Unit>>()

        for (chunkIndexX in 0 until numChunks) {
            for (chunkIndexY in 0 until numChunks) {
                val startX = chunkIndexX * chunkSizeX
                val startY = chunkIndexY * chunkSizeY
                val endX = minOf((chunkIndexX + 1) * chunkSizeX, width)
                val endY = minOf((chunkIndexY + 1) * chunkSizeY, height)

                deferredList += async(Dispatchers.Default) {
                    for (x in startX until endX) {
                        for (y in startY until endY) {
                            if (originalBitmap.getPixel(x, y) == Constants.color) {
                                // Update bounding box within a critical section
                                synchronized(this@CameraActivity) {
                                    left = minOf(left, x)
                                    right = maxOf(right, x)
                                    top = minOf(top, y)
                                    bottom = maxOf(bottom, y)
                                }

                                // Optimize: Break out of the inner loop once the color is found
                                break
                            }
                        }
                    }
                }
            }
        }

        // Wait for all coroutines to complete
        deferredList.awaitAll()

        // Ensure that the bounding box is within the image bounds
        left = maxOf(0, left)
        right = minOf(width - 1, right)
        top = maxOf(0, top)
        bottom = minOf(height + 3, bottom + 3)

        // Create cropped bitmap
        val croppedFace = Bitmap.createBitmap(
            originalBitmap,
            left,
            top,
            right - left + 1,
            bottom - top + 1
        )

        statusText = "Cropping finished $sec"
        log(statusText)

        imgList.add(croppedFace)

        // Saving images
        saveMediaToStorage(imgList)
    }

    private fun checkBlurriness(bitmap: Bitmap) {
        statusText = "Checking blurriness $sec"
        log(statusText)
        try {
            val results = blurHelper.classify(bitmap)
            analyseBlurriness(results)
        } catch (e: Exception) {
            log(e.message.toString())
        }
    }

    private fun saveMediaToStorage(list: ArrayList<Bitmap>) {
        statusText = "Saving the cached image to gallery $sec"
        log(statusText)
        CoroutineScope(Dispatchers.IO).launch {
            list.forEach {
                val bitmap = it
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
                            put(
                                MediaStore.MediaColumns.RELATIVE_PATH,
                                Environment.DIRECTORY_PICTURES
                            )
                        }

                        // Inserting the contentValues to
                        // contentResolver and getting the Uri
                        val imageUri: Uri? =
                            resolver.insert(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                contentValues
                            )

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
                    statusText = "Saved the cached image to gallery $sec"
                    log(statusText)
                }
            }
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
                val canvas = Canvas(combinedBitmap)

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

    private fun flipCanvasHorizontally(canvas: Canvas) {
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

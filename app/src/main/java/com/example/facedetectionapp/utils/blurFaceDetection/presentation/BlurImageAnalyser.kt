package com.example.facedetectionapp.utils.blurFaceDetection.presentation

import android.graphics.Bitmap
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.facedetectionapp.utils.blurFaceDetection.data.BlurClassifier
import com.example.facedetectionapp.utils.blurFaceDetection.model.BlurModel
import kotlin.math.log

class BlurImageAnalyser(
    private val classifier: BlurClassifier,
    private val onResults: (List<BlurModel>) -> Unit
): ImageAnalysis.Analyzer {
    private var frameSkipCounter = 0
    override fun analyze(image: ImageProxy) {
        log("analysis started")
        if(frameSkipCounter % 60 ==0) {
            log("inside")
            val rotationDegrees = image.imageInfo.rotationDegrees
            val bitmap = image
                .toBitmap()
                .centerCrop(512, 512)
            val result = classifier.classify(bitmap, rotationDegrees)
            if (result != null) {
                onResults(result)
            }
        }
        frameSkipCounter++
        image.close()
    }
    private fun configImg(bitmap: Bitmap): Bitmap {

        return bitmap
    }
}
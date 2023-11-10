package com.example.facedetectionapp.utils.blurFaceDetection.presentation

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.facedetectionapp.utils.blurFaceDetection.data.BlurClassifier
import com.example.facedetectionapp.utils.blurFaceDetection.model.BlurModel

class BlurImageAnalyser(
    private val classifier: BlurClassifier,
    private val onResults: (List<BlurModel>) -> Unit
): ImageAnalysis.Analyzer {
    private var frameSkipCounter = 0
    override fun analyze(image: ImageProxy) {
        if(frameSkipCounter % 60 ==0) {
            val rotationDegrees = image.imageInfo.rotationDegrees
            val bitmap = image
                .toBitmap()
                .centerCrop(512, 512)
            val result = classifier.classify(bitmap, rotationDegrees)
            if (result != null) {
                onResults(result)
            }
        }
        image.close()
    }
}
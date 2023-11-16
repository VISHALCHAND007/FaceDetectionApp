package com.example.facedetectionapp.utils.blurFaceDetection.data

import android.content.Context
import android.graphics.Bitmap
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.facedetectionapp.utils.blurFaceDetection.model.BlurModel
import com.example.facedetectionapp.utils.blurFaceDetection.presentation.log


class BlurImageAnalyser(
    private val context: Context,
    private val onResults: (List<BlurModel>) -> Unit
) : ImageAnalysis.Analyzer {
    private var frameSkipCounter = 0

    override fun analyze(image: ImageProxy) {
            val rotationDegrees = image.imageInfo.rotationDegrees
            val imageBitmap = Bitmap.createBitmap(
                image.width,
                image.height,
                Bitmap.Config.ARGB_8888
            )
            image.use {
                imageBitmap.copyPixelsFromBuffer(image.planes[0].buffer)
            }

            try {
                val result = BlurFaceHelper(context).classify(imageBitmap, rotationDegrees)
                onResults(result)
            } catch (e: Exception) {
                log(e.message.toString()+"ye hai")
            }
        frameSkipCounter++
    }
}
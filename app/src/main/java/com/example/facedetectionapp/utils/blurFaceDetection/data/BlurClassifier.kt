package com.example.facedetectionapp.utils.blurFaceDetection.data

import android.graphics.Bitmap
import com.example.facedetectionapp.utils.blurFaceDetection.model.BlurModel

interface BlurClassifier {
    fun classify(bitmap: Bitmap): List<BlurModel>?
}
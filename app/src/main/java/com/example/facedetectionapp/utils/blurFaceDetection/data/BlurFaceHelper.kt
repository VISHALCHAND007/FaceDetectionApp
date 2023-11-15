package com.example.facedetectionapp.utils.blurFaceDetection.data

import android.graphics.Bitmap
import android.view.Surface
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.facedetectionapp.utils.blurFaceDetection.model.BlurModel
import com.example.facedetectionapp.utils.blurFaceDetection.presentation.log
import com.google.android.gms.tflite.java.TfLite
import org.tensorflow.lite.InterpreterApi
import org.tensorflow.lite.TensorFlowLite
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.core.vision.ImageProcessingOptions
import java.io.File

class BlurFaceHelper : BlurClassifier {
    private var interpreterApi: InterpreterApi? = null
    private val model = "model_blur_512.tflite"

    private fun setUpInterpreter() {
        val tfliteOptions = InterpreterApi.Options().
        setRuntime(InterpreterApi.Options.TfLiteRuntime.PREFER_SYSTEM_OVER_APPLICATION)
        interpreterApi = InterpreterApi.create(File(model), tfliteOptions)
    }

    override fun classify(bitmap: Bitmap, rotation: Int): List<BlurModel> {
        setUpInterpreter()

        log("inside classify")
        val imageProcessor = ImageProcessor.Builder().build()
        val tensorImage = imageProcessor.process(TensorImage.fromBitmap(bitmap))

        ImageProcessingOptions.builder()
            .setOrientation(getOrientationFormRotation(rotation))
            .build()
        val outputArray = FloatArray(3)
        interpreterApi?.run(tensorImage, outputArray)

        return listOf(
            BlurModel(
                blurStrength = outputArray[0],
                nonBlurStrength = outputArray[1]
            )
        )
    }

    private fun getOrientationFormRotation(rotation: Int): ImageProcessingOptions.Orientation {
        return when (rotation) {
            Surface.ROTATION_0 -> {
                ImageProcessingOptions.Orientation.RIGHT_TOP
            }

            Surface.ROTATION_90 -> {
                ImageProcessingOptions.Orientation.TOP_LEFT
            }

            Surface.ROTATION_180 -> {
                ImageProcessingOptions.Orientation.RIGHT_BOTTOM
            }

            else -> {
                ImageProcessingOptions.Orientation.BOTTOM_RIGHT
            }
        }
    }
}
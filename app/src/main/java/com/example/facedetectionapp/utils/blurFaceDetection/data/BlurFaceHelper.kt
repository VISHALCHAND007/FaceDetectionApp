package com.example.facedetectionapp.utils.blurFaceDetection.data

import android.content.Context
import android.graphics.Bitmap
import android.view.Surface
import com.example.facedetectionapp.utils.blurFaceDetection.model.BlurModel
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.core.BaseOptions
import org.tensorflow.lite.task.core.vision.ImageProcessingOptions
import org.tensorflow.lite.task.gms.vision.classifier.ImageClassifier

class BlurFaceHelper(
    private val context: Context,
    private val threshold: Float = 0.5f,
    private val maxResults: Int = 1
) : BlurClassifier {
    private var classifier: ImageClassifier? = null
    private val model = "model_blur_512.tflite"

    private fun setUpClassifier() {
        val baseOptions = BaseOptions.builder()
            .setNumThreads(2)
            .build()

        val options = ImageClassifier.ImageClassifierOptions.builder()
            .setBaseOptions(baseOptions)
            .setMaxResults(maxResults)
            .setScoreThreshold(threshold)
            .build()

        try {
            classifier = ImageClassifier.createFromFileAndOptions(
                context,
                model,
                options
            )
        } catch (e: IllegalStateException) {
            e.printStackTrace()
        }
    }

    override fun classifier(bitmap: Bitmap, rotation: Int): List<BlurModel>? {
        if (classifier == null)
            setUpClassifier()

        val imageProcessor = org.tensorflow.lite.support.image.ImageProcessor.Builder().build()
        val tensorImage = imageProcessor.process(TensorImage.fromBitmap(bitmap))

        val imageProcessingOptions = ImageProcessingOptions.builder()
            .setOrientation(getOrientationFormRotation(rotation))
            .build()
        val results = classifier?.classify(tensorImage, imageProcessingOptions)
        return results?.flatMap { classifications ->
            classifications.categories.map { category ->
                BlurModel(
                    blurStrength = category.index.toFloat(),
                    nonBlurStrength = category.index.toFloat()
                )
            }
        }
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
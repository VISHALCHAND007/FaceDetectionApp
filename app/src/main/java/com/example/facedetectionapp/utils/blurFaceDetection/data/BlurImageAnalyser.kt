package com.example.facedetectionapp.utils.blurFaceDetection.data

import android.content.Context
import android.graphics.Bitmap
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.facedetectionapp.utils.Constants
import com.example.facedetectionapp.utils.blurFaceDetection.model.BlurModel
import com.example.facedetectionapp.utils.blurFaceDetection.presentation.log
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.nio.ByteBuffer
import java.nio.ByteOrder


class BlurImageAnalyser(
    private val context: Context,
    private val onResults: (List<BlurModel>) -> Unit
) : ImageAnalysis.Analyzer {
    private var frameSkipCounter = 0

    override fun analyze(image: ImageProxy) {
        if (frameSkipCounter % 60 == 0) {
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
//                val resultBitmap: Bitmap = configImg(imageBitmap)
                val result = BlurFaceHelper(context).classify(imageBitmap, rotationDegrees)
                onResults(result)
            } catch (e: Exception) {
                log(e.message.toString())
            }
        }
        frameSkipCounter++
//        image.close()
    }




    private fun configImg(bitmap: Bitmap): Bitmap {
        // Convert Bitmap to Mat
        val inputMat = Mat()
        Utils.bitmapToMat(bitmap, inputMat)

        // Convert to RGB if not already in RGB format
        Imgproc.cvtColor(inputMat, inputMat, Imgproc.COLOR_BGR2RGB)

        // Resize the image
        val newSize = Size(Constants.desiredWidth.toDouble(), Constants.desiredHeight.toDouble())
        val resizedMat = Mat()
        Imgproc.resize(
            inputMat,
            resizedMat,
            newSize,
            0.0,
            0.0,
            Imgproc.INTER_AREA
        )

        // Convert pixel values to the range (0, 1)
        resizedMat.convertTo(resizedMat, CvType.CV_32FC4, 1.0 / 255.0)


//         Convert back to CV_8U
        resizedMat.convertTo(resizedMat, CvType.CV_8UC4)
//        Log.d("Debug==", "Mat dimensions: ${resizedMat.rows()} x ${resizedMat.cols()}")
//        Log.d("Debug==", "Mat pixel values: ${resizedMat.dump()}")

        // Create a new Bitmap with the same dimensions as resizedMat
        val resultBitmap =
            Bitmap.createBitmap(resizedMat.cols(), resizedMat.rows(), Bitmap.Config.ARGB_8888)

        //changing the image color format
//        val finalMat = Mat(resizedMat.rows(), resizedMat.cols(), CvType.CV_8UC3)
//        resizedMat.copyTo(finalMat.row(0))
        // Convert Mat to Bitmap
        Utils.matToBitmap(resizedMat, resultBitmap)
//        Utils.matToBitmap(finalMat, resultBitmap)

        return resultBitmap
    }
}
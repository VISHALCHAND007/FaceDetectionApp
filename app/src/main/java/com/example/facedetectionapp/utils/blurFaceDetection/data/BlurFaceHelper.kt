package com.example.facedetectionapp.utils.blurFaceDetection.data

import android.content.Context
import android.graphics.Bitmap
import com.example.facedetectionapp.utils.Constants
import com.example.facedetectionapp.utils.blurFaceDetection.model.BlurModel
import org.tensorflow.lite.InterpreterApi
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel


class BlurFaceHelper(private val context: Context) : BlurClassifier {
    private var interpreterApi: InterpreterApi? = null
    private val model = "model_blur_512.tflite"
    private  val CHANNELS = 3
    private val BATCH_SIZE = 1

    private fun setUpInterpreter() {
        val tfliteOptions = InterpreterApi.Options()
            .setRuntime(InterpreterApi.Options.TfLiteRuntime.FROM_APPLICATION_ONLY)

        interpreterApi = InterpreterApi.create(loadModelFile(context)!!, tfliteOptions)
    }

    @Throws(IOException::class)
    private fun loadModelFile(activity: Context): ByteBuffer? {
        val fileDescriptor = activity.assets.openFd(model)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    override fun classify(bitmap: Bitmap, rotation: Int): List<BlurModel> {
        setUpInterpreter()

        val outputArray = processBitmap(bitmap)
        //test
//        val drawable = context.resources.getDrawable(R.drawable.image)
//        val width = drawable.intrinsicWidth
//        val height = drawable.intrinsicHeight
//
//        val bitmap1 = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
//        val canvas = Canvas(bitmap1)
//        drawable.setBounds(0, 0, canvas.width, canvas.height)
//        drawable.draw(canvas)
//        val outputArray = processBitmap(bitmap1)

        return listOf(
            BlurModel(
                blurStrength = outputArray[0][0],
                nonBlurStrength = outputArray[0][1]
            )
        )
    }


    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer =
            ByteBuffer.allocateDirect(BATCH_SIZE * Constants.desiredWidth * Constants.desiredHeight * CHANNELS * 4)
        byteBuffer.order(ByteOrder.nativeOrder())

        // Normalize pixel values to [0,1] and add the batch dimension
        for (i in 0 until Constants.desiredWidth) {
            for (j in 0 until Constants.desiredWidth) {
                val pixelValue = bitmap.getPixel(j, i)

                // Normalize the pixel values to [0, 1]
                val normalizedValue = ((pixelValue shr 16 and 0xFF) / 255.0).toFloat()

                // Add the pixel values to the buffer
                byteBuffer.putFloat(normalizedValue)
            }
        }

        // Add the batch dimension
        byteBuffer.rewind()
        return byteBuffer
    }

    private fun processBitmap(bitmap: Bitmap): Array<FloatArray> {
        // Resize the image to (512, 512)
        val resizedBitmap = Bitmap.createScaledBitmap(
            bitmap,
            Constants.desiredWidth,
            Constants.desiredHeight,
            true
        )

        // Convert Bitmap to ByteBuffer
        val inputBuffer = convertBitmapToByteBuffer(resizedBitmap)

        // Run inference
        val outputArray = Array(1) {
            FloatArray(
                2
            )
        }
        interpreterApi?.run(inputBuffer, outputArray)
        return outputArray
    }
}
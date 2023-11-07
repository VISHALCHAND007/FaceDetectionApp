package com.example.facedetectionapp.utils.faceDetection

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.media.FaceDetector.Face

class FaceBox(
    faceBoxOverlay: FaceBoxOverlay,
    private val imageRect: Rect,
    boundingBox: RectF
    ): FaceBoxOverlay.Facebox(faceBoxOverlay) {
    val left = boundingBox.left
    val top = boundingBox.top * 20
    val right = boundingBox.right
    val bottom = boundingBox.bottom

    private val paint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 6.0f
    }

    override fun draw(canvas: Canvas?) {
        val rect = getBoxRectangle(
            imgRectWidth = imageRect.width().toFloat(),
            imgRectHeight = imageRect.height().toFloat(),
            faceBoundingBox = RectF(
                left, top, right, bottom
            )
        )
        canvas?.drawRect(rect, paint)
    }

}
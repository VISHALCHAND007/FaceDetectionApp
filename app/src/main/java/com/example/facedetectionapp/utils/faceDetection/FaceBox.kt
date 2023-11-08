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
    private val boundingBox: RectF
    ): FaceBoxOverlay.Facebox(faceBoxOverlay) {

    private val paint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 6.0f
    }

    override fun draw(canvas: Canvas?) {
        val rect = getBoxRectangle(
            imgRectWidth = imageRect.width().toFloat(),
            imgRectHeight = imageRect.height().toFloat(),
            faceBoundingBox = boundingBox
        )
        canvas?.drawRect(rect, paint)
    }

}
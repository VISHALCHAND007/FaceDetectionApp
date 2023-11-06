package com.example.facedetectionapp.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import java.util.jar.Attributes
import kotlin.math.ceil

open class FaceBoxOverlay(context: Context?, attributes: AttributeSet?) :
    View(context, attributes) {
    private val lock = Any()
    private val faceboxes: MutableList<Facebox> = mutableListOf()
    var mScale: Float? = null
    var mOffSetX: Float? = null
    var mOffSetY: Float? = null

    abstract class Facebox(private val overlay: FaceBoxOverlay) {
        abstract fun draw(canvas: Canvas?)

        fun getBoxRectangle(
            imgRectWidth: Float,
            imgRectHeight: Float,
            faceBoundingBox: Rect
        ): RectF {
            val scaleX = overlay.width.toFloat() / imgRectHeight
            val scaleY = overlay.height.toFloat() / imgRectWidth
            val scale = scaleX.coerceAtLeast(scaleY)

            overlay.mScale = scale
            val offSetX = (overlay.width.toFloat() - ceil(imgRectHeight * scale)) / 2.0f
            val offSetY = (overlay.height.toFloat() - ceil(imgRectWidth * scale)) / 2.0f

            overlay.mOffSetX = offSetX
            overlay.mOffSetY = offSetY

            val mappedBox = RectF().apply {
                left = faceBoundingBox.right * scale + offSetX
                top = faceBoundingBox.right * scale + offSetY
                right  = faceBoundingBox.left * scale + offSetX
                bottom = faceBoundingBox.bottom * scale + offSetY
            }
            val centerX =  overlay.width.toFloat() / 2.0f
            return mappedBox.apply {
                left = centerX + (centerX - left)
                right = centerX - (right - centerX)
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            faceboxes.clear()
        }
        postInvalidate()
    }

    fun add(facebox: Facebox) {
        synchronized(lock) {
            faceboxes.add(facebox)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        synchronized(lock) {
            for (graphic in faceboxes) {
                graphic.draw(canvas)
            }
        }
    }
}
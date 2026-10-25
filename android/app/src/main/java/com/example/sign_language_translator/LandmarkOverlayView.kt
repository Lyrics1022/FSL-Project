package com.example.sign_language_translator

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

/**
 * Transparent HUD overlay that draws real-time hand skeleton joints and bones
 * on top of the camera viewfinder preview.
 */
class LandmarkOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class HandLandmarks(
        val points: List<PointF>,
        val isLeft: Boolean
    )

    private var hands: List<HandLandmarks> = emptyList()
    private var imageWidth: Int = 480
    private var imageHeight: Int = 640
    private var isFrontCamera: Boolean = true

    // Connections between the 21 MediaPipe hand landmarks (bones)
    companion object {
        val HAND_CONNECTIONS = arrayOf(
            // Thumb
            Pair(0, 1), Pair(1, 2), Pair(2, 3), Pair(3, 4),
            // Index
            Pair(0, 5), Pair(5, 6), Pair(6, 7), Pair(7, 8),
            // Middle
            Pair(0, 9), Pair(9, 10), Pair(10, 11), Pair(11, 12),
            // Ring
            Pair(0, 13), Pair(13, 14), Pair(14, 15), Pair(15, 16),
            // Pinky
            Pair(0, 17), Pair(17, 18), Pair(18, 19), Pair(19, 20),
            // Palm base
            Pair(5, 9), Pair(9, 13), Pair(13, 17)
        )
    }

    // Paint for bone connections (cyan/teal glow)
    private val bonePaint = Paint().apply {
        color = Color.parseColor("#8000F5D4") // Semi-transparent bright teal
        strokeWidth = 6f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    // Paint for joint nodes (knuckles, fingertips)
    private val jointPaint = Paint().apply {
        color = Color.parseColor("#00F5D4") // Vibrant teal
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // Paint for fingertip highlight
    private val tipPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    /**
     * Updates the landmarks to draw and triggers a screen repaint.
     */
    fun updateLandmarks(
        detectedHands: List<HandLandmarks>,
        srcWidth: Int,
        srcHeight: Int,
        isFront: Boolean
    ) {
        this.hands = detectedHands
        this.imageWidth = srcWidth
        this.imageHeight = srcHeight
        this.isFrontCamera = isFront
        postInvalidate()
    }

    fun clear() {
        this.hands = emptyList()
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (hands.isEmpty() || width == 0 || height == 0) return

        // Compute aspect-fill scale factors matching PreviewView's fillCenter
        val scaleX = width.toFloat() / imageWidth.toFloat()
        val scaleY = height.toFloat() / imageHeight.toFloat()
        val scale = max(scaleX, scaleY)

        val offsetX = (width - imageWidth * scale) / 2f
        val offsetY = (height - imageHeight * scale) / 2f

        for (hand in hands) {
            val pts = hand.points
            if (pts.size < 21) continue

            // 1. Draw bone lines
            for (conn in HAND_CONNECTIONS) {
                val start = pts[conn.first]
                val end = pts[conn.second]

                val x1 = transformX(start.x, scale, offsetX)
                val y1 = start.y * imageHeight * scale + offsetY
                val x2 = transformX(end.x, scale, offsetX)
                val y2 = end.y * imageHeight * scale + offsetY

                canvas.drawLine(x1, y1, x2, y2, bonePaint)
            }

            // 2. Draw joint dots
            for (i in pts.indices) {
                val pt = pts[i]
                val cx = transformX(pt.x, scale, offsetX)
                val cy = pt.y * imageHeight * scale + offsetY

                // Fingertips (4, 8, 12, 16, 20) get white core
                if (i in intArrayOf(4, 8, 12, 16, 20)) {
                    canvas.drawCircle(cx, cy, 9f, jointPaint)
                    canvas.drawCircle(cx, cy, 4f, tipPaint)
                } else if (i == 0) {
                    // Wrist
                    canvas.drawCircle(cx, cy, 10f, jointPaint)
                } else {
                    canvas.drawCircle(cx, cy, 6f, jointPaint)
                }
            }
        }
    }

    private fun transformX(normX: Float, scale: Float, offsetX: Float): Float {
        val pixelX = if (isFrontCamera) {
            // Mirror horizontally for natural front selfie viewing
            (1f - normX) * imageWidth * scale + offsetX
        } else {
            normX * imageWidth * scale + offsetX
        }
        return pixelX
    }
}

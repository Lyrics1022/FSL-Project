package com.example.sign_language_translator

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Dynamic HUD overlay displaying a dashed cyan bounding box and pill badge
 * that pops up ONLY when the user's hand(s) are detected in the camera frame.
 *
 * Automatically expands into a large framing rectangle when both hands are present
 * to ensure both hands remain comfortably framed during two-handed signs.
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

    // Density helpers
    private val density = context.resources.displayMetrics.density
    private fun dp(v: Float): Float = v * density
    @Suppress("DEPRECATION")
    private fun sp(v: Float): Float = v * context.resources.displayMetrics.scaledDensity

    // 1. Box Paint (Dashed cyan rounded rectangle)
    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(3.5f)
        pathEffect = DashPathEffect(floatArrayOf(dp(7f), dp(5f)), 0f)
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#00D2C4")
    }

    // 2. Soft glow paint behind the dashed box
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(8f)
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#3300D2C4")
    }

    // 3. Badge Background Paint (Solid cyan capsule)
    private val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#00D2C4")
    }

    // 4. Badge Text Paint
    private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#041A24")
        textSize = sp(10.5f)
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.08f
    }

    // 5. Frame Border & Status Pill Paints
    private val frameBorderCyanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.5f)
        color = Color.parseColor("#00D2C4")
    }

    private val frameBorderAmberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.5f)
        color = Color.parseColor("#F59E0B")
    }

    private val guidePillBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val guidePillTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(11f)
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.04f
    }

    enum class GuideState {
        IDLE,
        TRANSLATING,
        NO_HANDS,
        STOPPED
    }

    private var guideState: GuideState = GuideState.IDLE

    fun setGuideState(state: GuideState) {
        if (this.guideState != state) {
            this.guideState = state
            postInvalidateOnAnimation()
        }
    }

    val hasHands: Boolean
        get() = detectedHandsList.isNotEmpty()

    // Internal state
    private var detectedHandsList: List<HandLandmarks> = emptyList()
    private var srcWidth: Int = 0
    private var srcHeight: Int = 0
    private var isFrontCamera: Boolean = true
    private var lastHandDetectedTime: Long = 0L

    // Debounce duration before fading out when hands leave frame (avoids flicker)
    private val HAND_DISAPPEAR_DEBOUNCE_MS = 250L

    // Animation & Smoothing variables
    private var currentAlpha = 0f
    private var targetAlpha = 0f
    private var currentScale = 0.85f
    private var targetScale = 0.85f

    // Current smoothed box rectangle vs target rectangle
    private val currentBox = RectF()
    private val targetBox = RectF()
    private val badgeRect = RectF()
    private var isBoxInitialized = false

    // Badge label state
    private var badgeText = "HANDS DETECTED"

    fun updateLandmarks(
        detectedHands: List<HandLandmarks>,
        srcW: Int,
        srcH: Int,
        isFront: Boolean
    ) {
        val now = SystemClock.uptimeMillis()
        this.srcWidth = srcW
        this.srcHeight = srcH
        this.isFrontCamera = isFront

        if (detectedHands.isNotEmpty()) {
            this.detectedHandsList = detectedHands
            this.lastHandDetectedTime = now
            this.targetAlpha = 1f
            this.targetScale = 1f

            this.badgeText = if (detectedHands.size >= 2) {
                "2 HANDS DETECTED"
            } else {
                "HAND DETECTED"
            }
        } else {
            // Keep hands active briefly to avoid abrupt flicker on single dropped frames
            if (now - lastHandDetectedTime > HAND_DISAPPEAR_DEBOUNCE_MS) {
                this.targetAlpha = 0f
                this.targetScale = 0.85f
                this.detectedHandsList = emptyList()
            }
        }

        postInvalidateOnAnimation()
    }

    fun clear() {
        this.detectedHandsList = emptyList()
        this.targetAlpha = 0f
        this.targetScale = 0.85f
        this.currentAlpha = 0f
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // 1. Draw status overlays (Frame border & status pill badges)
        drawStatusOverlays(canvas, w, h)

        // 2. Update animation values (Alpha & Scale for pop-up effect)
        val alphaStep = (targetAlpha - currentAlpha) * 0.25f
        currentAlpha += alphaStep

        val scaleStep = (targetScale - currentScale) * 0.22f
        currentScale += scaleStep

        // If hands are completely hidden and target is 0, don't draw hand bounding box
        if (currentAlpha < 0.015f && targetAlpha == 0f) {
            currentAlpha = 0f
            return
        }

        // 2. Compute Target Bounding Box based on detected hands
        computeTargetBoundingBox(w, h)

        // 3. Smoothly interpolate currentBox toward targetBox (low-pass smoothing)
        if (!isBoxInitialized || currentBox.isEmpty) {
            currentBox.set(targetBox)
            isBoxInitialized = true
        } else {
            val lerpFactor = 0.28f
            currentBox.left += (targetBox.left - currentBox.left) * lerpFactor
            currentBox.top += (targetBox.top - currentBox.top) * lerpFactor
            currentBox.right += (targetBox.right - currentBox.right) * lerpFactor
            currentBox.bottom += (targetBox.bottom - currentBox.bottom) * lerpFactor
        }

        // 4. Configure paint alphas
        val alphaInt = (currentAlpha.coerceIn(0f, 1f) * 255).toInt()
        val glowAlphaInt = (currentAlpha.coerceIn(0f, 1f) * 70).toInt()

        boxPaint.alpha = alphaInt
        badgeBgPaint.alpha = alphaInt
        badgeTextPaint.alpha = alphaInt
        glowPaint.alpha = glowAlphaInt

        val cornerRadius = dp(24f)

        // 5. Draw overlay with pop-up scale animation centered on box
        canvas.save()
        val cx = currentBox.centerX()
        val cy = currentBox.centerY()
        canvas.scale(currentScale, currentScale, cx, cy)

        // Outer glow
        canvas.drawRoundRect(currentBox, cornerRadius, cornerRadius, glowPaint)

        // Dashed bounding rectangle
        canvas.drawRoundRect(currentBox, cornerRadius, cornerRadius, boxPaint)

        // 6. Draw "HANDS DETECTED" pill badge on top-left of the box
        val textWidth = badgeTextPaint.measureText(badgeText)
        val badgePadH = dp(12f)
        val badgeHeight = dp(22f)
        val badgeWidth = textWidth + badgePadH * 2f
        val badgeLeft = currentBox.left + dp(12f)
        val badgeTop = currentBox.top - (badgeHeight / 2f)

        badgeRect.set(badgeLeft, badgeTop, badgeLeft + badgeWidth, badgeTop + badgeHeight)
        canvas.drawRoundRect(badgeRect, badgeHeight / 2f, badgeHeight / 2f, badgeBgPaint)

        // Centered text inside badge
        val textY = badgeRect.centerY() - ((badgeTextPaint.descent() + badgeTextPaint.ascent()) / 2f)
        canvas.drawText(badgeText, badgeRect.centerX(), textY, badgeTextPaint)

        canvas.restore()

        // Continue animation loop until settled
        val needsInvalidate = abs(targetAlpha - currentAlpha) > 0.01f ||
                abs(targetScale - currentScale) > 0.01f ||
                abs(targetBox.left - currentBox.left) > 1f ||
                abs(targetBox.top - currentBox.top) > 1f
        if (needsInvalidate) {
            postInvalidateOnAnimation()
        }
    }

    private fun computeTargetBoundingBox(viewW: Float, viewH: Float) {
        val hands = detectedHandsList
        val safeMarginHorizontal = dp(14f)
        val safeMarginTop = dp(14f)
        val safeMarginBottom = viewH - dp(115f) // Leave plenty of clearance above the shutter button

        if (hands.isEmpty()) {
            // Default standby position (centered upper area)
            val defW = viewW * 0.76f
            val defH = viewH * 0.38f
            val defLeft = (viewW - defW) / 2f
            val defTop = viewH * 0.16f
            targetBox.set(defLeft, defTop, defLeft + defW, defTop + defH)
            return
        }

        // Map landmark points to screen coordinates
        var minX = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var minY = Float.MAX_VALUE
        var maxY = Float.MIN_VALUE

        for (hand in hands) {
            for (pt in hand.points) {
                val screenPt = mapPointToScreen(pt, viewW, viewH)
                if (screenPt.x < minX) minX = screenPt.x
                if (screenPt.x > maxX) maxX = screenPt.x
                if (screenPt.y < minY) minY = screenPt.y
                if (screenPt.y > maxY) maxY = screenPt.y
            }
        }

        if (minX == Float.MAX_VALUE || maxX == Float.MIN_VALUE) {
            val defW = viewW * 0.76f
            val defH = viewH * 0.38f
            val defLeft = (viewW - defW) / 2f
            val defTop = viewH * 0.16f
            targetBox.set(defLeft, defTop, defLeft + defW, defTop + defH)
            return
        }

        val handSpanW = maxX - minX
        val handSpanH = maxY - minY
        val isTwoHands = hands.size >= 2

        if (isTwoHands) {
            // BOTH HANDS IN FRAME:
            // Make the rectangle frame large enough so both hands are comfortably enclosed
            val padX = dp(42f)
            val padY = dp(36f)

            var boxL = minX - padX
            var boxR = maxX + padX
            var boxT = minY - padY
            var boxB = maxY + padY

            // Ensure a spacious minimum frame for two-handed gestures
            val minTwoHandW = viewW * 0.88f
            val minTwoHandH = viewH * 0.50f

            if ((boxR - boxL) < minTwoHandW) {
                val cx = (boxL + boxR) / 2f
                boxL = cx - minTwoHandW / 2f
                boxR = cx + minTwoHandW / 2f
            }
            if ((boxB - boxT) < minTwoHandH) {
                val cy = (boxT + boxB) / 2f
                boxT = cy - minTwoHandH / 2f
                boxB = cy + minTwoHandH / 2f
            }

            // Clamp inside safe view boundaries
            boxL = max(safeMarginHorizontal, boxL)
            boxR = min(viewW - safeMarginHorizontal, boxR)
            boxT = max(safeMarginTop, boxT)
            boxB = min(safeMarginBottom, boxB)

            targetBox.set(boxL, boxT, boxR, boxB)
        } else {
            // SINGLE HAND IN FRAME:
            // Keep a focused, well-proportioned frame around the active signing hand
            val padX = dp(32f)
            val padY = dp(28f)

            var boxL = minX - padX
            var boxR = maxX + padX
            var boxT = minY - padY
            var boxB = maxY + padY

            // Standard minimum dimensions for a single hand
            val minSingleHandW = viewW * 0.68f
            val minSingleHandH = viewH * 0.36f

            if ((boxR - boxL) < minSingleHandW) {
                val cx = (boxL + boxR) / 2f
                boxL = cx - minSingleHandW / 2f
                boxR = cx + minSingleHandW / 2f
            }
            if ((boxB - boxT) < minSingleHandH) {
                val cy = (boxT + boxB) / 2f
                boxT = cy - minSingleHandH / 2f
                boxB = cy + minSingleHandH / 2f
            }

            boxL = max(safeMarginHorizontal, boxL)
            boxR = min(viewW - safeMarginHorizontal, boxR)
            boxT = max(safeMarginTop, boxT)
            boxB = min(safeMarginBottom, boxB)

            targetBox.set(boxL, boxT, boxR, boxB)
        }
    }

    private fun drawStatusOverlays(canvas: Canvas, w: Float, h: Float) {
        val frameRect = RectF(dp(2.5f), dp(2.5f), w - dp(2.5f), h - dp(2.5f))
        val frameR = dp(18f)

        when (guideState) {
            GuideState.IDLE -> {
                // Clean viewfinder (no broken lines or window shape)
            }
            GuideState.TRANSLATING -> {
                if (detectedHandsList.isNotEmpty()) {
                    // Frame glowing cyan border
                    canvas.drawRoundRect(frameRect, frameR, frameR, frameBorderCyanPaint)
                } else {
                    // Frame amber border & No hands warning
                    canvas.drawRoundRect(frameRect, frameR, frameR, frameBorderAmberPaint)
                    drawGuidePill(canvas, dp(14f), dp(14f), "No hands", Color.parseColor("#E61E293B"), Color.parseColor("#F59E0B"), Color.parseColor("#55F59E0B"))
                }
            }
            GuideState.NO_HANDS -> {
                canvas.drawRoundRect(frameRect, frameR, frameR, frameBorderAmberPaint)
                drawGuidePill(canvas, dp(14f), dp(14f), "No hands", Color.parseColor("#E61E293B"), Color.parseColor("#F59E0B"), Color.parseColor("#55F59E0B"))
            }
            GuideState.STOPPED -> {
                drawGuidePill(canvas, dp(14f), dp(14f), "Stopped", Color.parseColor("#E62A1B24"), Color.parseColor("#F87171"), Color.parseColor("#55EF4444"))
            }
        }
    }

    private fun drawGuidePill(canvas: Canvas, x: Float, y: Float, text: String, bgColor: Int, textColor: Int, strokeColor: Int = Color.TRANSPARENT) {
        guidePillTextPaint.color = textColor
        val textWidth = guidePillTextPaint.measureText(text)
        val pillPadH = dp(14f)
        val pillHeight = dp(28f)
        val pillWidth = textWidth + pillPadH * 2f
        val pillRect = RectF(x, y, x + pillWidth, y + pillHeight)

        guidePillBgPaint.style = Paint.Style.FILL
        guidePillBgPaint.color = bgColor
        canvas.drawRoundRect(pillRect, pillHeight / 2f, pillHeight / 2f, guidePillBgPaint)

        if (strokeColor != Color.TRANSPARENT) {
            guidePillBgPaint.style = Paint.Style.STROKE
            guidePillBgPaint.strokeWidth = dp(1.2f)
            guidePillBgPaint.color = strokeColor
            canvas.drawRoundRect(pillRect, pillHeight / 2f, pillHeight / 2f, guidePillBgPaint)
        }

        val textY = pillRect.centerY() - ((guidePillTextPaint.descent() + guidePillTextPaint.ascent()) / 2f)
        canvas.drawText(text, pillRect.centerX(), textY, guidePillTextPaint)
    }

    private fun mapPointToScreen(pt: PointF, viewW: Float, viewH: Float): PointF {
        if (srcWidth <= 0 || srcHeight <= 0) {
            val px = if (isFrontCamera) (1f - pt.x) * viewW else pt.x * viewW
            return PointF(px, pt.y * viewH)
        }

        val frameAspect = srcWidth.toFloat() / srcHeight.toFloat()
        val viewAspect = viewW / viewH

        val scale = if (viewAspect > frameAspect) {
            viewW / srcWidth.toFloat()
        } else {
            viewH / srcHeight.toFloat()
        }

        val scaledW = srcWidth * scale
        val scaledH = srcHeight * scale
        val offsetX = (viewW - scaledW) / 2f
        val offsetY = (viewH - scaledH) / 2f

        // Mirror X for front camera preview
        val normX = if (isFrontCamera) (1f - pt.x) else pt.x
        val screenX = normX * scaledW + offsetX
        val screenY = pt.y * scaledH + offsetY

        return PointF(screenX, screenY)
    }
}

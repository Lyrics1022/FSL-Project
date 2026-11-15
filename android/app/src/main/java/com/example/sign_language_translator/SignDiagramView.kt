package com.example.sign_language_translator

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Clean monochrome (black & white line-art) two-step gesture diagram.
 * Illustrates Step 1 (starting hand shape & position), an animated motion arrow,
 * and Step 2 (resulting hand shape & motion finish) without color, focused purely on hands.
 */
class SignDiagramView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = context.resources.displayMetrics.density
    private fun dp(v: Float): Float = v * density
    @Suppress("DEPRECATION")
    private fun sp(v: Float): Float = v * context.resources.displayMetrics.scaledDensity

    private var currentSign: FslSignItem? = null

    // Monochrome Line-Art Paints (Black, White & Slate only)
    private val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#09101A")
    }

    private val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
        color = Color.parseColor("#1E2D3D")
    }

    private val handFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#111C28")
    }

    private val handStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.2f)
        color = Color.parseColor("#FFFFFF")
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val detailStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.2f)
        color = Color.parseColor("#94A3B8")
        strokeCap = Paint.Cap.ROUND
    }

    private val refLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.4f)
        color = Color.parseColor("#64748B")
        pathEffect = DashPathEffect(floatArrayOf(dp(4f), dp(4f)), 0f)
    }

    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.8f)
        color = Color.parseColor("#FFFFFF")
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#152232")
    }

    private val badgeStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = Color.parseColor("#334155")
    }

    private val stepTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFFFF")
        textSize = sp(10.5f)
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.05f
    }

    private val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CBD5E1")
        textSize = sp(10f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }

    private val arrowTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFFFF")
        textSize = sp(9.5f)
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val refTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#94A3B8")
        textSize = sp(8.5f)
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }

    fun setSign(sign: FslSignItem) {
        this.currentSign = sign
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cornerRadius = dp(16f)
        val cardRect = RectF(dp(2f), dp(2f), w - dp(2f), h - dp(2f))

        // 1. Clean monochrome card background
        canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, cardBgPaint)
        canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, cardBorderPaint)

        val sign = currentSign ?: return

        // 2. Define 3-section layout: Step 1 (Left), Arrow (Center), Step 2 (Right)
        val step1CenterX = w * 0.27f
        val step2CenterX = w * 0.73f
        val centerArrowX = w * 0.50f
        val handCenterY = h * 0.48f

        // Step 1 Header Badge
        drawStepBadge(canvas, step1CenterX, dp(20f), "1. START")
        // Step 2 Header Badge
        drawStepBadge(canvas, step2CenterX, dp(20f), "2. FINISH")

        // 3. Draw Step 1 Hand(s)
        drawHandIllustration(canvas, sign.step1Hand, step1CenterX, handCenterY)

        // 4. Draw Connecting Motion Arrow with Action Text
        drawCentralArrow(canvas, w * 0.38f, handCenterY, w * 0.62f, handCenterY, sign.arrowText)

        // 5. Draw Step 2 Hand(s)
        drawHandIllustration(canvas, sign.step2Hand, step2CenterX, handCenterY)

        // 6. Draw Bottom Captions
        canvas.drawText(sign.step1Caption, step1CenterX, h - dp(18f), captionPaint)
        canvas.drawText(sign.step2Caption, step2CenterX, h - dp(18f), captionPaint)
    }

    private fun drawStepBadge(canvas: Canvas, cx: Float, cy: Float, text: String) {
        val tw = stepTitlePaint.measureText(text)
        val bw = tw + dp(18f)
        val bh = dp(22f)
        val rect = RectF(cx - bw / 2f, cy - bh / 2f, cx + bw / 2f, cy + bh / 2f)

        canvas.drawRoundRect(rect, dp(6f), dp(6f), badgeBgPaint)
        canvas.drawRoundRect(rect, dp(6f), dp(6f), badgeStrokePaint)

        val ty = rect.centerY() - ((stepTitlePaint.descent() + stepTitlePaint.ascent()) / 2f)
        canvas.drawText(text, rect.centerX(), ty, stepTitlePaint)
    }

    private fun drawCentralArrow(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, text: String) {
        val midX = (x1 + x2) / 2f
        val midY = (y1 + y2) / 2f

        // Static solid directional arrow
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        canvas.drawPath(path, arrowPaint)

        // Arrow head pointing right
        val headPath = Path().apply {
            moveTo(x2, y2)
            lineTo(x2 - dp(10f), y2 - dp(6f))
            lineTo(x2 - dp(10f), y2 + dp(6f))
            close()
        }
        val headFill = Paint(arrowPaint).apply { style = Paint.Style.FILL }
        canvas.drawPath(headPath, headFill)

        // Action Label Pill
        val tw = arrowTextPaint.measureText(text)
        val bw = tw + dp(14f)
        val bh = dp(20f)
        val pillRect = RectF(midX - bw / 2f, midY - dp(24f), midX + bw / 2f, midY - dp(4f))

        canvas.drawRoundRect(pillRect, dp(6f), dp(6f), badgeBgPaint)
        canvas.drawRoundRect(pillRect, dp(6f), dp(6f), badgeStrokePaint)

        val ty = pillRect.centerY() - ((arrowTextPaint.descent() + arrowTextPaint.ascent()) / 2f)
        canvas.drawText(text, pillRect.centerX(), ty, arrowTextPaint)
    }

    private fun drawHandIllustration(canvas: Canvas, illustration: HandIllustration, cx: Float, cy: Float) {
        when (illustration) {
            HandIllustration.FLAT_PALM -> drawFlatHand(canvas, cx, cy, angle = 0f)
            HandIllustration.FLAT_PALM_CHIN -> {
                drawReferenceLine(canvas, cx, cy - dp(40f), "CHIN")
                drawFlatHand(canvas, cx, cy - dp(8f), angle = 0f)
            }
            HandIllustration.FLAT_PALM_FORWARD -> {
                drawFlatHand(canvas, cx, cy, angle = 15f)
                drawMotionRays(canvas, cx + dp(28f), cy - dp(24f))
            }
            HandIllustration.OPEN_5 -> drawOpen5Hand(canvas, cx, cy)
            HandIllustration.OPEN_5_CHIN -> {
                drawReferenceLine(canvas, cx, cy - dp(40f), "CHIN")
                drawOpen5Hand(canvas, cx, cy - dp(8f))
            }
            HandIllustration.OPEN_5_FOREHEAD -> {
                drawReferenceLine(canvas, cx, cy - dp(40f), "FOREHEAD")
                drawOpen5Hand(canvas, cx, cy - dp(8f))
            }
            HandIllustration.CLOSED_FIST -> drawFist(canvas, cx, cy, tilt = 0f)
            HandIllustration.CLOSED_FIST_TILTED -> {
                drawFist(canvas, cx, cy + dp(4f), tilt = 25f)
                drawMotionArcs(canvas, cx, cy)
            }
            HandIllustration.INDEX_POINT -> drawIndexPointHand(canvas, cx, cy)
            HandIllustration.TWO_INDEX_TOUCH -> drawTwoIndexMeet(canvas, cx, cy)
            HandIllustration.TWO_INDEX_STACKED -> drawTwoFistsStacked(canvas, cx, cy, isLocked = false)
            HandIllustration.TWO_INDEX_LOCKED -> drawTwoFistsStacked(canvas, cx, cy, isLocked = true)
            HandIllustration.Y_HAND -> drawYHand(canvas, cx, cy)
            HandIllustration.Y_HAND_CHIN -> {
                drawReferenceLine(canvas, cx, cy - dp(36f), "CHIN")
                drawYHand(canvas, cx, cy - dp(6f))
            }
            HandIllustration.TWO_Y_HANDS -> drawTwoYHands(canvas, cx, cy)
            HandIllustration.PINCH_OPEN -> drawPinchHand(canvas, cx, cy, isClosed = false)
            HandIllustration.PINCH_CLOSED -> drawPinchHand(canvas, cx, cy, isClosed = true)
            HandIllustration.BENT_FLAT -> drawBentHand(canvas, cx, cy)
            HandIllustration.A_HAND -> drawAHand(canvas, cx, cy)
            HandIllustration.V_PEACE -> {
                drawReferenceLine(canvas, cx, cy - dp(40f), "EYES")
                drawVHand(canvas, cx, cy - dp(6f))
            }
            HandIllustration.TWO_CUPPED -> drawTwoCuppedHands(canvas, cx, cy)
            HandIllustration.HORIZON_ARM_SUN_RISE -> drawHorizonSunrise(canvas, cx, cy)
            HandIllustration.HORIZON_ARM_SUN_45 -> drawHorizonSun45(canvas, cx, cy)
            HandIllustration.HORIZON_ARM_SUNSET -> drawHorizonSunset(canvas, cx, cy)
            HandIllustration.THREE_HAND -> drawFingerGroupHand(canvas, cx, cy, count = 3, withThumb = true)
            HandIllustration.FOUR_HAND -> drawFingerGroupHand(canvas, cx, cy, count = 4, withThumb = false)
            HandIllustration.V_HAND -> drawVHand(canvas, cx, cy)
            HandIllustration.C_HAND -> drawCHand(canvas, cx, cy)
            HandIllustration.L_HAND -> drawLHand(canvas, cx, cy)
            HandIllustration.F_HAND -> drawThumbTouchHand(canvas, cx, cy, touchedFinger = 1)
            HandIllustration.W_HAND -> drawThumbTouchHand(canvas, cx, cy, touchedFinger = 4)
            HandIllustration.SEVEN_HAND -> drawThumbTouchHand(canvas, cx, cy, touchedFinger = 3)
            HandIllustration.EIGHT_HAND -> drawThumbTouchHand(canvas, cx, cy, touchedFinger = 2)
            HandIllustration.INDEX_CHIN -> {
                drawReferenceLine(canvas, cx, cy - dp(40f), "CHIN")
                drawIndexPointHand(canvas, cx, cy - dp(6f))
            }
            HandIllustration.INDEX_FOREHEAD -> {
                drawReferenceLine(canvas, cx, cy - dp(40f), "FOREHEAD")
                drawIndexPointHand(canvas, cx, cy - dp(6f))
            }
            HandIllustration.INDEX_CHEEK -> {
                drawReferenceLine(canvas, cx, cy - dp(40f), "EAR / CHEEK")
                drawIndexPointHand(canvas, cx, cy - dp(6f))
            }
            HandIllustration.TWO_FLAT_HANDS -> {
                drawFlatHand(canvas, cx - dp(18f), cy, angle = -8f)
                drawFlatHand(canvas, cx + dp(18f), cy, angle = 8f)
            }
            HandIllustration.TWO_FISTS -> {
                drawFist(canvas, cx - dp(16f), cy + dp(6f), tilt = 0f)
                drawFist(canvas, cx + dp(16f), cy - dp(10f), tilt = 15f)
            }
        }
    }

    private fun drawReferenceLine(canvas: Canvas, cx: Float, cy: Float, label: String) {
        val halfW = dp(38f)
        canvas.drawLine(cx - halfW, cy, cx + halfW, cy, refLinePaint)
        canvas.drawText(label, cx, cy - dp(3f), refTextPaint)
    }

    private fun drawMotionRays(canvas: Canvas, cx: Float, cy: Float) {
        val p = detailStrokePaint
        canvas.drawLine(cx, cy, cx + dp(8f), cy - dp(8f), p)
        canvas.drawLine(cx + dp(3f), cy + dp(4f), cx + dp(12f), cy + dp(2f), p)
        canvas.drawLine(cx + dp(1f), cy + dp(12f), cx + dp(9f), cy + dp(16f), p)
    }

    private fun drawMotionArcs(canvas: Canvas, cx: Float, cy: Float) {
        val rect = RectF(cx + dp(22f), cy - dp(18f), cx + dp(34f), cy + dp(18f))
        canvas.drawArc(rect, -70f, 140f, false, detailStrokePaint)
    }

    // -------------------------------------------------------------------------
    // Anatomical Hand Vector Renderers (Procedural Crisp Line Art)
    // -------------------------------------------------------------------------

    private fun drawFlatHand(canvas: Canvas, cx: Float, cy: Float, angle: Float) {
        canvas.save()
        canvas.translate(cx, cy)
        if (angle != 0f) canvas.rotate(angle)

        val s = dp(0.72f)
        val p = Path().apply {
            // Wrist
            moveTo(-14f * s, 42f * s)
            lineTo(-14f * s, 20f * s)
            // Thumb
            quadTo(-26f * s, 12f * s, -24f * s, -6f * s)
            quadTo(-20f * s, -14f * s, -14f * s, -4f * s)
            // Index finger
            lineTo(-14f * s, -38f * s)
            quadTo(-10f * s, -45f * s, -6f * s, -38f * s)
            // Middle finger
            lineTo(-4f * s, -48f * s)
            quadTo(0f * s, -55f * s, 4f * s, -48f * s)
            // Ring finger
            lineTo(6f * s, -42f * s)
            quadTo(10f * s, -48f * s, 14f * s, -42f * s)
            // Pinky finger
            lineTo(16f * s, -34f * s)
            quadTo(20f * s, -40f * s, 23f * s, -34f * s)
            // Pinky outer edge to wrist
            lineTo(23f * s, 12f * s)
            quadTo(22f * s, 28f * s, 14f * s, 42f * s)
            close()
        }

        canvas.drawPath(p, handFillPaint)
        canvas.drawPath(p, handStrokePaint)

        // Internal finger crease lines
        canvas.drawLine(-6f * s, -38f * s, -6f * s, 6f * s, detailStrokePaint)
        canvas.drawLine(4f * s, -48f * s, 4f * s, 8f * s, detailStrokePaint)
        canvas.drawLine(14f * s, -42f * s, 14f * s, 10f * s, detailStrokePaint)
        canvas.drawLine(-14f * s, -4f * s, -6f * s, 16f * s, detailStrokePaint)

        canvas.restore()
    }

    private fun drawOpen5Hand(canvas: Canvas, cx: Float, cy: Float) {
        canvas.save()
        canvas.translate(cx, cy)
        val s = dp(0.70f)

        val p = Path().apply {
            moveTo(-12f * s, 42f * s)
            // Thumb flaring out wide left
            quadTo(-20f * s, 22f * s, -36f * s, 6f * s)
            quadTo(-44f * s, -2f * s, -36f * s, -10f * s)
            quadTo(-28f * s, -8f * s, -16f * s, 8f * s)
            // Index angled up-left
            lineTo(-20f * s, -42f * s)
            quadTo(-15f * s, -50f * s, -10f * s, -42f * s)
            // Middle straight up
            lineTo(-3f * s, -52f * s)
            quadTo(2f * s, -60f * s, 7f * s, -52f * s)
            // Ring angled up-right
            lineTo(16f * s, -44f * s)
            quadTo(22f * s, -50f * s, 26f * s, -42f * s)
            // Pinky flaring wide right
            lineTo(34f * s, -28f * s)
            quadTo(40f * s, -34f * s, 42f * s, -24f * s)
            quadTo(36f * s, 14f * s, 14f * s, 42f * s)
            close()
        }

        canvas.drawPath(p, handFillPaint)
        canvas.drawPath(p, handStrokePaint)

        // Crease lines
        canvas.drawLine(-10f * s, -35f * s, -6f * s, 12f * s, detailStrokePaint)
        canvas.drawLine(7f * s, -45f * s, 4f * s, 14f * s, detailStrokePaint)
        canvas.drawLine(22f * s, -36f * s, 12f * s, 16f * s, detailStrokePaint)

        canvas.restore()
    }

    private fun drawFist(canvas: Canvas, cx: Float, cy: Float, tilt: Float) {
        canvas.save()
        canvas.translate(cx, cy)
        if (tilt != 0f) canvas.rotate(tilt)
        val s = dp(0.74f)

        val p = Path().apply {
            // Wrist
            moveTo(-14f * s, 38f * s)
            lineTo(-16f * s, 14f * s)
            // Curled knuckles top
            quadTo(-24f * s, -8f * s, -18f * s, -20f * s)
            quadTo(-8f * s, -26f * s, 0f * s, -24f * s)
            quadTo(12f * s, -26f * s, 20f * s, -18f * s)
            quadTo(24f * s, -8f * s, 22f * s, 8f * s)
            quadTo(20f * s, 24f * s, 14f * s, 38f * s)
            close()
        }

        canvas.drawPath(p, handFillPaint)
        canvas.drawPath(p, handStrokePaint)

        // Thumb wrapped across front
        val thumbPath = Path().apply {
            moveTo(-18f * s, 10f * s)
            quadTo(-16f * s, -4f * s, -4f * s, -8f * s)
            quadTo(10f * s, -6f * s, 14f * s, 4f * s)
            quadTo(4f * s, 14f * s, -10f * s, 16f * s)
            close()
        }
        canvas.drawPath(thumbPath, handFillPaint)
        canvas.drawPath(thumbPath, handStrokePaint)

        // Curled finger division lines
        canvas.drawLine(-12f * s, -18f * s, -10f * s, -4f * s, detailStrokePaint)
        canvas.drawLine(0f * s, -24f * s, 0f * s, -6f * s, detailStrokePaint)
        canvas.drawLine(12f * s, -20f * s, 10f * s, -2f * s, detailStrokePaint)

        canvas.restore()
    }

    private fun drawIndexPointHand(canvas: Canvas, cx: Float, cy: Float) {
        canvas.save()
        canvas.translate(cx, cy)
        val s = dp(0.72f)

        val p = Path().apply {
            // Wrist
            moveTo(-14f * s, 40f * s)
            lineTo(-16f * s, 16f * s)
            // Index finger pointing straight up
            lineTo(-14f * s, -46f * s)
            quadTo(-10f * s, -54f * s, -4f * s, -46f * s)
            lineTo(-4f * s, -12f * s)
            // Curled other 3 fingers
            quadTo(8f * s, -16f * s, 18f * s, -10f * s)
            quadTo(22f * s, 4f * s, 20f * s, 14f * s)
            quadTo(18f * s, 28f * s, 14f * s, 40f * s)
            close()
        }

        canvas.drawPath(p, handFillPaint)
        canvas.drawPath(p, handStrokePaint)

        // Thumb curled over the folded fingers
        val thumb = Path().apply {
            moveTo(-14f * s, 14f * s)
            quadTo(-10f * s, 0f * s, 2f * s, -2f * s)
            quadTo(12f * s, 4f * s, 8f * s, 16f * s)
            close()
        }
        canvas.drawPath(thumb, handFillPaint)
        canvas.drawPath(thumb, handStrokePaint)

        canvas.restore()
    }

    private fun drawTwoIndexMeet(canvas: Canvas, cx: Float, cy: Float) {
        // Left index pointing up-right, Right index pointing up-left, touching in center
        canvas.save()
        canvas.translate(cx, cy)
        val s = dp(0.60f)

        // Left hand
        val leftHand = Path().apply {
            moveTo(-42f * s, 36f * s)
            lineTo(-38f * s, 12f * s)
            lineTo(-8f * s, -38f * s)
            quadTo(-2f * s, -44f * s, 0f * s, -38f * s) // fingertip touching center
            lineTo(-16f * s, -6f * s)
            quadTo(-20f * s, 18f * s, -24f * s, 36f * s)
            close()
        }
        canvas.drawPath(leftHand, handFillPaint)
        canvas.drawPath(leftHand, handStrokePaint)

        // Right hand
        val rightHand = Path().apply {
            moveTo(42f * s, 36f * s)
            lineTo(38f * s, 12f * s)
            lineTo(8f * s, -38f * s)
            quadTo(2f * s, -44f * s, 0f * s, -38f * s) // fingertip touching center
            lineTo(16f * s, -6f * s)
            quadTo(20f * s, 18f * s, 24f * s, 36f * s)
            close()
        }
        canvas.drawPath(rightHand, handFillPaint)
        canvas.drawPath(rightHand, handStrokePaint)

        // Touch impact spark lines
        canvas.drawLine(0f, -48f * s, 0f, -56f * s, detailStrokePaint)
        canvas.drawLine(-6f * s, -46f * s, -12f * s, -52f * s, detailStrokePaint)
        canvas.drawLine(6f * s, -46f * s, 12f * s, -52f * s, detailStrokePaint)

        canvas.restore()
    }

    private fun drawTwoFistsStacked(canvas: Canvas, cx: Float, cy: Float, isLocked: Boolean) {
        canvas.save()
        canvas.translate(cx, cy)
        val s = dp(0.60f)

        // Bottom base fist (held level)
        val botFistY = if (isLocked) 10f * s else 16f * s
        drawIndexPointHorizontal(canvas, 0f, botFistY, s, pointingRight = true)

        // Top striking fist
        val topFistY = if (isLocked) -16f * s else -34f * s
        drawFist(canvas, 0f, topFistY, tilt = 0f)

        if (isLocked) {
            // Impact strike rays
            canvas.drawLine(-24f * s, 0f, -32f * s, 0f, detailStrokePaint)
            canvas.drawLine(24f * s, 0f, 32f * s, 0f, detailStrokePaint)
        } else {
            // Downward strike arrow between them
            val arr = Path().apply {
                moveTo(0f, -14f * s)
                lineTo(0f, 2f * s)
            }
            canvas.drawPath(arr, arrowPaint)
        }

        canvas.restore()
    }

    private fun drawIndexPointHorizontal(canvas: Canvas, cx: Float, cy: Float, s: Float, pointingRight: Boolean) {
        canvas.save()
        canvas.translate(cx, cy)
        val dir = if (pointingRight) 1f else -1f

        val p = Path().apply {
            moveTo(-22f * s * dir, 12f * s)
            lineTo(-22f * s * dir, -12f * s)
            // Index finger pointing out
            lineTo(26f * s * dir, -12f * s)
            quadTo(32f * s * dir, -6f * s, 26f * s * dir, 0f * s)
            lineTo(8f * s * dir, 0f * s)
            quadTo(6f * s * dir, 12f * s, -12f * s * dir, 14f * s)
            close()
        }
        canvas.drawPath(p, handFillPaint)
        canvas.drawPath(p, handStrokePaint)
        canvas.restore()
    }

    private fun drawYHand(canvas: Canvas, cx: Float, cy: Float) {
        canvas.save()
        canvas.translate(cx, cy)
        val s = dp(0.72f)

        val p = Path().apply {
            // Wrist
            moveTo(-14f * s, 40f * s)
            // Thumb flaring wide left
            lineTo(-18f * s, 14f * s)
            quadTo(-32f * s, 4f * s, -38f * s, -12f * s)
            quadTo(-32f * s, -18f * s, -24f * s, -10f * s)
            lineTo(-14f * s, 4f * s)
            // Curled middle 3 fingers top
            lineTo(-12f * s, -16f * s)
            quadTo(0f * s, -20f * s, 14f * s, -14f * s)
            // Pinky flaring wide right
            lineTo(26f * s, -38f * s)
            quadTo(32f * s, -44f * s, 36f * s, -36f * s)
            lineTo(24f * s, 10f * s)
            quadTo(20f * s, 26f * s, 14f * s, 40f * s)
            close()
        }

        canvas.drawPath(p, handFillPaint)
        canvas.drawPath(p, handStrokePaint)

        // Creases on folded middle fingers
        canvas.drawLine(-6f * s, -18f * s, -6f * s, -2f * s, detailStrokePaint)
        canvas.drawLine(4f * s, -18f * s, 4f * s, 0f * s, detailStrokePaint)

        canvas.restore()
    }

    private fun drawTwoYHands(canvas: Canvas, cx: Float, cy: Float) {
        canvas.save()
        canvas.translate(cx, cy)
        val s = dp(0.56f)

        // Left Y Hand
        drawYHand(canvas, -26f * s, 0f)
        // Right Y Hand
        drawYHand(canvas, 26f * s, 0f)

        // Small downward bounce arrows
        canvas.drawLine(-26f * s, 28f * s, -26f * s, 42f * s, arrowPaint)
        canvas.drawLine(26f * s, 28f * s, 26f * s, 42f * s, arrowPaint)

        canvas.restore()
    }

    private fun drawPinchHand(canvas: Canvas, cx: Float, cy: Float, isClosed: Boolean) {
        canvas.save()
        canvas.translate(cx, cy)
        val s = dp(0.72f)

        val p = Path().apply {
            moveTo(-16f * s, 36f * s)
            lineTo(-20f * s, 10f * s)
            // Back of hand up to knuckles
            quadTo(-20f * s, -14f * s, -6f * s, -24f * s)

            if (isClosed) {
                // Beak snapped shut (touching thumb tip)
                lineTo(22f * s, -10f * s) // index/middle tip
                lineTo(22f * s, -2f * s)  // thumb tip meeting
                lineTo(0f * s, 12f * s)
            } else {
                // Beak open wide
                lineTo(20f * s, -22f * s) // top beak (fingers)
                lineTo(14f * s, -14f * s) // mouth gap
                lineTo(22f * s, 2f * s)   // bottom beak (thumb)
                lineTo(2f * s, 16f * s)
            }

            quadTo(10f * s, 26f * s, 12f * s, 36f * s)
            close()
        }

        canvas.drawPath(p, handFillPaint)
        canvas.drawPath(p, handStrokePaint)

        canvas.restore()
    }

    private fun drawBentHand(canvas: Canvas, cx: Float, cy: Float) {
        canvas.save()
        canvas.translate(cx, cy)
        val s = dp(0.72f)

        val p = Path().apply {
            moveTo(-14f * s, 40f * s)
            lineTo(-14f * s, 0f * s)
            // 90 degree forward bend
            lineTo(18f * s, 0f * s)
            quadTo(24f * s, 6f * s, 18f * s, 12f * s)
            lineTo(-4f * s, 12f * s)
            lineTo(-4f * s, 40f * s)
            close()
        }
        canvas.drawPath(p, handFillPaint)
        canvas.drawPath(p, handStrokePaint)
        canvas.restore()
    }

    private fun drawAHand(canvas: Canvas, cx: Float, cy: Float) {
        canvas.save()
        canvas.translate(cx, cy)
        val s = dp(0.72f)

        // Fist body
        val p = Path().apply {
            moveTo(-14f * s, 38f * s)
            lineTo(-16f * s, 10f * s)
            // Thumb pointing straight up along side
            lineTo(-16f * s, -26f * s)
            quadTo(-10f * s, -32f * s, -6f * s, -26f * s)
            lineTo(-6f * s, -4f * s)
            // Curled other fingers
            quadTo(12f * s, -8f * s, 18f * s, 0f * s)
            quadTo(20f * s, 16f * s, 14f * s, 38f * s)
            close()
        }
        canvas.drawPath(p, handFillPaint)
        canvas.drawPath(p, handStrokePaint)
        canvas.restore()
    }

    private fun drawVHand(canvas: Canvas, cx: Float, cy: Float) {
        canvas.save()
        canvas.translate(cx, cy)
        val s = dp(0.70f)

        val p = Path().apply {
            moveTo(-14f * s, 38f * s)
            lineTo(-16f * s, 12f * s)
            // Index finger pointing up-left
            lineTo(-20f * s, -44f * s)
            quadTo(-14f * s, -50f * s, -10f * s, -42f * s)
            // V junction
            lineTo(-4f * s, -16f * s)
            // Middle finger pointing up-right
            lineTo(10f * s, -42f * s)
            quadTo(16f * s, -48f * s, 20f * s, -40f * s)
            lineTo(12f * s, -10f * s)
            // Curled ring and pinky
            quadTo(18f * s, 6f * s, 14f * s, 38f * s)
            close()
        }
        canvas.drawPath(p, handFillPaint)
        canvas.drawPath(p, handStrokePaint)
        canvas.restore()
    }

    private fun drawTwoCuppedHands(canvas: Canvas, cx: Float, cy: Float) {
        canvas.save()
        canvas.translate(cx, cy)
        val s = dp(0.60f)

        // Left cupped hand
        val left = Path().apply {
            moveTo(-36f * s, 28f * s)
            quadTo(-42f * s, 0f * s, -24f * s, -24f * s)
            quadTo(-12f * s, -30f * s, -4f * s, -18f * s)
            quadTo(-16f * s, 2f * s, -22f * s, 28f * s)
            close()
        }
        canvas.drawPath(left, handFillPaint)
        canvas.drawPath(left, handStrokePaint)

        // Right cupped hand
        val right = Path().apply {
            moveTo(36f * s, 28f * s)
            quadTo(42f * s, 0f * s, 24f * s, -24f * s)
            quadTo(12f * s, -30f * s, 4f * s, -18f * s)
            quadTo(16f * s, 2f * s, 22f * s, 28f * s)
            close()
        }
        canvas.drawPath(right, handFillPaint)
        canvas.drawPath(right, handStrokePaint)

        canvas.restore()
    }

    private fun drawHorizonSunrise(canvas: Canvas, cx: Float, cy: Float) {
        canvas.save()
        canvas.translate(cx, cy)
        val s = dp(0.62f)

        // Horizon arm bar
        canvas.drawLine(-38f * s, 16f * s, 38f * s, 16f * s, handStrokePaint)
        canvas.drawText("HORIZON", 0f, 28f * s, refTextPaint)

        // Rising Sun Hand
        drawFlatHand(canvas, 0f, -14f * s, angle = 0f)

        // Rising motion rays
        canvas.drawLine(0f, -48f * s, 0f, -58f * s, detailStrokePaint)
        canvas.drawLine(-14f * s, -44f * s, -22f * s, -52f * s, detailStrokePaint)
        canvas.drawLine(14f * s, -44f * s, 22f * s, -52f * s, detailStrokePaint)

        canvas.restore()
    }

    private fun drawHorizonSun45(canvas: Canvas, cx: Float, cy: Float) {
        canvas.save()
        canvas.translate(cx, cy)
        val s = dp(0.62f)

        // Horizon arm bar
        canvas.drawLine(-38f * s, 16f * s, 38f * s, 16f * s, handStrokePaint)
        canvas.drawText("HORIZON", 0f, 28f * s, refTextPaint)

        // Sun hand at 45 degrees
        drawFlatHand(canvas, 8f * s, -8f * s, angle = 45f)

        canvas.restore()
    }

    private fun drawHorizonSunset(canvas: Canvas, cx: Float, cy: Float) {
        canvas.save()
        canvas.translate(cx, cy)
        val s = dp(0.62f)

        // Horizon arm bar
        canvas.drawLine(-38f * s, 0f, 38f * s, 0f, handStrokePaint)
        canvas.drawText("HORIZON", 0f, -10f * s, refTextPaint)

        // Dipping curved hand over the horizon
        val sunsetPath = Path().apply {
            moveTo(-12f * s, -14f * s)
            quadTo(14f * s, -16f * s, 18f * s, 14f * s)
            lineTo(10f * s, 22f * s)
            quadTo(6f * s, -4f * s, -12f * s, -6f * s)
            close()
        }
        canvas.drawPath(sunsetPath, handFillPaint)
        canvas.drawPath(sunsetPath, handStrokePaint)

        canvas.restore()
    }

    private fun drawFingerGroupHand(canvas: Canvas, cx: Float, cy: Float, count: Int, withThumb: Boolean) {
        canvas.save()
        canvas.translate(cx, cy)
        val s = dp(0.70f)
        val palm = RectF(-18f * s, -6f * s, 18f * s, 32f * s)
        canvas.drawRoundRect(palm, 10f * s, 10f * s, handFillPaint)
        canvas.drawRoundRect(palm, 10f * s, 10f * s, handStrokePaint)
        canvas.drawLine(-10f * s, 32f * s, -10f * s, 44f * s, handStrokePaint)
        canvas.drawLine(10f * s, 32f * s, 10f * s, 44f * s, handStrokePaint)

        val fingerXs = if (count == 3) floatArrayOf(-12f, 0f, 12f) else floatArrayOf(-13f, -4f, 5f, 14f)
        for (fx in fingerXs) {
            canvas.drawLine(fx * s, -6f * s, fx * s, -42f * s, handStrokePaint)
        }
        if (withThumb) {
            canvas.drawLine(-18f * s, 8f * s, -30f * s, -12f * s, handStrokePaint)
        }
        canvas.restore()
    }

    private fun drawThumbTouchHand(canvas: Canvas, cx: Float, cy: Float, touchedFinger: Int) {
        canvas.save()
        canvas.translate(cx, cy)
        val s = dp(0.70f)
        val palm = RectF(-18f * s, -6f * s, 18f * s, 32f * s)
        canvas.drawRoundRect(palm, 10f * s, 10f * s, handFillPaint)
        canvas.drawRoundRect(palm, 10f * s, 10f * s, handStrokePaint)
        val xs = floatArrayOf(-13f, -4f, 5f, 14f)
        for (idx in xs.indices) {
            val fingerNum = idx + 1
            if (fingerNum == touchedFinger) {
                canvas.drawLine(xs[idx] * s, -6f * s, -4f * s, 8f * s, handStrokePaint)
            } else {
                canvas.drawLine(xs[idx] * s, -6f * s, xs[idx] * s, -42f * s, handStrokePaint)
            }
        }
        // Thumb arching inward to meet touched finger
        val thumbArc = Path().apply {
            moveTo(-18f * s, 14f * s)
            quadTo(-16f * s, 0f * s, -4f * s, 8f * s)
        }
        canvas.drawPath(thumbArc, handStrokePaint)
        canvas.restore()
    }

    private fun drawCHand(canvas: Canvas, cx: Float, cy: Float) {
        canvas.save()
        canvas.translate(cx, cy)
        val s = dp(0.72f)
        val cPath = Path().apply {
            moveTo(16f * s, -28f * s)
            quadTo(-24f * s, -24f * s, -22f * s, 6f * s)
            quadTo(-20f * s, 32f * s, 14f * s, 26f * s)
            lineTo(14f * s, 16f * s)
            quadTo(-8f * s, 20f * s, -10f * s, 4f * s)
            quadTo(-10f * s, -12f * s, 16f * s, -16f * s)
            close()
        }
        canvas.drawPath(cPath, handFillPaint)
        canvas.drawPath(cPath, handStrokePaint)
        canvas.restore()
    }

    private fun drawLHand(canvas: Canvas, cx: Float, cy: Float) {
        canvas.save()
        canvas.translate(cx, cy)
        val s = dp(0.72f)
        val lPath = Path().apply {
            moveTo(-12f * s, 38f * s)
            lineTo(-12f * s, -42f * s)
            quadTo(-6f * s, -48f * s, -2f * s, -42f * s)
            lineTo(-2f * s, 4f * s)
            quadTo(14f * s, 4f * s, 16f * s, 16f * s)
            lineTo(28f * s, 16f * s)
            quadTo(34f * s, 22f * s, 28f * s, 28f * s)
            lineTo(12f * s, 28f * s)
            lineTo(12f * s, 38f * s)
            close()
        }
        canvas.drawPath(lPath, handFillPaint)
        canvas.drawPath(lPath, handStrokePaint)
        canvas.restore()
    }
}

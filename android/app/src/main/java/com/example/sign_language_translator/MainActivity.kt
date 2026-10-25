package com.example.sign_language_translator

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    /** IDLE -> PREPARING (1s delay) -> RECORDING -> PROCESSING -> RESULT */
    private enum class State { IDLE, PREPARING, RECORDING, PROCESSING, RESULT }

    private lateinit var viewFinder: PreviewView
    private lateinit var btnRecord: AppCompatButton
    private lateinit var tvResult: TextView
    private lateinit var tvConfidence: TextView
    private lateinit var progressConfidence: ProgressBar
    private lateinit var groupAccuracy: View

    private lateinit var layoutStatus: View
    private lateinit var imgTimerDot: ImageView
    private lateinit var tvTimer: TextView
    private lateinit var btnFlipCamera: ImageButton
    private lateinit var landmarkOverlay: LandmarkOverlayView

    private var state = State.IDLE
    private var lensFacing: Int = CameraSelector.LENS_FACING_FRONT
    private lateinit var cameraExecutor: ExecutorService
    private var fslClassifier: FslClassifier? = null
    private var handTracker: HandLandmarkTracker? = null
    @Volatile private var isModelReady = false

    private val RECORDING_TIME_MS = 2600L
    private val countdownRunnable = Runnable {
        if (state == State.PREPARING) {
            beginRecording()
        }
    }
    private val autoStopRunnable = Runnable {
        if (state == State.RECORDING) {
            stopRecording()
        }
    }

    // Timer / animation state
    private var startTime: Long = 0
    private val uiHandler = Handler(Looper.getMainLooper())
    private var blinkAnimation: AlphaAnimation? = null
    private var countAnimator: ValueAnimator? = null
    private var pulseAnimator: ObjectAnimator? = null

    private val timerRunnable = object : Runnable {
        override fun run() {
            val elapsedMs = SystemClock.uptimeMillis() - startTime
            val elapsedSec = elapsedMs / 1000f
            tvTimer.text = String.format(Locale.US, "%.1fs / 2.5s", minOf(2.5f, elapsedSec))
            uiHandler.postDelayed(this, 100)
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Load the neural network and hand tracker asynchronously in the background so the UI never freezes
        cameraExecutor.execute {
            try {
                fslClassifier = FslClassifier(applicationContext)
                handTracker = HandLandmarkTracker(applicationContext)
                isModelReady = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        viewFinder = findViewById(R.id.viewFinder)
        btnRecord = findViewById(R.id.btnRecord)
        tvResult = findViewById(R.id.tvResult)
        tvConfidence = findViewById(R.id.tvConfidence)
        progressConfidence = findViewById(R.id.progressConfidence)
        groupAccuracy = findViewById(R.id.groupAccuracy)

        layoutStatus = findViewById(R.id.layoutStatus)
        imgTimerDot = findViewById(R.id.imgTimerDot)
        tvTimer = findViewById(R.id.tvTimer)
        btnFlipCamera = findViewById(R.id.btnFlipCamera)
        landmarkOverlay = findViewById(R.id.landmarkOverlay)

        btnFlipCamera.setOnClickListener {
            // Spin animation feedback on tap
            btnFlipCamera.animate().rotationBy(180f).setDuration(250).start()
            toggleCamera()
        }

        setupWindow()
        setupButton()
        applyButtonState(State.IDLE)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
    }

    // ---------------------------------------------------------------------
    // Setup
    // ---------------------------------------------------------------------

    @Suppress("DEPRECATION")
    private fun setupWindow() {
        val bg = ContextCompat.getColor(this, R.color.bg)
        window.setBackgroundDrawable(ColorDrawable(bg))
        window.statusBarColor = bg
        window.navigationBarColor = bg
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupButton() {
        btnRecord.setOnClickListener {
            when (state) {
                State.IDLE, State.RESULT -> startCountdown()
                State.PREPARING -> cancelCountdown()
                State.RECORDING -> stopRecording()
                State.PROCESSING -> Unit // ignore taps while analyzing
            }
        }

        // Small "press in" effect so the button feels alive
        btnRecord.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN ->
                    v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(80).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }
            false // let the normal click handling continue
        }
    }

    // ---------------------------------------------------------------------
    // State transitions
    // ---------------------------------------------------------------------

    /** Starts a 1.0-second prep countdown so the user can position hands before recording starts */
    private fun startCountdown() {
        state = State.PREPARING
        resetResultCard()

        tvTimer.text = "Starting in 1.0s..."
        showRecordingChip()
        applyButtonState(state)
        showPlaceholder("Position your hands...")
        haptic(HapticFeedbackConstants.CLOCK_TICK)

        uiHandler.postDelayed(countdownRunnable, 1000L)
    }

    /** Allows canceling the countdown before recording actually begins */
    private fun cancelCountdown() {
        uiHandler.removeCallbacks(countdownRunnable)
        state = State.IDLE
        hideRecordingChip()
        applyButtonState(state)
        showPlaceholder("Waiting...")
        haptic(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    private fun beginRecording() {
        state = State.RECORDING

        // The recording timer starts now, when hands are ready
        tvTimer.text = "0.0s / 2.5s"
        startTime = SystemClock.uptimeMillis()
        uiHandler.post(timerRunnable)
        uiHandler.postDelayed(autoStopRunnable, RECORDING_TIME_MS)

        // Pulsing red dot
        if (blinkAnimation == null) {
            blinkAnimation = AlphaAnimation(0.2f, 1.0f).apply {
                duration = 600
                repeatMode = Animation.REVERSE
                repeatCount = Animation.INFINITE
            }
        }
        imgTimerDot.startAnimation(blinkAnimation)

        applyButtonState(state)
        showPlaceholder("Recording sign...")
        handTracker?.startRecording()
    }

    private fun stopRecording() {
        state = State.PROCESSING

        uiHandler.removeCallbacks(countdownRunnable)
        uiHandler.removeCallbacks(timerRunnable)
        uiHandler.removeCallbacks(autoStopRunnable)
        imgTimerDot.clearAnimation()
        hideRecordingChip()

        applyButtonState(state)
        showPlaceholder("Analyzing...")
        startPulse()
        haptic(HapticFeedbackConstants.CONTEXT_CLICK)

        processVideoFrames()
    }

    /** Call this when your model finishes. The accuracy pops up here. */
    private fun showResult(sign: String, confidence: Int) {
        state = State.RESULT
        stopPulse()
        applyButtonState(state)

        val color = confidenceColor(confidence)

        // 1. The translated word pops in
        tvResult.text = sign
        tvResult.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        tvResult.pivotX = 0f
        tvResult.pivotY = tvResult.height / 2f
        tvResult.alpha = 0f
        tvResult.scaleX = 0.85f
        tvResult.scaleY = 0.85f
        tvResult.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(350)
            .setInterpolator(OvershootInterpolator(1.4f))
            .start()

        // 2. The accuracy group slides up and fades in
        tvConfidence.setTextColor(color)
        tvConfidence.text = "0% confident"
        progressConfidence.progress = 0
        progressConfidence.progressTintList = ColorStateList.valueOf(color)

        groupAccuracy.animate().cancel()
        groupAccuracy.alpha = 0f
        groupAccuracy.translationY = dp(12)
        groupAccuracy.visibility = View.VISIBLE
        groupAccuracy.animate()
            .alpha(1f).translationY(0f)
            .setStartDelay(150)
            .setDuration(350)
            .setInterpolator(DecelerateInterpolator())
            .start()

        // 3. The bar fills and the percentage counts up
        countAnimator?.cancel()
        countAnimator = ValueAnimator.ofInt(0, confidence).apply {
            startDelay = 250
            duration = 900
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val value = it.animatedValue as Int
                progressConfidence.progress = value
                tvConfidence.text = "$value% confident"
            }
            start()
        }

        hapticSuccess()
    }

    // ---------------------------------------------------------------------
    // UI helpers
    // ---------------------------------------------------------------------

    private fun applyButtonState(newState: State) {
        btnRecord.alpha = 1f
        when (newState) {
            State.PREPARING -> {
                btnRecord.text = "Get ready... (tap to cancel)"
                btnRecord.setBackgroundResource(R.drawable.bg_button_start)
                btnRecord.setTextColor(ContextCompat.getColor(this, R.color.on_accent))
                btnRecord.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
                btnRecord.alpha = 0.85f
            }
            State.RECORDING -> {
                btnRecord.text = "Stop recording"
                btnRecord.setBackgroundResource(R.drawable.bg_button_stop)
                btnRecord.setTextColor(Color.WHITE)
                btnRecord.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_square, 0, 0, 0)
            }
            State.PROCESSING -> {
                setStartStyle("Analyzing...")
                btnRecord.alpha = 0.5f
            }
            State.RESULT -> setStartStyle("Record again")
            State.IDLE -> setStartStyle("Start recording")
        }
    }

    private fun setStartStyle(label: String) {
        btnRecord.text = label
        btnRecord.setBackgroundResource(R.drawable.bg_button_start)
        btnRecord.setTextColor(ContextCompat.getColor(this, R.color.on_accent))
        btnRecord.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
    }

    private fun showRecordingChip() {
        layoutStatus.animate().cancel()
        layoutStatus.pivotX = 0f
        layoutStatus.pivotY = 0f
        layoutStatus.alpha = 0f
        layoutStatus.scaleX = 0.8f
        layoutStatus.scaleY = 0.8f
        layoutStatus.visibility = View.VISIBLE
        layoutStatus.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(200)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun hideRecordingChip() {
        layoutStatus.animate().cancel()
        layoutStatus.animate()
            .alpha(0f)
            .setDuration(150)
            .withEndAction { layoutStatus.visibility = View.GONE }
            .start()
    }

    /** Muted text for non-final states such as "Waiting..." and "Analyzing...". */
    private fun showPlaceholder(text: String) {
        tvResult.text = text
        tvResult.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
    }

    /** Hide the accuracy and reset the card for a new recording. */
    private fun resetResultCard() {
        countAnimator?.cancel()
        stopPulse()
        tvResult.animate().cancel()
        tvResult.scaleX = 1f
        tvResult.scaleY = 1f
        groupAccuracy.animate().cancel()
        groupAccuracy.visibility = View.INVISIBLE
        progressConfidence.progress = 0
    }

    private fun startPulse() {
        pulseAnimator?.cancel()
        pulseAnimator = ObjectAnimator.ofFloat(tvResult, View.ALPHA, 1f, 0.35f).apply {
            duration = 700
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    private fun stopPulse() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        tvResult.alpha = 1f
    }

    /** Green at 85%+, amber at 60-84%, red below 60%. */
    private fun confidenceColor(confidence: Int): Int {
        val colorRes = when {
            confidence >= 85 -> R.color.success
            confidence >= 60 -> R.color.warning
            else -> R.color.error
        }
        return ContextCompat.getColor(this, colorRes)
    }

    private fun dp(value: Int): Float = value * resources.displayMetrics.density

    private fun haptic(type: Int) {
        btnRecord.performHapticFeedback(type)
    }

    private fun hapticSuccess() {
        haptic(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
            else HapticFeedbackConstants.LONG_PRESS
        )
    }

    // ---------------------------------------------------------------------
    // Camera and core logic
    // ---------------------------------------------------------------------

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }

                val imageAnalyzer = ImageAnalysis.Builder()
                    .setTargetResolution(android.util.Size(480, 640))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                            try {
                                val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                                val bitmap = imageProxy.toBitmap()
                                val rotatedBitmap = if (rotationDegrees != 0) {
                                    val matrix = android.graphics.Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                                    val rotated = android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                                    bitmap.recycle()
                                    rotated
                                } else {
                                    bitmap
                                }

                                val isFront = (lensFacing == CameraSelector.LENS_FACING_FRONT)
                                val detectedHands = handTracker?.processFrame(rotatedBitmap, isFront) ?: emptyList()
                                val bmpW = rotatedBitmap.width
                                val bmpH = rotatedBitmap.height

                                runOnUiThread {
                                    landmarkOverlay.updateLandmarks(detectedHands, bmpW, bmpH, isFront)
                                }


                                rotatedBitmap.recycle()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            imageProxy.close()
                        }
                    }

                cameraProvider.unbindAll()

                // Check if the requested lens is available, otherwise fall back to whatever is present
                val targetSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                val finalSelector = if (cameraProvider.hasCamera(targetSelector)) {
                    targetSelector
                } else if (cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                    lensFacing = CameraSelector.LENS_FACING_FRONT
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                    lensFacing = CameraSelector.LENS_FACING_BACK
                    CameraSelector.DEFAULT_BACK_CAMERA
                } else {
                    null
                }

                if (finalSelector != null) {
                    cameraProvider.bindToLifecycle(this, finalSelector, preview, imageAnalyzer)
                }
            } catch (exc: Exception) {
                exc.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun toggleCamera() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
            CameraSelector.LENS_FACING_BACK
        } else {
            CameraSelector.LENS_FACING_FRONT
        }
        startCamera()
    }

    private fun processVideoFrames() {
        cameraExecutor.execute {
            try {
                // Get the real 30-frame hand movement coordinates tracked from camera
                val motion = handTracker?.stopRecording()
                val handFrames = motion?.framesWithHands ?: 0

                // If no hands were detected during recording, do not produce a random guess
                if (motion == null || handFrames < 2) {
                    uiHandler.post {
                        showResult("No Sign Detected", 0)
                    }
                    return@execute
                }

                if (!isModelReady || fslClassifier == null) {
                    uiHandler.post {
                        showResult("Model Loading...", 0)
                    }
                    return@execute
                }

                // Run real offline ONNX model inference on the tracked movements
                val result = fslClassifier?.classify(motion.sequence)
                if (result == null) {
                    uiHandler.post {
                        showResult("Recognition Failed", 0)
                    }
                    return@execute
                }

                uiHandler.post {
                    showResult(result.label, result.confidence)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                uiHandler.post {
                    showResult("Recognition Error", 0)
                }
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Camera permission required.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        countAnimator?.cancel()
        pulseAnimator?.cancel()
        cameraExecutor.shutdown()
        uiHandler.removeCallbacksAndMessages(null)
        fslClassifier?.close()
        handTracker?.close()
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}
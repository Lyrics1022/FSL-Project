package com.example.sign_language_translator

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.drawerlayout.widget.DrawerLayout
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    /** IDLE -> PREPARING (1s delay) -> RECORDING -> PROCESSING -> RESULT */
    private enum class State { IDLE, PREPARING, RECORDING, PROCESSING, RESULT }

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var btnMenu: ImageButton
    private lateinit var viewFinder: PreviewView
    private lateinit var btnRecord: FrameLayout
    private lateinit var imgActionIcon: ImageView
    private lateinit var tvActionLabel: TextView
    private lateinit var btnSpeak: ImageButton
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

    // TextToSpeech engine
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var lastRecognizedWord: String = ""
    private var isFlippingCamera = false

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

        // Initialize TextToSpeech engine
        tts = TextToSpeech(this, this)

        // Load the neural network and hand tracker asynchronously in the background so the UI never freezes
        cameraExecutor.execute {
            try {
                fslClassifier = FslClassifier(applicationContext)
                handTracker = HandLandmarkTracker(applicationContext)
                isModelReady = true
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }

        drawerLayout = findViewById(R.id.drawerLayout)
        btnMenu = findViewById(R.id.btnMenu)
        viewFinder = findViewById(R.id.viewFinder)
        btnRecord = findViewById(R.id.btnRecord)
        imgActionIcon = findViewById(R.id.imgActionIcon)
        tvActionLabel = findViewById(R.id.tvActionLabel)
        btnSpeak = findViewById(R.id.btnSpeak)
        tvResult = findViewById(R.id.tvResult)
        tvConfidence = findViewById(R.id.tvConfidence)
        progressConfidence = findViewById(R.id.progressConfidence)
        groupAccuracy = findViewById(R.id.groupAccuracy)

        layoutStatus = findViewById(R.id.layoutStatus)
        imgTimerDot = findViewById(R.id.imgTimerDot)
        tvTimer = findViewById(R.id.tvTimer)
        btnFlipCamera = findViewById(R.id.btnFlipCamera)
        landmarkOverlay = findViewById(R.id.landmarkOverlay)

        // Hamburger Menu toggle
        btnMenu.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        // Close Navigation Drawer button
        findViewById<View>(R.id.btnCloseDrawer)?.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        // Navigation Drawer Item Clicks
        findViewById<View>(R.id.menuVocab).setOnClickListener {
            drawerLayout.closeDrawers()
            showVocabularyDialog()
        }
        findViewById<View>(R.id.menuModel).setOnClickListener {
            drawerLayout.closeDrawers()
            Toast.makeText(this, "Engine: BiLSTM + ONNX Runtime (Offline)", Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.menuAbout).setOnClickListener {
            drawerLayout.closeDrawers()
            showAboutDialog()
        }

        // Volume / Pronunciation button click
        btnSpeak.setOnClickListener {
            btnSpeak.animate().scaleX(0.85f).scaleY(0.85f).setDuration(80).withEndAction {
                btnSpeak.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }.start()
            haptic(HapticFeedbackConstants.CLOCK_TICK)

            val textToSpeak = if (lastRecognizedWord.isNotBlank()) lastRecognizedWord else tvResult.text.toString()
            speakWord(textToSpeak)
        }

        btnFlipCamera.setOnClickListener {
            if (isFlippingCamera) return@setOnClickListener
            isFlippingCamera = true
            btnFlipCamera.isEnabled = false

            btnFlipCamera.animate()
                .rotationBy(180f)
                .setDuration(280)
                .withEndAction {
                    btnFlipCamera.isEnabled = true
                    isFlippingCamera = false
                }
                .start()

            toggleCamera()
        }

        setupWindow()
        setupInsets()
        setupSignActionButton()
        applyButtonState(State.IDLE)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d("FSL_TTS", "Speech started: utteranceId=$utteranceId")
                    uiHandler.post {
                        btnSpeak.setColorFilter(ContextCompat.getColor(this@MainActivity, R.color.accent))
                        btnSpeak.animate().scaleX(1.2f).scaleY(1.2f).setDuration(120).start()
                    }
                }

                override fun onDone(utteranceId: String?) {
                    Log.d("FSL_TTS", "Speech finished: utteranceId=$utteranceId")
                    uiHandler.post {
                        btnSpeak.clearColorFilter()
                        btnSpeak.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    Log.e("FSL_TTS", "Speech error: utteranceId=$utteranceId")
                    uiHandler.post {
                        btnSpeak.clearColorFilter()
                        btnSpeak.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                    }
                }
            })
            isTtsReady = true
            Log.d("FSL_TTS", "TTS engine initialized successfully")
        } else {
            Log.e("FSL_TTS", "TTS initialization failed with status=$status")
        }
    }

    private fun speakWord(text: String) {
        if (!isTtsReady) {
            Toast.makeText(this, "Voice audio is initializing...", Toast.LENGTH_SHORT).show()
            Log.w("FSL_TTS", "speakWord called but TTS is not ready yet")
            return
        }
        if (text.isBlank()) return

        val spokenText = when {
            text.equals("Please record...", ignoreCase = true) || text.equals("Ready", ignoreCase = true) ->
                "Please record a sign"
            text.equals("No Sign Detected", ignoreCase = true) ->
                "No sign detected"
            else -> text
        }

        val utteranceId = "fsl_pronounce_${System.currentTimeMillis()}"
        Log.d("FSL_TTS", "Speaking text: '$spokenText' with utteranceId=$utteranceId")
        val result = tts?.speak(spokenText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        Log.d("FSL_TTS", "speak result code: $result")
    }

    private fun showVocabularyDialog() {
        VocabLibraryDialog(this) { signText ->
            speakWord(signText)
        }.show()
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle("About Sign Translator")
            .setMessage("Mobile-first Filipino Sign Language (FSL) offline translation engine.\n\n• Tracking: MediaPipe Tasks Vision\n• Model: 1D-CNN + BiLSTM (ONNX Runtime)\n• Dual-hypothesis mirror invariant inference\n• 100% on-device private processing")
            .setPositiveButton("OK", null)
            .show()
    }

    // ---------------------------------------------------------------------
    // Window & Shutter Button Setup
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

    private fun setupInsets() {
        val mainContent = findViewById<View>(R.id.mainContent)
        val navDrawer = findViewById<View>(R.id.navDrawer)

        ViewCompat.setOnApplyWindowInsetsListener(drawerLayout) { _, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val navBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val cutoutHeight = insets.getInsets(WindowInsetsCompat.Type.displayCutout()).top
            val safeTop = maxOf(statusBarHeight, cutoutHeight)

            // Top bar breathing room away from the phone's status bar and camera cutout
            mainContent?.updatePadding(
                top = safeTop + dp(8).toInt(),
                bottom = navBarInsetSafe(navBarHeight)
            )

            // Navigation Drawer safe inset padding so header does not collide with the status bar clock/icons
            navDrawer?.updatePadding(
                top = safeTop + dp(28).toInt(),
                bottom = navBarHeight + dp(24).toInt()
            )

            insets
        }
    }

    private fun navBarInsetSafe(navBarHeight: Int): Int {
        return if (navBarHeight > 0) navBarHeight + dp(8).toInt() else dp(16).toInt()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupSignActionButton() {
        btnRecord.setOnClickListener {
            when (state) {
                State.IDLE, State.RESULT -> beginRecording()
                State.PREPARING -> cancelCountdown()
                State.RECORDING -> stopRecording()
                State.PROCESSING -> Unit
            }
        }

        // Action button press feedback
        btnRecord.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN ->
                    v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(70).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    v.animate().scaleX(1f).scaleY(1f).setDuration(110).start()
            }
            false
        }
    }

    // ---------------------------------------------------------------------
    // State transitions
    // ---------------------------------------------------------------------

    private fun cancelCountdown() {
        uiHandler.removeCallbacks(countdownRunnable)
        uiHandler.removeCallbacks(autoStopRunnable)
        state = State.IDLE
        hideRecordingChip()
        applyButtonState(state)
        showPlaceholder("Ready when you are")
        haptic(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    private fun beginRecording() {
        state = State.RECORDING
        resetResultCard()

        startTime = SystemClock.uptimeMillis()
        uiHandler.postDelayed(autoStopRunnable, RECORDING_TIME_MS)

        applyButtonState(state)
        haptic(HapticFeedbackConstants.CLOCK_TICK)

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
        showPlaceholder("Translating...")
        startPulse()
        haptic(HapticFeedbackConstants.CONTEXT_CLICK)

        processVideoFrames()
    }

    private fun showResult(sign: String, confidence: Int) {
        state = State.RESULT
        stopPulse()
        applyButtonState(state)

        val isSuccess = (confidence > 0 && sign != "No Sign Detected")
        if (isSuccess) {
            lastRecognizedWord = sign
            tvResult.text = sign
            tvResult.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            tvResult.setTypeface(Typeface.DEFAULT_BOLD, Typeface.NORMAL)
        } else {
            lastRecognizedWord = ""
            tvResult.text = sign
            tvResult.setTextColor(ContextCompat.getColor(this, R.color.accent))
            tvResult.setTypeface(null, Typeface.ITALIC)
        }

        // 1. Result word animation
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

        // 2. Accuracy progress bar & confidence tag
        val accentColor = ContextCompat.getColor(this, R.color.accent)
        tvConfidence.setTextColor(accentColor)
        tvConfidence.text = "0% CONFIDENT"
        progressConfidence.progress = 0
        progressConfidence.progressTintList = ColorStateList.valueOf(accentColor)

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

        // 3. Counting number animation
        countAnimator?.cancel()
        countAnimator = ValueAnimator.ofInt(0, confidence).apply {
            startDelay = 250
            duration = 800
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val value = it.animatedValue as Int
                progressConfidence.progress = value
                tvConfidence.text = "$value% CONFIDENT"
            }
            start()
        }

        hapticSuccess()
    }

    // ---------------------------------------------------------------------
    // UI Helpers & Shutter State
    // ---------------------------------------------------------------------

    private var isHandsPresentInRecording: Boolean? = null

    private fun applyButtonState(newState: State) {
        when (newState) {
            State.IDLE -> {
                btnRecord.isEnabled = true
                btnRecord.alpha = 1f
                btnRecord.setBackgroundResource(R.drawable.bg_button_signing_idle)
                imgActionIcon.setImageResource(R.drawable.ic_hand_signing)
                imgActionIcon.setColorFilter(Color.parseColor("#041A24"))
                tvActionLabel.text = "Start signing"
                tvActionLabel.setTextColor(Color.parseColor("#FFFFFF"))
                landmarkOverlay.setGuideState(LandmarkOverlayView.GuideState.IDLE)
                showPlaceholder("Ready when you are")
            }
            State.PREPARING -> {
                btnRecord.isEnabled = true
                btnRecord.alpha = 0.85f
            }
            State.RECORDING -> {
                btnRecord.isEnabled = true
                btnRecord.alpha = 1f
                isHandsPresentInRecording = null
                updateRecordingHandsPresence(landmarkOverlay.hasHands)
            }
            State.PROCESSING -> {
                btnRecord.isEnabled = false
                btnRecord.alpha = 0.5f
                tvActionLabel.text = "Translating..."
                tvActionLabel.setTextColor(Color.parseColor("#FFFFFF"))
            }
            State.RESULT -> {
                btnRecord.isEnabled = true
                btnRecord.alpha = 1f
                btnRecord.setBackgroundResource(R.drawable.bg_button_signing_stopped)
                imgActionIcon.setImageResource(R.drawable.ic_square)
                imgActionIcon.setColorFilter(Color.parseColor("#FFFFFF"))
                tvActionLabel.text = "Stopped • tap to restart"
                tvActionLabel.setTextColor(Color.parseColor("#FFFFFF"))
                landmarkOverlay.setGuideState(LandmarkOverlayView.GuideState.STOPPED)
            }
        }
    }

    private fun updateRecordingHandsPresence(hasHands: Boolean) {
        if (state != State.RECORDING) return
        if (isHandsPresentInRecording == hasHands) return
        isHandsPresentInRecording = hasHands

        if (hasHands) {
            // State 2: Translating (Cyan button, square icon, "Translating...")
            btnRecord.setBackgroundResource(R.drawable.bg_button_signing_idle)
            imgActionIcon.setImageResource(R.drawable.ic_square)
            imgActionIcon.setColorFilter(Color.parseColor("#041A24"))
            tvActionLabel.text = "Translating..."
            tvActionLabel.setTextColor(Color.parseColor("#FFFFFF"))
            landmarkOverlay.setGuideState(LandmarkOverlayView.GuideState.TRANSLATING)
            showPlaceholder("Translating...")
        } else {
            // State 3: Show your hands (Dark amber-ring button, amber slashed hand icon, "Show your hands")
            btnRecord.setBackgroundResource(R.drawable.bg_button_signing_nohands)
            imgActionIcon.setImageResource(R.drawable.ic_hand_no_hands)
            imgActionIcon.setColorFilter(Color.parseColor("#F59E0B"))
            tvActionLabel.text = "Show your hands"
            tvActionLabel.setTextColor(Color.parseColor("#F59E0B"))
            landmarkOverlay.setGuideState(LandmarkOverlayView.GuideState.NO_HANDS)
            showPlaceholder("Waiting for signs...", Color.parseColor("#F59E0B"))
        }
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

    private fun showPlaceholder(text: String, color: Int = ContextCompat.getColor(this, R.color.accent)) {
        tvResult.text = text
        tvResult.setTextColor(color)
        tvResult.setTypeface(null, Typeface.ITALIC)
    }

    private fun resetResultCard() {
        countAnimator?.cancel()
        stopPulse()
        tvResult.animate().cancel()
        tvResult.scaleX = 1f
        tvResult.scaleY = 1f
        groupAccuracy.animate().cancel()
        groupAccuracy.visibility = View.GONE
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
    // Camera & Core Pipeline
    // ---------------------------------------------------------------------

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }

                @Suppress("DEPRECATION")
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
                                    if (state == State.RECORDING) {
                                        updateRecordingHandsPresence(detectedHands.isNotEmpty())
                                    }
                                }

                                rotatedBitmap.recycle()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            imageProxy.close()
                        }
                    }

                cameraProvider.unbindAll()

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
                val motion = handTracker?.stopRecording()
                val handFrames = motion?.framesWithHands ?: 0

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
        tts?.stop()
        tts?.shutdown()
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
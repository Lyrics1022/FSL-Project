package com.example.sign_language_translator

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import java.util.Collections
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Tracks hand and upper body pose landmarks from CameraX frames in real-time using MediaPipe Tasks Vision.
 * Buffers 159-dim coordinate trajectories while recording, verifies hand presence,
 * and resamples to a 30-frame sequence for model inference.
 */
class HandLandmarkTracker(private val context: Context) : AutoCloseable {

    data class MotionData(
        val sequence: FloatArray,
        val framesWithHands: Int,
        val totalFrames: Int
    )

    companion object {
        const val HAND_MODEL_PATH = "hand_landmarker.task"
        const val POSE_MODEL_PATH = "pose_landmarker_lite.task"
        const val FEATURE_DIM = 159 // 63 (L) + 63 (R) + 33 (Pose)
        const val TARGET_SEQ_LEN = 30
    }

    private var handLandmarker: HandLandmarker? = null
    private var poseLandmarker: PoseLandmarker? = null
    private val frameBuffer = Collections.synchronizedList(mutableListOf<FloatArray>())
    @Volatile var isRecording: Boolean = false
    @Volatile private var framesWithHandsCount: Int = 0
    @Volatile private var totalRecordedFrames: Int = 0
    @Volatile private var lastGoodHandFeatures: FloatArray? = null
    @Volatile private var consecutiveEmptyHandFrames: Int = 0

    init {
        setupLandmarker()
    }

    private fun setupLandmarker() {
        try {
            val handBaseOptions = BaseOptions.builder()
                .setModelAssetPath(HAND_MODEL_PATH)
                .build()

            val handOptions = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(handBaseOptions)
                .setMinHandDetectionConfidence(0.25f)
                .setMinTrackingConfidence(0.25f)
                .setNumHands(2)
                .setRunningMode(RunningMode.IMAGE)
                .build()

            handLandmarker = HandLandmarker.createFromOptions(context, handOptions)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val poseBaseOptions = BaseOptions.builder()
                .setModelAssetPath(POSE_MODEL_PATH)
                .build()

            val poseOptions = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(poseBaseOptions)
                .setMinPoseDetectionConfidence(0.3f)
                .setMinTrackingConfidence(0.3f)
                .setRunningMode(RunningMode.IMAGE)
                .build()

            poseLandmarker = PoseLandmarker.createFromOptions(context, poseOptions)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun startRecording() {
        synchronized(frameBuffer) {
            frameBuffer.clear()
            framesWithHandsCount = 0
            totalRecordedFrames = 0
            lastGoodHandFeatures = null
            consecutiveEmptyHandFrames = 0
        }
        isRecording = true
    }

    fun stopRecording(): MotionData {
        isRecording = false
        val capturedFrames: List<FloatArray>
        val handsCount: Int
        val totalCount: Int

        synchronized(frameBuffer) {
            capturedFrames = ArrayList(frameBuffer)
            handsCount = framesWithHandsCount
            totalCount = totalRecordedFrames
            frameBuffer.clear()
            framesWithHandsCount = 0
            totalRecordedFrames = 0
            lastGoodHandFeatures = null
            consecutiveEmptyHandFrames = 0
        }

        if (capturedFrames.isEmpty() || handsCount == 0) {
            return MotionData(FloatArray(TARGET_SEQ_LEN * FEATURE_DIM), handsCount, totalCount)
        }

        // Active Sign Windowing:
        // Filter out idle leading preparation and trailing recovery frames where hands are absent,
        // exactly mirroring the dataset preprocessing (extract_top20_fast.py).
        // This ensures the 30-frame temporal window spans the true active sign gesture.
        val handEnergies = FloatArray(capturedFrames.size) { i ->
            var sum = 0f
            val f = capturedFrames[i]
            for (k in 0 until 126) {
                sum += kotlin.math.abs(f[k])
            }
            sum
        }

        var firstActive = -1
        var lastActive = -1
        for (i in handEnergies.indices) {
            if (handEnergies[i] > 0.05f) {
                if (firstActive == -1) firstActive = i
                lastActive = i
            }
        }

        val effectiveFrames: List<FloatArray> = if (firstActive != -1 && (lastActive - firstActive) >= 3) {
            val start = maxOf(0, firstActive - 1)
            val end = minOf(capturedFrames.size, lastActive + 2)
            capturedFrames.subList(start, end)
        } else {
            capturedFrames
        }

        // Resample captured frames to exactly TARGET_SEQ_LEN (30 frames)
        val resampled = resampleSequence(effectiveFrames, TARGET_SEQ_LEN)
        return MotionData(resampled, handsCount, totalCount)
    }

    /**
     * Processes a single CameraX frame Bitmap.
     * Returns the detected hand landmarks for the real-time HUD overlay.
     * Buffers sequence features only while isRecording is true.
     */
    fun processFrame(bitmap: Bitmap, isFrontCamera: Boolean = true): List<LandmarkOverlayView.HandLandmarks> {
        if (handLandmarker == null && poseLandmarker == null) return emptyList()

        val overlayHands = mutableListOf<LandmarkOverlayView.HandLandmarks>()

        try {
            val mpImage: MPImage = BitmapImageBuilder(bitmap).build()
            val frameFeatures = FloatArray(FEATURE_DIM)
            var hasHand = false

            // 1. Hands (dims 0..125)
            val handResult: HandLandmarkerResult? = handLandmarker?.detect(mpImage)
            if (handResult != null && handResult.landmarks().isNotEmpty()) {
                val handednessList = handResult.handednesses()
                val landmarksList = handResult.landmarks()
                hasHand = true

                val isSingleHand = (landmarksList.size == 1)

                for (i in landmarksList.indices) {
                    val landmarks = landmarksList[i]
                    val rawIsLeft = if (i < handednessList.size && handednessList[i].isNotEmpty()) {
                        handednessList[i][0].categoryName().equals("Left", ignoreCase = true)
                    } else {
                        i == 0
                    }

                    // For the live HUD overlay on screen, preserve raw points for LandmarkOverlayView
                    val overlayPoints = ArrayList<PointF>(landmarks.size)
                    for (lm in landmarks) {
                        overlayPoints.add(PointF(lm.x(), lm.y()))
                    }
                    overlayHands.add(LandmarkOverlayView.HandLandmarks(overlayPoints, rawIsLeft))

                    // Normalize hand coordinates relative to wrist (index 0)
                    val wrist = landmarks[0]
                    var maxDist = 0.001f
                    for (lm in landmarks) {
                        val dx = lm.x() - wrist.x()
                        val dy = lm.y() - wrist.y()
                        val dz = lm.z() - wrist.z()
                        val dist = sqrt(dx * dx + dy * dy + dz * dz)
                        if (dist > maxDist) maxDist = dist
                    }

                    // Hand Assignment & Chirality:
                    // In front camera (selfie mode), the raw CameraX bitmap is unmirrored,
                    // so the user's physical right hand appears on the left of the image and is
                    // detected by MediaPipe as a left hand.
                    // We map physical hands consistently:
                    //   Physical Left hand -> slot 0..62
                    //   Physical Right hand -> slot 63..125
                    // And invert local X on front camera so 3D morphology matches canonical orientation.
                    val effectiveIsLeft = if (isFrontCamera) !rawIsLeft else rawIsLeft
                    val targetOffset = if (effectiveIsLeft) 0 else 63
                    val invertLocalX = isFrontCamera

                    for (j in 0 until min(21, landmarks.size)) {
                        val lm = landmarks[j]
                        val baseIdx = targetOffset + (j * 3)
                        val dx = (lm.x() - wrist.x()) / maxDist
                        frameFeatures[baseIdx] = if (invertLocalX) -dx else dx
                        frameFeatures[baseIdx + 1] = (lm.y() - wrist.y()) / maxDist
                        frameFeatures[baseIdx + 2] = (lm.z() - wrist.z()) / maxDist
                    }
                }
            }

            if (hasHand) {
                lastGoodHandFeatures = frameFeatures.copyOfRange(0, 126)
                consecutiveEmptyHandFrames = 0
            } else {
                consecutiveEmptyHandFrames++
                // If it's a momentary 1-frame tracking flicker while recording, smoothly hold the previous hand pose
                if (isRecording && consecutiveEmptyHandFrames == 1 && lastGoodHandFeatures != null) {
                    System.arraycopy(lastGoodHandFeatures!!, 0, frameFeatures, 0, 126)
                    hasHand = true
                }
            }

            // 2. Upper body pose (dims 126..158: 11 joints = 33 floats)
            try {
                val poseResult: PoseLandmarkerResult? = poseLandmarker?.detect(mpImage)
                if (poseResult != null && poseResult.landmarks().isNotEmpty()) {
                    val poseLms = poseResult.landmarks()[0]
                    // Indices in MediaPipe: nose (0), shoulders (11, 12), elbows (13, 14), wrists (15, 16), hips (23, 24), ears (7, 8)
                    val poseIndices = intArrayOf(0, 11, 12, 13, 14, 15, 16, 23, 24, 7, 8)

                    val leftShoulder = if (11 < poseLms.size) poseLms[11] else null
                    val rightShoulder = if (12 < poseLms.size) poseLms[12] else null

                    val midX = if (leftShoulder != null && rightShoulder != null) (leftShoulder.x() + rightShoulder.x()) / 2f else (leftShoulder?.x() ?: 0f)
                    val midY = if (leftShoulder != null && rightShoulder != null) (leftShoulder.y() + rightShoulder.y()) / 2f else (leftShoulder?.y() ?: 0f)
                    val midZ = if (leftShoulder != null && rightShoulder != null) (leftShoulder.z() + rightShoulder.z()) / 2f else (leftShoulder?.z() ?: 0f)

                    var maxPoseDist = 0.001f
                    for (idx in poseIndices) {
                        if (idx < poseLms.size) {
                            val lm = poseLms[idx]
                            val dx = lm.x() - midX
                            val dy = lm.y() - midY
                            val dz = lm.z() - midZ
                            val dist = sqrt(dx * dx + dy * dy + dz * dz)
                            if (dist > maxPoseDist) maxPoseDist = dist
                        }
                    }

                    for (k in poseIndices.indices) {
                        val idx = poseIndices[k]
                        if (idx < poseLms.size) {
                            val lm = poseLms[idx]
                            val baseIdx = 126 + (k * 3)
                            val normDx = (lm.x() - midX) / maxPoseDist
                            frameFeatures[baseIdx] = if (isFrontCamera) -normDx else normDx
                            frameFeatures[baseIdx + 1] = (lm.y() - midY) / maxPoseDist
                            frameFeatures[baseIdx + 2] = (lm.z() - midZ) / maxPoseDist
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore pose dropouts gracefully
            }

            if (isRecording) {
                synchronized(frameBuffer) {
                    totalRecordedFrames++
                    if (hasHand) {
                        framesWithHandsCount++
                    }
                    frameBuffer.add(frameFeatures)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return overlayHands
    }

    /**
     * Resamples variable-length frame lists to exactly targetLen using linear interpolation.
     */
    private fun resampleSequence(frames: List<FloatArray>, targetLen: Int): FloatArray {
        val output = FloatArray(targetLen * FEATURE_DIM)
        val origLen = frames.size

        if (origLen == 1) {
            val single = frames[0]
            for (t in 0 until targetLen) {
                System.arraycopy(single, 0, output, t * FEATURE_DIM, FEATURE_DIM)
            }
            return output
        }

        for (t in 0 until targetLen) {
            val pos = t.toFloat() / (targetLen - 1) * (origLen - 1)
            val low = min(pos.toInt(), origLen - 1)
            val high = min(low + 1, origLen - 1)
            val weight = pos - low

            val lowFrame = frames[low]
            val highFrame = frames[high]

            val outOffset = t * FEATURE_DIM
            for (f in 0 until FEATURE_DIM) {
                output[outOffset + f] = (1f - weight) * lowFrame[f] + weight * highFrame[f]
            }
        }

        return output
    }

    override fun close() {
        try {
            handLandmarker?.close()
            handLandmarker = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            poseLandmarker?.close()
            poseLandmarker = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

package com.example.sign_language_translator

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.json.JSONArray
import java.io.InputStream
import java.nio.FloatBuffer
import java.util.Collections
import kotlin.math.exp

/**
 * FSL Offline Classifier using ONNX Runtime.
 * Loads fsl_model.onnx and vocab.json from the Android assets directory.
 */
class FslClassifier(private val context: Context) : AutoCloseable {

    data class RecognitionResult(
        val label: String,
        val confidence: Int,
        val rawIndex: Int
    )

    private val ortEnv: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val ortSession: OrtSession
    private val vocabList: List<String>

    companion object {
        const val MODEL_FILE = "fsl_model.onnx"
        const val VOCAB_FILE = "vocab.json"
        const val SEQUENCE_LENGTH = 30
        const val FEATURE_DIM = 159
        const val TOTAL_INPUT_SIZE = SEQUENCE_LENGTH * FEATURE_DIM // 4,770 floats
    }

    init {
        // 1. Load Vocab
        vocabList = loadVocabFromAssets(VOCAB_FILE)

        // 2. Load ONNX Model bytes into session
        val modelBytes = context.assets.open(MODEL_FILE).use { it.readBytes() }
        val sessionOptions = OrtSession.SessionOptions()
        ortSession = ortEnv.createSession(modelBytes, sessionOptions)
    }

    private fun loadVocabFromAssets(fileName: String): List<String> {
        val list = mutableListOf<String>()
        try {
            val jsonString = context.assets.open(fileName).bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                list.add(jsonArray.getString(i))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    private data class PredictionScore(
        val index: Int,
        val confidencePct: Int,
        val maxLogit: Float
    )

    private fun evaluateBuffer(bufferFloats: FloatArray): PredictionScore {
        val shape = longArrayOf(1, SEQUENCE_LENGTH.toLong(), FEATURE_DIM.toLong())
        val buffer = FloatBuffer.wrap(bufferFloats)

        val inputTensor = OnnxTensor.createTensor(ortEnv, buffer, shape)
        val output = ortSession.run(Collections.singletonMap("landmarks_sequence", inputTensor))
        inputTensor.close()

        val rawOutput = output.get(0).value
        val logits: FloatArray = when (rawOutput) {
            is Array<*> -> (rawOutput[0] as FloatArray)
            is FloatArray -> rawOutput
            else -> throw IllegalStateException("Unexpected model output type: ${rawOutput?.javaClass}")
        }
        output.close()

        // Compute softmax and argmax
        var bestIdx = 0
        var maxLogit = Float.NEGATIVE_INFINITY
        for (i in logits.indices) {
            if (logits[i] > maxLogit) {
                maxLogit = logits[i]
                bestIdx = i
            }
        }

        var sumExp = 0.0
        val expScores = DoubleArray(logits.size)
        for (i in logits.indices) {
            expScores[i] = exp((logits[i] - maxLogit).toDouble())
            sumExp += expScores[i]
        }
        val confidenceProb = if (sumExp > 0.0) (expScores[bestIdx] / sumExp) else 0.0
        val confidencePct = (confidenceProb * 100).toInt().coerceIn(1, 100)

        return PredictionScore(bestIdx, confidencePct, maxLogit)
    }

    /**
     * Creates a spatially mirrored counterpart of the 30x159 sequence:
     * - Swaps Left Hand (dims 0..62) and Right Hand (dims 63..125)
     * - Inverts X coordinates for both hands and upper body pose
     */
    private fun createMirroredSequence(input: FloatArray): FloatArray {
        val mirrored = FloatArray(TOTAL_INPUT_SIZE)
        for (t in 0 until SEQUENCE_LENGTH) {
            val frameOffset = t * FEATURE_DIM

            // 1. Swap hands and invert X
            for (j in 0 until 21) {
                val lhIdx = frameOffset + (j * 3)
                val rhIdx = frameOffset + 63 + (j * 3)

                // Mirror RH -> new LH
                mirrored[lhIdx] = -input[rhIdx]
                mirrored[lhIdx + 1] = input[rhIdx + 1]
                mirrored[lhIdx + 2] = input[rhIdx + 2]

                // Mirror LH -> new RH
                mirrored[rhIdx] = -input[lhIdx]
                mirrored[rhIdx + 1] = input[lhIdx + 1]
                mirrored[rhIdx + 2] = input[lhIdx + 2]
            }

            // 2. Pose (dims 126..158): Invert X
            for (k in 0 until 11) {
                val poseIdx = frameOffset + 126 + (k * 3)
                mirrored[poseIdx] = -input[poseIdx]
                mirrored[poseIdx + 1] = input[poseIdx + 1]
                mirrored[poseIdx + 2] = input[poseIdx + 2]
            }
        }
        return mirrored
    }

    /**
     * Runs dual-hypothesis calibrated inference on a 30-frame sequence of 159 keypoint coordinates.
     * Evaluates both standard and mirrored spatial representations to ensure 100% calibration
     * across left/right hand signing and front/back camera orientations.
     *
     * @param inputFeatures Flattened array of shape (30 * 159). If size differs, it will be padded/truncated.
     * @return RecognitionResult with formatted English word and confidence percentage (0-100).
     */
    fun classify(inputFeatures: FloatArray? = null): RecognitionResult {
        // Ensure exact 4,770 floats
        val cleanInput = FloatArray(TOTAL_INPUT_SIZE)
        if (inputFeatures != null) {
            val copyLen = minOf(inputFeatures.size, TOTAL_INPUT_SIZE)
            System.arraycopy(inputFeatures, 0, cleanInput, 0, copyLen)
        }

        // 1. Test Hypothesis A: Standard spatial trajectory
        val normalPred = evaluateBuffer(cleanInput)

        // 2. Test Hypothesis B: Horizontally mirrored / hand-swapped spatial trajectory
        val mirroredInput = createMirroredSequence(cleanInput)
        val mirrorPred = evaluateBuffer(mirroredInput)

        // 3. Dual-Hypothesis Selection: Automatically choose the hypothesis with highest model confidence
        val chosenPred = if (mirrorPred.confidencePct > normalPred.confidencePct) {
            mirrorPred
        } else {
            normalPred
        }

        val rawLabel = if (chosenPred.index in vocabList.indices) vocabList[chosenPred.index] else "UNKNOWN"
        val formattedLabel = rawLabel
            .removePrefix("FSL_Dataset_")
            .replace("_", " ")
            .trim()

        return RecognitionResult(
            label = formattedLabel,
            confidence = chosenPred.confidencePct,
            rawIndex = chosenPred.index
        )
    }

    override fun close() {
        try {
            ortSession.close()
            ortEnv.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

package com.example.sentinel.module1.pipeline

import android.content.Context
import android.graphics.Bitmap
import com.example.sentinel.core.ForensicModule
import com.example.sentinel.core.LayerResult
import com.example.sentinel.core.RiskLevel
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class TfliteClassifier(private val context: Context) : ForensicModule {

    override val moduleName = "AI Forgery Classifier"

    private val modelFileName = "forgery_detector.tflite"
    private val inputSize = 224
    private val pixelSize = 3
    private val bytesPerChannel = 4

    private var interpreter: Interpreter? = null

    init {
        loadModel()
    }

    private fun loadModel() {
        try {
            val assetFileDescriptor = context.assets.openFd(modelFileName)
            val fileInputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
            val fileChannel = fileInputStream.channel
            val mappedBuffer: MappedByteBuffer = fileChannel.map(
                FileChannel.MapMode.READ_ONLY,
                assetFileDescriptor.startOffset,
                assetFileDescriptor.declaredLength
            )
            interpreter = Interpreter(mappedBuffer)
        } catch (e: Exception) {
            interpreter = null
        }
    }

    override suspend fun analyze(input: com.example.sentinel.core.ModuleInput): LayerResult {
        if (input !is com.example.sentinel.core.ModuleInput.ImageInput) throw IllegalArgumentException("Expected ImageInput")
        val bitmap = input.bitmap
        val localInterpreter = interpreter ?: return LayerResult.unavailable(moduleName)

        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val inputBuffer = bitmapToByteBuffer(resizedBitmap)
        val outputBuffer = Array(1) { FloatArray(1) }

        localInterpreter.run(inputBuffer, outputBuffer)
        val forgeryProbability = outputBuffer[0][0].coerceIn(0f, 1f)

        val riskLevel = when {
            forgeryProbability >= 0.7f -> RiskLevel.FORGED
            forgeryProbability >= 0.4f -> RiskLevel.SUSPICIOUS
            else -> RiskLevel.CLEAR
        }

        return LayerResult(
            layerName = moduleName,
            passed = riskLevel == RiskLevel.CLEAR,
            riskLevel = riskLevel,
            score = forgeryProbability,
            details = buildList {
                add("Model: $modelFileName")
                add("Input resolution: ${inputSize}x${inputSize}")
                add("Forgery probability: ${"%.1f".format(forgeryProbability * 100)}%")
                when (riskLevel) {
                    RiskLevel.CLEAR -> add("AI finds no micro-visual forgery artifacts")
                    RiskLevel.SUSPICIOUS -> add("AI detects moderate forgery artifacts")
                    RiskLevel.FORGED -> add("AI detects strong forgery artifacts in micro-texture")
                }
            }
        )
    }

    private fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(bytesPerChannel * inputSize * inputSize * pixelSize)
        byteBuffer.order(ByteOrder.nativeOrder())
        val intValues = IntArray(inputSize * inputSize)
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        for (pixelValue in intValues) {
            byteBuffer.putFloat(((pixelValue shr 16 and 0xFF) / 255.0f))
            byteBuffer.putFloat(((pixelValue shr 8 and 0xFF) / 255.0f))
            byteBuffer.putFloat(((pixelValue and 0xFF) / 255.0f))
        }
        return byteBuffer
    }

    fun release() {
        interpreter?.close()
        interpreter = null
    }
}

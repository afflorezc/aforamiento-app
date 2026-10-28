package com.uniban.campo.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class ModelNotAvailableException(message: String) : Exception(message)

/**
 * Detector YOLOv8 (Ultralytics) exportado a TensorFlow Lite.
 *
 * El modelo .pt original debe exportarse una vez con:
 *   yolo export model=model.pt format=tflite imgsz=864
 * y colocarse como assets/models/model.tflite (o filesDir/models/model.tflite).
 */
class YoloTfliteDetector private constructor(private val interpreter: Interpreter) : AutoCloseable {

    private val inputSize = YoloConfig.INPUT_SIZE
    private val numClasses = YoloConfig.NUM_CLASSES

    /** Ejecuta la inferencia sobre un bitmap ya reescalado a INPUT_SIZE x INPUT_SIZE (letterbox). */
    fun detect(letterboxed: Bitmap): List<Detection> {
        require(letterboxed.width == inputSize && letterboxed.height == inputSize) {
            "La entrada debe ser ${inputSize}x${inputSize}"
        }

        val input = floatInput(letterboxed)
        val output = allocateOutput()
        interpreter.run(input, output)

        val raw = readOutput(output)
        val candidates = decodeCandidates(raw)
        return Nms.nms(candidates, YoloConfig.IOU_THRESHOLD)
    }

    private fun floatInput(bitmap: Bitmap): ByteBuffer {
        val pixels = IntArray(inputSize * inputSize)
        bitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        val buffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3).order(ByteOrder.nativeOrder())
        for (p in pixels) {
            buffer.putFloat(Color.red(p) / 255f)
            buffer.putFloat(Color.green(p) / 255f)
            buffer.putFloat(Color.blue(p) / 255f)
        }
        buffer.rewind()
        return buffer
    }

    private fun allocateOutput(): FloatArray {
        // Máximo posible: [1, 4+nc, N] con N = (864/8)^2 + (864/16)^2 + (864/32)^2 = 15309
        val n = (inputSize / 8) * (inputSize / 8) +
            (inputSize / 16) * (inputSize / 16) +
            (inputSize / 32) * (inputSize / 32)
        return FloatArray(n * (4 + numClasses))
    }

    private fun readOutput(output: FloatArray): Array<FloatArray> {
        // Formas habituales de la exportación TFLite de Ultralytics:
        //   [1, 4+nc, N] -> filas por atributo (transpuesta)
        //   [1, N, 4+nc] -> filas por ancla
        val shape = interpreter.getOutputTensor(0).shape()
        val attrs = 4 + numClasses
        val flat = output.copyOfRange(0, shape.drop(1).fold(1) { a, b -> a * b })

        val rows: Array<FloatArray> = when {
            shape.size == 3 && shape[1] == attrs -> {
                val anchors = shape[2]
                Array(anchors) { i -> FloatArray(attrs) { c -> flat[c * anchors + i] } }
            }
            shape.size == 3 && shape[2] == attrs -> {
                val anchors = shape[1]
                Array(anchors) { i -> flat.copyOfRange(i * attrs, i * attrs + attrs) }
            }
            else -> throw IllegalStateException("Forma de salida no soportada: ${shape.contentToString()}")
        }
        return rows
    }

    private fun decodeCandidates(rows: Array<FloatArray>): List<Detection> {
        val result = ArrayList<Detection>(256)
        val confTh = YoloConfig.CONF_THRESHOLD
        for (row in rows) {
            var bestClass = -1
            var bestScore = 0f
            for (c in 0 until numClasses) {
                val s = row[4 + c]
                if (s > bestScore) {
                    bestScore = s
                    bestClass = c
                }
            }
            if (bestClass < 0 || bestScore < confTh) continue

            val cx = row[0]
            val cy = row[1]
            val w = row[2]
            val h = row[3]
            result.add(
                Detection(
                    x1 = cx - w / 2f,
                    y1 = cy - h / 2f,
                    x2 = cx + w / 2f,
                    y2 = cy + h / 2f,
                    classId = bestClass,
                    score = bestScore
                )
            )
        }
        return result
    }

    override fun close() {
        interpreter.close()
    }

    companion object {

        fun isModelAvailable(context: Context): Boolean = resolveModelFile(context) != null

        private fun resolveModelFile(context: Context): java.io.File? {
            val local = java.io.File(context.filesDir, "${YoloConfig.MODELS_DIR}/${YoloConfig.MODEL_FILE_NAME}")
            if (local.exists() && local.length() > 0) return local
            return null
        }

        fun create(context: Context): YoloTfliteDetector {
            val buffer: MappedByteBuffer = resolveModelFile(context)?.let { file ->
                FileInputStream(file).use { fis ->
                    fis.channel.map(FileChannel.MapMode.READ_ONLY, 0, file.length())
                }
            } ?: loadFromAssets(context)

            val inputType = Interpreter(buffer).use { it.getInputTensor(0).dataType() }
            check(inputType == org.tensorflow.lite.DataType.FLOAT32) {
                "El modelo debe estar exportado en float32 (no cuantizado). Use: yolo export format=tflite imgsz=864"
            }

            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }
            return YoloTfliteDetector(Interpreter(buffer, options))
        }

        private fun loadFromAssets(context: Context): MappedByteBuffer {
            val assetPath = "${YoloConfig.MODELS_DIR}/${YoloConfig.MODEL_FILE_NAME}"
            val fd = try {
                context.assets.openFd(assetPath)
            } catch (e: Exception) {
                throw ModelNotAvailableException(
                    "No se encontró el modelo YOLO. Coloque el archivo exportado en " +
                        "assets/$assetPath (o en filesDir/$assetPath). " +
                        "Exporte el .pt con: yolo export model=model.pt format=tflite imgsz=864"
                )
            }
            return FileInputStream(fd.fileDescriptor).use { fis ->
                fis.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
            }
        }
    }
}

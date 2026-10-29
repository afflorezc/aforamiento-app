package com.uniban.campo.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class ModelNotAvailableException(message: String) : Exception(message)

/**
 * Detector YOLO sobre LiteRT (sucesor de TensorFlow Lite).
 *
 * Soporta ambos formatos de salida de Ultralytics:
 *  - YOLOv5nu / YOLOv5u / YOLOv8 (unified): [1, 4+nc, N] o [1, N, 4+nc],
 *    sin objectness; la confianza es la probabilidad de clase directa.
 *  - YOLOv5 clásico: [1, N, 5+nc] con objectness;
 *    la confianza es objectness * probabilidad de clase.
 *
 * El modelo .pt original debe exportarse una vez con (imgsz = tamaño de entrenamiento):
 *   yolo export model=yolov5nu.pt format=tflite imgsz=864
 *
 * Búsqueda del modelo, en este orden:
 *  1. Archivo .tflite en filesDir/models
 *  2. assets/models/model.tflite (nombre preferido)
 *  3. Cualquier .tflite en assets/models
 *  4. Cualquier .tflite en la raíz de assets
 *
 * Si el modelo está en assets, se copia por stream a filesDir (los assets
 * comprimidos dentro del APK no permiten mmap con openFd) y se usa la copia.
 */
class YoloTfliteDetector private constructor(private val interpreter: Interpreter) : AutoCloseable {

    private val inputSize = YoloConfig.INPUT_SIZE
    private val numClasses = YoloConfig.NUM_CLASSES

    /** Buffer de entrada reutilizado (directo y alineado a página); evita reasignar ~9 MB por tile. */
    private val inputBuffer: ByteBuffer = ByteBuffer
        .allocateDirect(4 * inputSize * inputSize * 3)
        .order(ByteOrder.nativeOrder())

    /** Forma real del tensor de salida, p. ej. [1, 6, 15309] o [1, 15309, 6]. */
    private val outputShape: IntArray = interpreter.getOutputTensor(0).shape().also {
        check(it.size == 3 && it[0] == 1) {
            "Forma de salida no soportada: ${it.contentToString()}. " +
                "Se espera un tensor [1, attrs, N] o [1, N, attrs] de un modelo de detección exportado con Ultralytics."
        }
    }

    /**
     * La dimensión pequeña es la de atributos (attrs) y la grande la de anclas (N):
     * [1, attrs, N] viene transpuesta (YOLOv5u/v8), [1, N, attrs] en filas por ancla.
     */
    private val outputTransposed: Boolean = outputShape[1] < outputShape[2]
    private val outputAttrs: Int = if (outputTransposed) outputShape[1] else outputShape[2]
    private val outputAnchors: Int = if (outputTransposed) outputShape[2] else outputShape[1]

    /** true = YOLOv5 clásico (cx, cy, w, h, objectness, cls...); false = v5u/v8 (cx, cy, w, h, cls...). */
    private val hasObjectness: Boolean = when (outputAttrs) {
        4 + numClasses -> false
        5 + numClasses -> true
        else -> throw IllegalStateException(
            "Forma de salida no soportada: ${outputShape.contentToString()} " +
                "(attrs=$outputAttrs para $numClasses clases). " +
                "Se esperaba attrs=${4 + numClasses} (YOLOv5u/YOLOv8) o attrs=${5 + numClasses} (YOLOv5 clásico). " +
                "Verifique que el modelo fue exportado con: yolo export format=tflite imgsz=$inputSize"
        )
    }

    private val outputPlane: Array<FloatArray> = if (outputTransposed) {
        Array(outputAttrs) { FloatArray(outputAnchors) }
    } else {
        Array(outputAnchors) { FloatArray(outputAttrs) }
    }

    init {
        Log.i(
            TAG,
            "Modelo cargado: salida=${outputShape.contentToString()}, " +
                "estilo=${if (hasObjectness) "YOLOv5 (objectness)" else "YOLOv5u/v8 (sin objectness)"}, " +
                "attrs=$outputAttrs, anclas=$outputAnchors"
        )
    }

    /** Ejecuta la inferencia sobre un bitmap ya reescalado a INPUT_SIZE x INPUT_SIZE (letterbox). */
    fun detect(letterboxed: Bitmap): List<Detection> {
        require(letterboxed.width == inputSize && letterboxed.height == inputSize) {
            "La entrada debe ser ${inputSize}x${inputSize}"
        }

        fillInput(letterboxed)

        try {
            interpreter.run(inputBuffer, arrayOf(outputPlane))
        } catch (e: Exception) {
            throw IllegalStateException(
                "Fallo en la inferencia con LiteRT: ${e.message}. " +
                    "Verifique que el modelo fue exportado con: yolo export format=tflite imgsz=$inputSize",
                e
            )
        }

        // [1, attrs, N] -> trasponer a filas por ancla; [1, N, attrs] -> ya está en filas.
        val rows: Array<FloatArray> = if (outputTransposed) {
            Array(outputAnchors) { i -> FloatArray(outputAttrs) { c -> outputPlane[c][i] } }
        } else {
            outputPlane
        }

        // Algunas exportaciones entregan cx,cy,w,h normalizados en 0..1 en vez de píxeles.
        val coordScale = coordinateScale(rows)

        val scored = decodeCandidates(rows, threshold = 0f, coordScale = coordScale)
        val confTh = YoloConfig.CONF_THRESHOLD
        val candidates = scored.filter { it.score >= confTh }

        // Diagnóstico para logcat: decide entre umbral bajo, unidades de coordenadas o modelo.
        val maxScore = scored.maxOfOrNull { it.score } ?: 0f
        val top = scored.maxByOrNull { it.score }
        Log.i(
            TAG,
            "Puntajes: max=$maxScore, >=$confTh=${candidates.size}, " +
                ">=0.10=${scored.count { it.score >= 0.10f }}, filas=$outputAnchors, coordScale=$coordScale" +
                (top?.let {
                    ", top=${"%.3f".format(it.score)} cls=${it.classId} " +
                        "bbox=[${it.x1.toInt()},${it.y1.toInt()},${it.x2.toInt()},${it.y2.toInt()}] en espacio 864"
                } ?: "")
        )

        val kept = Nms.nms(candidates, YoloConfig.IOU_THRESHOLD)
        Log.d(TAG, "Candidatos=${candidates.size}, tras NMS=${kept.size}")
        return kept
    }

    /**
     * Detecta si las coordenadas salen en 0..1 (normalizadas) en lugar de píxeles.
     * Los valores cx/cy/w/h de todas las anclas cubren todo el rango de entrada,
     * por lo que el máximo distingue de forma fiable ambos formatos.
     */
    private fun coordinateScale(rows: Array<FloatArray>): Float {
        var maxCoord = 0f
        for (row in rows) {
            val m = maxOf(
                kotlin.math.abs(row[0]), kotlin.math.abs(row[1]),
                kotlin.math.abs(row[2]), kotlin.math.abs(row[3])
            )
            if (m > maxCoord) maxCoord = m
        }
        return if (maxCoord in 0.01f..1.5f) {
            Log.w(TAG, "Coordenadas normalizadas detectadas (max=$maxCoord); se escalan a $inputSize px")
            inputSize.toFloat()
        } else {
            1f
        }
    }

    /**
     * Rellena el ByteBuffer directo (orden nativo, alineado a página) con la imagen
     * NHWC normalizada a [0, 1], exigiendo la posición 0 que requiere el runtime.
     */
    private fun fillInput(bitmap: Bitmap) {
        val pixels = IntArray(inputSize * inputSize)
        bitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        inputBuffer.rewind()
        for (p in pixels) {
            inputBuffer.putFloat(Color.red(p) / 255f)
            inputBuffer.putFloat(Color.green(p) / 255f)
            inputBuffer.putFloat(Color.blue(p) / 255f)
        }
        inputBuffer.rewind()
    }

    private fun decodeCandidates(
        rows: Array<FloatArray>,
        threshold: Float,
        coordScale: Float = 1f
    ): List<Detection> {
        val result = ArrayList<Detection>(256)
        val confTh = threshold
        val scoreOffset = if (hasObjectness) 5 else 4

        for (row in rows) {
            // En YOLOv5 clásico la confianza final es objectness * probabilidad de clase.
            val objectness = if (hasObjectness) row[4] else 1f
            if (objectness < confTh) continue

            var bestClass = -1
            var bestScore = 0f
            for (c in 0 until numClasses) {
                val s = row[scoreOffset + c] * objectness
                if (s > bestScore) {
                    bestScore = s
                    bestClass = c
                }
            }
            if (bestClass < 0 || bestScore <= confTh) continue

            val cx = row[0] * coordScale
            val cy = row[1] * coordScale
            val w = row[2] * coordScale
            val h = row[3] * coordScale
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
        private const val TAG = "YoloDetector"

        fun isModelAvailable(context: Context): Boolean =
            resolveModelFile(context) != null || findAssetModelPath(context) != null

        /** Archivo local ya copiado (cualquier .tflite en filesDir/models). */
        private fun resolveModelFile(context: Context): File? {
            val dir = File(context.filesDir, YoloConfig.MODELS_DIR)
            val preferred = File(dir, YoloConfig.MODEL_FILE_NAME)
            if (preferred.exists() && preferred.length() > 0) return preferred
            return dir.listFiles()
                ?.firstOrNull { it.isFile && it.name.endsWith(".tflite", ignoreCase = true) && it.length() > 0 }
        }

        /** Ruta dentro de assets del primer .tflite disponible. */
        private fun findAssetModelPath(context: Context): String? {
            val modelsDir = YoloConfig.MODELS_DIR
            val inModels = try {
                context.assets.list(modelsDir).orEmpty()
            } catch (e: Exception) {
                emptyArray()
            }
            if (inModels.any { it.equals(YoloConfig.MODEL_FILE_NAME, ignoreCase = true) }) {
                return "$modelsDir/${YoloConfig.MODEL_FILE_NAME}"
            }
            inModels.firstOrNull { it.endsWith(".tflite", ignoreCase = true) }?.let {
                return "$modelsDir/$it"
            }
            val root = try {
                context.assets.list("").orEmpty()
            } catch (e: Exception) {
                emptyArray()
            }
            root.firstOrNull { it.endsWith(".tflite", ignoreCase = true) }?.let { return it }
            return null
        }

        /** Copia el asset por stream (funciona aunque el asset esté comprimido en el APK). */
        private fun copyAssetToLocal(context: Context, assetPath: String): File {
            val dir = File(context.filesDir, YoloConfig.MODELS_DIR).apply { mkdirs() }
            val dest = File(dir, assetPath.substringAfterLast('/'))
            if (dest.exists() && dest.length() > 0) return dest
            context.assets.open(assetPath).use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            }
            return dest
        }

        private fun mapFile(file: File): MappedByteBuffer =
            FileInputStream(file).use { fis ->
                fis.channel.map(FileChannel.MapMode.READ_ONLY, 0, file.length())
            }

        fun create(context: Context): YoloTfliteDetector {
            val buffer: MappedByteBuffer = resolveModelFile(context)?.let { file ->
                Log.i(TAG, "Usando modelo local: ${file.absolutePath}")
                mapFile(file)
            } ?: findAssetModelPath(context)?.let { assetPath ->
                val local = copyAssetToLocal(context, assetPath)
                Log.i(TAG, "Modelo copiado de assets/$assetPath a ${local.absolutePath}")
                mapFile(local)
            } ?: throw ModelNotAvailableException(
                "No se encontró el modelo YOLO. Coloque un archivo .tflite en " +
                    "assets/${YoloConfig.MODELS_DIR}/ (preferido: ${YoloConfig.MODEL_FILE_NAME}) " +
                    "o en filesDir/${YoloConfig.MODELS_DIR}/. " +
                    "Exporte el .pt con: yolo export model=model.pt format=tflite imgsz=${
                        YoloConfig.INPUT_SIZE
                    }"
            )

            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }
            val interpreter = Interpreter(buffer, options)

            val inputType = try {
                interpreter.getInputTensor(0).dataType()
            } catch (e: Exception) {
                interpreter.close()
                throw ModelNotAvailableException(
                    "El archivo no es un modelo LiteRT/TFLite válido: ${e.message}"
                )
            }

            if (inputType != DataType.FLOAT32) {
                interpreter.close()
                throw ModelNotAvailableException(
                    "El modelo debe estar exportado en float32 (no cuantizado, sin int8). " +
                        "Use: yolo export model=model.pt format=tflite imgsz=${YoloConfig.INPUT_SIZE}"
                )
            }

            try {
                return YoloTfliteDetector(interpreter)
            } catch (e: Exception) {
                interpreter.close()
                throw e
            }
        }
    }
}

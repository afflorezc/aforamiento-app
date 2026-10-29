package com.uniban.campo.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.util.Log
import com.uniban.campo.util.ImageUtils
import java.io.File
import kotlin.math.max

data class EstimationProgress(val stage: String, val fraction: Float?)

data class EstimationResult(
    val totalPlants: Int,
    val platanoCount: Int,
    val bananoCount: Int,
    val avgConfidence: Float,
    val inputImagePath: String,
    val outputImagePath: String,
    val imageWidth: Int,
    val imageHeight: Int,
    val tilesUsed: Int,
    val detections: List<Detection>
)

/**
 * Pipeline completo de estimación:
 * 1. Copia y decodifica la imagen de entrada.
 * 2. Si supera [YoloConfig.TILE_THRESHOLD_PX], la particiona en tiles con superposición,
 *    ejecuta YOLO por tile, traslada las bbox a coordenadas globales y elimina las
 *    plantas detectadas dos veces en las intersecciones (NMS global + distancia entre centros).
 * 3. Dibuja las bbox finales sobre la imagen original y guarda el resultado JPEG.
 */
class PlantCounter(private val context: Context) {

    private val tag = "PlantCounter"
    private var detector: YoloTfliteDetector? = null

    private fun report(onProgress: (EstimationProgress) -> Unit, stage: String, fraction: Float?) {
        Log.d(tag, "[$fraction] $stage")
        onProgress(EstimationProgress(stage, fraction))
    }

    private fun getDetector(): YoloTfliteDetector =
        detector ?: YoloTfliteDetector.create(context).also { detector = it }

    fun close() {
        detector?.close()
        detector = null
    }

    fun estimate(
        sourceUri: Uri,
        estimationId: String,
        areaHa: Float,
        onProgress: (EstimationProgress) -> Unit = {}
    ): EstimationResult {
        val det = getDetector()
        val imagesDir = File(context.filesDir, "estimations").apply { mkdirs() }

        report(onProgress, "Copiando imagen de entrada…", 0.05f)
        val inputFile = ImageUtils.copyUriToDir(context, sourceUri, imagesDir, baseName = estimationId)

        report(onProgress, "Decodificando imagen…", 0.10f)
        val bitmap = try {
            ImageUtils.decodeFullImage(inputFile)
        } catch (e: OutOfMemoryError) {
            inputFile.delete()
            throw IllegalStateException(
                "La imagen es demasiado grande para la memoria del dispositivo. " +
                    "Reduzca la resolución del ortomosaico.",
                e
            )
        } catch (e: Exception) {
            inputFile.delete()
            throw IllegalStateException("No se pudo leer la imagen: ${e.message}", e)
        }

        try {
            val width = bitmap.width
            val height = bitmap.height
            val useTiling = max(width, height) > YoloConfig.TILE_THRESHOLD_PX

            val finalDetections: List<Detection>
            val tilesUsed: Int

            if (useTiling) {
                val tiles = computeTiles(width, height)
                tilesUsed = tiles.size
                val all = ArrayList<Detection>(tiles.size * 64)
                tiles.forEachIndexed { index, tile ->
                    report(
                        onProgress,
                        "Procesando tile ${index + 1} de ${tiles.size}…",
                        0.15f + 0.65f * index / tiles.size
                    )
                    val cropped = Bitmap.createBitmap(bitmap, tile.x, tile.y, tile.w, tile.h)
                    val (letterboxed, meta) = Letterbox.apply(cropped)
                    val tileDets = det.detect(letterboxed)
                    for (d in tileDets) {
                        val mapped = meta.toOriginal(d.x1, d.y1, d.x2, d.y2)
                        all.add(
                            Detection(
                                x1 = mapped[0] + tile.x,
                                y1 = mapped[1] + tile.y,
                                x2 = mapped[2] + tile.x,
                                y2 = mapped[3] + tile.y,
                                classId = d.classId,
                                score = d.score
                            )
                        )
                    }
                    if (cropped !== bitmap) cropped.recycle()
                    letterboxed.recycle()
                }
                report(onProgress, "Fusionando detecciones…", 0.82f)
                finalDetections = Nms.mergeTileDetections(all)
                    .map { clamp(it, width, height) }
                    .filter { it.width > 1f && it.height > 1f }
                Log.d(tag, "Tiles=${tiles.size}, detecciones crudas=${all.size}, tras merge=${finalDetections.size}")
            } else {
                tilesUsed = 1
                report(onProgress, "Detectando plantas…", 0.40f)
                val (letterboxed, meta) = Letterbox.apply(bitmap)
                val tileDets = det.detect(letterboxed)
                letterboxed.recycle()
                Log.d(
                    tag,
                    "Detecciones del modelo=$tileDets.size; letterbox scale=${meta.scale} pad=(${meta.padX},${meta.padY})"
                )
                report(onProgress, "Fusionando detecciones…", 0.70f)
                val mapped = Nms.nms(tileDets, YoloConfig.IOU_THRESHOLD)
                    .map { d ->
                        val coords = meta.toOriginal(d.x1, d.y1, d.x2, d.y2)
                        Detection(coords[0], coords[1], coords[2], coords[3], d.classId, d.score)
                    }
                finalDetections = mapped
                    .map { clamp(it, width, height) }
                    .filter { it.width > 1f && it.height > 1f }
                Log.d(tag, "Tras mapeo=${mapped.size}, tras filtro de tamaño=${finalDetections.size}")
            }

            val platanoCount = finalDetections.count { it.classId == 0 }
            val bananoCount = finalDetections.count { it.classId == 1 }
            val avgConfidence = if (finalDetections.isEmpty()) {
                0f
            } else {
                finalDetections.map { it.score }.average().toFloat()
            }

            report(onProgress, "Generando imagen de salida…", 0.90f)
            val outputFile = File(imagesDir, "${estimationId}_output.jpg")
            // BitmapFactory puede entregar bitmaps inmutables; Canvas exige uno mutable.
            val drawable = ensureMutable(bitmap)
            try {
                drawDetections(drawable, finalDetections)
                ImageUtils.saveJpeg(drawable, outputFile)
            } finally {
                drawable.recycle()
            }

            Log.i(
                tag,
                "Estimación $estimationId: total=${platanoCount + bananoCount} " +
                    "(plátano=$platanoCount, banano=$bananoCount), confianza=${"%.2f".format(avgConfidence)}, " +
                    "tiles=$tilesUsed, imagen=${width}x$height"
            )
            report(onProgress, "Guardando resultados…", 1.0f)
            return EstimationResult(
                totalPlants = platanoCount + bananoCount,
                platanoCount = platanoCount,
                bananoCount = bananoCount,
                avgConfidence = avgConfidence,
                inputImagePath = inputFile.absolutePath,
                outputImagePath = outputFile.absolutePath,
                imageWidth = width,
                imageHeight = height,
                tilesUsed = tilesUsed,
                detections = finalDetections
            )
        } catch (e: Exception) {
            bitmap.recycle()
            inputFile.delete()
            throw e
        }
    }

    private fun ensureMutable(source: Bitmap): Bitmap {
        if (source.isMutable) return source
        val copy = source.copy(Bitmap.Config.RGB_565, true)
            ?: throw IllegalStateException("No se pudo preparar la imagen de salida (bitmap inmutable)")
        source.recycle()
        return copy
    }

    private data class Tile(val x: Int, val y: Int, val w: Int, val h: Int)

    private fun computeTiles(width: Int, height: Int): List<Tile> {
        val size = YoloConfig.TILE_SIZE
        val stride = (size * (1f - YoloConfig.TILE_OVERLAP)).toInt().coerceAtLeast(1)
        val tiles = mutableListOf<Tile>()

        fun positions(length: Int): List<Int> {
            if (length <= size) return listOf(0)
            val list = mutableListOf(0)
            var p = stride
            while (p + size < length) {
                list.add(p)
                p += stride
            }
            list.add(length - size)
            return list.distinct()
        }

        for (y in positions(height)) {
            for (x in positions(width)) {
                tiles.add(
                    Tile(
                        x = x,
                        y = y,
                        w = minOf(size, width - x),
                        h = minOf(size, height - y)
                    )
                )
            }
        }
        return tiles
    }

    private fun clamp(d: Detection, width: Int, height: Int): Detection {
        val x1 = d.x1.coerceIn(0f, width.toFloat())
        val y1 = d.y1.coerceIn(0f, height.toFloat())
        val x2 = d.x2.coerceIn(0f, width.toFloat())
        val y2 = d.y2.coerceIn(0f, height.toFloat())
        return Detection(x1, y1, x2, y2, d.classId, d.score)
    }

    private fun drawDetections(bitmap: Bitmap, detections: List<Detection>) {
        if (detections.isEmpty()) return
        val canvas = Canvas(bitmap)
        val dim = max(bitmap.width, bitmap.height).toFloat()
        val stroke = max(2f, dim / 700f)
        val labelSize = max(18f, dim / 90f)
        val cornerPad = labelSize * 0.35f

        val rectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
        }
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = labelSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        for (d in detections) {
            val color = if (d.classId == 0) Color.parseColor("#2E7D32") else Color.parseColor("#F9A825")
            rectPaint.color = color
            bgPaint.color = color

            canvas.drawRect(d.x1, d.y1, d.x2, d.y2, rectPaint)

            val label = "%s %.2f".format(YoloConfig.CLASS_NAMES.getOrElse(d.classId) { "?" }, d.score)
            val textWidth = textPaint.measureText(label)
            val bgLeft = d.x1
            val bgRight = d.x1 + textWidth + cornerPad * 2

            if (d.y1 - labelSize - cornerPad * 2 >= 0f) {
                val bgTop = d.y1 - labelSize - cornerPad * 2
                canvas.drawRect(bgLeft, bgTop, bgRight, d.y1, bgPaint)
                canvas.drawText(label, bgLeft + cornerPad, d.y1 - cornerPad, textPaint)
            } else {
                val bgBottom = d.y1 + labelSize + cornerPad * 2
                canvas.drawRect(bgLeft, d.y1, bgRight, bgBottom, bgPaint)
                canvas.drawText(label, bgLeft + cornerPad, d.y1 + labelSize + cornerPad, textPaint)
            }
        }
    }
}

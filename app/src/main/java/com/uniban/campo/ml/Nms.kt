package com.uniban.campo.ml

import kotlin.math.max
import kotlin.math.min

object Nms {

    fun iou(a: Detection, b: Detection): Float {
        val ix1 = max(a.x1, b.x1)
        val iy1 = max(a.y1, b.y1)
        val ix2 = min(a.x2, b.x2)
        val iy2 = min(a.y2, b.y2)
        val iw = max(0f, ix2 - ix1)
        val ih = max(0f, iy2 - iy1)
        val inter = iw * ih
        if (inter <= 0f) return 0f
        val union = a.width * a.height + b.width * b.height - inter
        return if (union <= 0f) 0f else inter / union
    }

    /** Supresión de no-máximos estándar, aplicada de forma independiente por clase. */
    fun nms(detections: List<Detection>, iouThreshold: Float): List<Detection> {
        if (detections.isEmpty()) return emptyList()
        return detections.groupBy { it.classId }.values.flatMap { perClass ->
            val sorted = perClass.sortedByDescending { it.score }
            val keep = mutableListOf<Detection>()
            val suppressed = BooleanArray(sorted.size)
            for (i in sorted.indices) {
                if (suppressed[i]) continue
                val a = sorted[i]
                keep.add(a)
                for (j in i + 1 until sorted.size) {
                    if (suppressed[j]) continue
                    if (iou(a, sorted[j]) >= iouThreshold) suppressed[j] = true
                }
            }
            keep
        }
    }

    /**
     * Fusiona detecciones provenientes de distintos tiles:
     * primero NMS global por clase y luego une centros muy cercanos
     * (plantas detectadas en la zona de superposición de dos tiles).
     */
    fun mergeTileDetections(
        detections: List<Detection>,
        iouThreshold: Float = YoloConfig.TILE_MERGE_IOU,
        centerRatio: Float = YoloConfig.TILE_MERGE_CENTER_RATIO
    ): List<Detection> {
        val afterNms = nms(detections, iouThreshold)
        val sorted = afterNms.sortedByDescending { it.score }.toMutableList()
        val keep = mutableListOf<Detection>()
        while (sorted.isNotEmpty()) {
            val best = sorted.removeAt(0)
            keep.add(best)
            val minSide = max(1f, min(best.width, best.height))
            val maxDist = centerRatio * minSide
            sorted.removeAll { other ->
                other.classId == best.classId &&
                    kotlin.math.abs(other.centerX - best.centerX) <= maxDist &&
                    kotlin.math.abs(other.centerY - best.centerY) <= maxDist
            }
        }
        return keep
    }
}

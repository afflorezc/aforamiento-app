package com.uniban.campo.ml

data class Detection(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val classId: Int,
    val score: Float
) {
    val width: Float get() = x2 - x1
    val height: Float get() = y2 - y1
    val centerX: Float get() = (x1 + x2) / 2f
    val centerY: Float get() = (y1 + y2) / 2f
}

object CropType {
    const val PLATANO = "PLATANO"
    const val BANANO = "BANANO"

    val labels = mapOf(PLATANO to "Plátano", BANANO to "Banano")
}

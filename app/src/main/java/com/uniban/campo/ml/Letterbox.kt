package com.uniban.campo.ml

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream

data class LetterboxMeta(
    val scale: Float,
    val padX: Float,
    val padY: Float,
    val outWidth: Int,
    val outHeight: Int
) {
    /** Convierte una caja en coordenadas del letterbox a coordenadas del bitmap original. */
    fun toOriginal(x1: Float, y1: Float, x2: Float, y2: Float): FloatArray {
        val ox1 = (x1 - padX) / scale
        val oy1 = (y1 - padY) / scale
        val ox2 = (x2 - padX) / scale
        val oy2 = (y2 - padY) / scale
        return floatArrayOf(ox1, oy1, ox2, oy2)
    }

}

object Letterbox {

    /** Escala y centra [src] en un cuadrado de [size]x[size] con color de relleno 114 (estándar YOLO). */
    fun apply(src: Bitmap, size: Int = YoloConfig.INPUT_SIZE): Pair<Bitmap, LetterboxMeta> {
        val srcW = src.width
        val srcH = src.height
        val scale = minOf(size.toFloat() / srcW, size.toFloat() / srcH)
        val scaledW = (srcW * scale).toInt().coerceAtLeast(1)
        val scaledH = (srcH * scale).toInt().coerceAtLeast(1)
        val padX = (size - scaledW) / 2f
        val padY = (size - scaledH) / 2f

        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        canvas.drawColor(Color.rgb(114, 114, 114))
        canvas.drawBitmap(
            src,
            null,
            android.graphics.RectF(padX, padY, padX + scaledW, padY + scaledH),
            paint
        )

        return out to LetterboxMeta(scale, padX, padY, size, size)
    }
    /** Guarda la imagen preprocesada antes de ingresar al modelo */
    fun saveBitmap(context: Context, bitmap: Bitmap, filename: String) {
        val dir = File(context.filesDir, "debug")
        dir.mkdirs()

        val file = File(dir, filename)

        FileOutputStream(file).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }

        Log.i("Letterbox", "Imagen guardada en: ${file.absolutePath}")
    }
}

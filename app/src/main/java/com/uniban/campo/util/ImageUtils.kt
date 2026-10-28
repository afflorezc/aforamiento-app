package com.uniban.campo.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.uniban.campo.ml.YoloConfig
import com.uniban.campo.util.tiff.TiffDecoder
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object ImageUtils {

    val allowedExtensions = setOf("jpg", "jpeg", "png", "tif", "tiff", "bmp")
    val allowedMimeTypes = arrayOf("image/jpeg", "image/png", "image/bmp", "image/tiff", "image/*")

    fun extensionOf(file: File): String =
        file.extension.lowercase()

    fun isSupported(file: File): Boolean =
        extensionOf(file) in allowedExtensions

    /** Copia el contenido del Uri a [destDir] conservando la extensión. Devuelve el archivo copiado. */
    fun copyUriToDir(context: Context, uri: Uri, destDir: File, baseName: String): File {
        destDir.mkdirs()
        val ext = resolveExtension(context, uri)
        if (ext !in allowedExtensions) {
            throw IOException("Formato no soportado: .$ext. Use jpg, jpeg, tif, tiff, bmp o png")
        }
        val dest = File(destDir, "${baseName}.$ext")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(dest).use { output -> input.copyTo(output) }
        } ?: throw IOException("No se pudo leer la imagen seleccionada")
        return dest
    }

    private fun resolveExtension(context: Context, uri: Uri): String {
        val fromPath = uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase()
        if (!fromPath.isNullOrBlank() && fromPath.length <= 5) return fromPath
        return context.contentResolver.getType(uri)
            ?.substringAfterLast('/')
            ?.lowercase()
            ?: "jpg"
    }

    /** Decodifica la imagen completa en RGB_565 (bajo consumo de memoria para ortomosaicos). */
    fun decodeFullImage(file: File): Bitmap {
        if (TiffDecoder.isTiff(file)) {
            return TiffDecoder.decode(file)
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IOException("No se pudo decodificar la imagen")
        }

        // Intentar resolución completa primero
        val full = decodeWithConfig(file, inSampleSize = 1)
            ?: throw OutOfMemoryError("Imagen demasiado grande para la memoria disponible")

        // Si la imagen es enorme, reducir al doble hasta que quepa
        var sample = 1
        var bitmap: Bitmap? = full
        val maxPixels = 64_000_000 // ~256 MB en ARGB; en RGB_565 es la mitad
        while (bitmap != null && bitmap.width.toLong() * bitmap.height > maxPixels) {
            bitmap.recycle()
            sample *= 2
            bitmap = decodeWithConfig(file, inSampleSize = sample)
        }
        return bitmap ?: throw OutOfMemoryError("Imagen demasiado grande para la memoria disponible")
    }

    private fun decodeWithConfig(file: File, inSampleSize: Int): Bitmap? = try {
        BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
        )
    } catch (e: OutOfMemoryError) {
        null
    }

    fun readDimensions(file: File): Pair<Int, Int> {
        if (TiffDecoder.isTiff(file)) {
            val bmp = TiffDecoder.decode(file)
            val dims = bmp.width to bmp.height
            bmp.recycle()
            return dims
        }
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        return opts.outWidth to opts.outHeight
    }

    fun saveJpeg(bitmap: Bitmap, file: File) {
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, YoloConfig.JPEG_QUALITY, out)
            out.flush()
        }
    }
}

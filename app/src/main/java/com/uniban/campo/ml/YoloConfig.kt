package com.uniban.campo.ml

object YoloConfig {
    /** Tamaño de entrada con el que fue entrenado el modelo (Ultralytics). */
    const val INPUT_SIZE = 864

    /** Número de clases del modelo: platano y banano. */
    const val NUM_CLASSES = 2
    val CLASS_NAMES = listOf("platano", "banano")

    const val CONF_THRESHOLD = 0.25f
    const val IOU_THRESHOLD = 0.45f

    /** Nombre del archivo del modelo dentro de assets/models/ o filesDir/models/. */
    const val MODEL_FILE_NAME = "model.tflite"
    const val MODELS_DIR = "models"

    /**
     * Si el lado mayor de la imagen supera este valor (ortomosaicos),
     * se particiona en tiles con superposición.
     */
    const val TILE_THRESHOLD_PX = 8000

    /** Tamaño de cada tile y fracción de superposición entre tiles. */
    const val TILE_SIZE = 2048
    const val TILE_OVERLAP = 0.15f

    /** IoU mínima para considerar que dos bbox de tiles distintos son la misma planta. */
    const val TILE_MERGE_IOU = 0.40f

    /**
     * Umbral adicional por distancia entre centros (fracción del lado menor de la bbox)
     * para eliminar duplicados casi idénticos con IoU bajo.
     */
    const val TILE_MERGE_CENTER_RATIO = 0.25f

    const val JPEG_QUALITY = 92
}

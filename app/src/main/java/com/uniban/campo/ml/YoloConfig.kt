package com.uniban.campo.ml

object YoloConfig {
    /** Tamaño de entrada con el que fue entrenado el modelo (Ultralytics). */
    const val INPUT_SIZE = 864

    /** Número de clases del modelo: platano y banano. */
    const val NUM_CLASSES = 2
    val CLASS_NAMES = listOf("platano", "banano")

    /**
     * Umbral de confianza. 0.15 prioriza recall (conteo de plantas);
     * baje a 0.10 si el logcat muestra maxScore entre 0.10 y 0.15.
     */
    const val CONF_THRESHOLD = 0.15f
    const val IOU_THRESHOLD = 0.30f

    /** Nombre del archivo del modelo dentro de assets/models/ o filesDir/models/. */
    const val MODEL_FILE_NAME = "model.tflite"
    const val MODELS_DIR = "models"

    /**
     * Lado mayor a partir del cual se particiona en tiles con superposición.
     * Con un solo reescalado a 864 px las plantas de ortomosaicos medianos
     * (2560-8000 px) quedan en pocos píxeles y el modelo las ignora; tiling
     * antes conserva la escala de las plantas. Las imágenes de más de
     * 8000 px (el caso del enunciado) se parten siempre por estar por encima.
     */
    const val TILE_THRESHOLD_PX = 2560

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

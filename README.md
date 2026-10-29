# Unibán Campo — App Android de estimación de plantas de plátano/banano

Aplicación Kotlin (Jetpack Compose) que estima el conteo de plantas de plátano o banano
en una parcela a partir de una fotografía UAV en posición nadir, usando un modelo YOLO
previamente entrenado con Ultralytics. Funciona **offline** y sincroniza automáticamente
cuando hay conexión.

La interfaz replica el diseño del prototipo web (Unibán Campo): verde `#1B5E20`,
tarjetas blancas con bordes `#D8E5D9`, esquinas de 12–16 dp y tipografía Outfit.

## Funcionalidades

- **Login** con correo y contraseña (cualquier valor; sin backend de autenticación).
- **Estimación**: selector de parcela, tipo de plantación (plátano/banano), área a
  estimar (no puede superar el `area_ha` de la parcela) y carga de imagen
  (jpg, jpeg, png, tif, tiff, bmp).
- **Modelo YOLO (TFLite)** entrenado a 864×864 px:
  - Imágenes pequeñas (lado ≤ 2560 px, ver `YoloConfig.TILE_THRESHOLD_PX`): se
    reescalan con letterbox y se infiere una vez.
  - Imágenes con lado mayor a 2560 px (incluye los ortomosaicos de más de 8000 px
    del enunciado): se **particionan en tiles** de 2048 px con
    15 % de superposición; las detecciones se trasladan a coordenadas globales y se
    **eliminan los duplicados en las intersecciones** (NMS global por clase + distancia
    entre centros). El total = suma de detecciones de plátano y banano.
    El tiling temprano conserva la escala de las plantas: con un único reescalado a
    864 px las coronas de una parcela mediana quedarían en ~10 px y el modelo las ignora.
- **Imagen de salida** con las mismas dimensiones que la original, con las bbox YOLO
  dibujadas y etiquetadas. Imagen de entrada y de salida se guardan en
  `filesDir/estimations/`.
- **BD local (Room)** con el modelo entidad-relación solicitado:
  - `parcel(id, name, area_ha)` — sincronizada desde el servicio en la nube.
  - `crop_estimation(id, date, input_img, measured_area, estimated_plants,
    confidence, output_img, parcel_id, status)` — `status` es `PENDING` | `SENT`.
- **Sincronización offline-first** (WorkManager cada 15 min + al abrir la app + manual):
  - `GET /api/v1/service/parcels` → actualiza la BD local (`area_ha` llega como string
    y se convierte a float).
  - `POST /api/v1/aforamientos` con las estimaciones `PENDING` y las marca `SENT`.
  - Header `X-API-Key` (sin Bearer), valor tomado del archivo `.env`.

## Requisitos

- Android Studio (Ladybug o superior), JDK 17+.
- Android SDK 35.
- Modelo `.pt` entrenado con Ultralytics (clases: plátano y banano) y Python con
  `ultralytics` para exportarlo a TFLite.

## Configuración

1. **API key** — edite el archivo `.env` en la raíz del proyecto:

   ```
   X_API_KEY=su_clave_real_aquí
   ```

   La clave se inyecta en `BuildConfig.X_API_KEY` en cada compilación.

2. **Modelo YOLO** — exporte el `.pt` y colóquelo en
   `app/src/main/assets/models/model.tflite` (instrucciones en
   `app/src/main/assets/models/README.md`):

   ```bash
   yolo export model=modelo.pt format=tflite imgsz=864
   ```

   La app corre la inferencia con la misma configuración validada en Python con
   Ultralytics: `imgsz=864`, `iou=0.30`, `max_det=600`, dispositivo CPU
   (4 hilos nativos), umbral de confianza 0.15 (`YoloConfig`).

   **Precisión de la exportación (tipo de dato):**

   | Comando | Tipo resultante | Tamaño (nano) | Estado en la app |
   |---|---|---|---|
   | `format=tflite imgsz=864` | pesos **float32** (interfaz float32) | ~11 MB | Soportado — recomendado |
   | `... half=True` | pesos **float16**, interfaz float32 (dequantiza en runtime) | ~5.5 MB | Soportado — misma precisión práctica |
   | `... int8=True` | pesos/activaciones **int8** (interfaz int8/uint8) | ~3 MB | Rechazado: requiere cuantización en la app |

   En logcat, `YoloDetector` imprime al cargar:
   `Exportación detectada: entrada=FLOAT32, salida=FLOAT32, tamaño=…` — eso indica
   el tipo que entregó la exportación. **Recomendación móvil: mantener float32**
   (el nano ya es ligero y es la máxima precisión); `half=True` es el alternativo
   válido si se quiere reducir el APK/modelo a la mitad con impacto despreciable;
   evite `int8=True` salvo validar mAP y extender el runtime de inferencia.

3. Abra el proyecto en Android Studio y ejecute la configuración `app`.

## Notas de implementación

- **Tiling**: para imágenes con lado mayor a `TILE_THRESHOLD_PX` (2560 px) se crean
  tiles de 2048 px con 15 % de solapamiento; cada tile se letterbox-a 864 px, se
  infiere y las cajas se trasladan con el offset del tile; el merge final usa NMS
  (IoU 0.40) y distancia entre centros (25 % del lado menor de la caja) para no
  contar dos veces la misma planta en la zona de intersección de dos tiles.
  El umbral de confianza por defecto es 0.15 (`YoloConfig.CONF_THRESHOLD`).
- **Bitmaps**: la imagen de entrada se decodifica con `inMutable = true` (y con un
  respaldo por `copy`) porque `Canvas` lanza "Immutable bitmap passed to Canvas
  constructor" sobre bitmaps de `BitmapFactory`.
- **TIFF**: Android no decodifica TIFF nativamente; se incluye un decodificador puro
  en Kotlin (`util/tiff/TiffDecoder.kt`) que soporta tiras o teselas, compresión
  ninguna/LZW/Deflate/PackBits, 8/16 bits, grises/RGB/paleta.
- **Memoria**: las imágenes se decodifican en RGB_565 y la app declara `largeHeap`.
- **Endpoint de envío**: el cuerpo JSON enviado a `/api/v1/aforamientos` es
  `{ id, parcelId, date, areaHa, estimatedPlants, confidence }` con `date`
  (`yyyy-MM-dd`) igual al día de ejecución del modelo.

## Estructura

```
app/src/main/java/com/uniban/campo/
├── CampoApp.kt               # Arranque, sync periódica
├── MainActivity.kt           # Login + navegación inferior
├── ui/                       # Pantallas Compose (tema Unibán Campo)
├── data/
│   ├── db/                   # Room: parcel, crop_estimation
│   ├── remote/               # Retrofit + X-API-Key
│   ├── repo/                 # Repositorio, SyncManager, SyncWorker
│   └── prefs/                # Sesión
├── ml/                       # YoloTfliteDetector, Letterbox, Nms, PlantCounter
└── util/                     # Connectivity, ImageUtils, tiff/TiffDecoder
```

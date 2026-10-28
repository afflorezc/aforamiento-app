# Modelo YOLO

Coloque aquí el modelo exportado con el nombre exacto:

```
model.tflite
```

El modelo original en formato `.pt` (entrenado con Ultralytics a 864px sobre plantas de
plátano y banano) debe exportarse una sola vez desde una estación de trabajo con
Python y Ultralytics:

```bash
pip install ultralytics
yolo export model=ruta/al/modelo.pt format=tflite imgsz=864
```

Esto genera `modelo_saved_model/model.tflite` (float32). Copie el archivo como
`app/src/main/assets/models/model.tflite` y vuelva a compilar.

Alternativa sin recompilar (depuración): empuje el modelo al almacenamiento interno
de la app en un dispositivo/emulador:

```bash
adb push model.tflite /data/local/tmp/model.tflite
adb shell run-as com.uniban.campo mkdir -p files/models
adb shell run-as com.uniban.campo cp /data/local/tmp/model.tflite files/models/model.tflite
```

La app busca primero en `filesDir/models/model.tflite` y luego en `assets/models/model.tflite`.

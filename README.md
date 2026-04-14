# AugmentedRealityApplication
This repository contains two projects:
- `AugmentedReality/` — Android app (Jetpack Compose + CameraX + EfficientDet Lite2)
- `ApplicationServer/` — Kotlin/Ktor + LDAP + ML backend
## Model file (not tracked in git)
The Android app requires this model at runtime:
- `AugmentedReality/app/src/main/assets/efficientdet_lite2.tflite`
Download and place it:
```bash
cd AugmentedReality
curl -L "https://storage.googleapis.com/tfhub-lite-models/tensorflow/lite-model/efficientdet/lite2/detection/metadata/1.tflite" \
  -o app/src/main/assets/efficientdet_lite2.tflite
```

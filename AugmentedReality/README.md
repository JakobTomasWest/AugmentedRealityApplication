# Augmented Reality Object Detection

A modern Android app that performs **real-time on-device object detection** using the device camera. Built with Jetpack Compose, CameraX, and TensorFlow Lite EfficientDet Lite2 — with a Ktor backend for authenticated photo storage and server-side analysis.

---

## ✨ Features

- **Real-Time Detection** — Live camera preview with bounding box overlays and confidence scores at ~12 FPS
- **EfficientDet Lite2** — On-device TFLite inference (no network required for detection)
- **Filtered Detection Classes** — Surfaces only the five most useful classes: `person`, `laptop`, `cell phone`, `bottle`, `chair`
- **Label Aliasing** — Collapses common model misdetections (e.g. `refrigerator` → `laptop`, `couch` → `chair`) via a tested alias map
- **Photo Capture & Upload** — Saves to device MediaStore, uploads to a Ktor backend, runs server-side ML analysis
- **Secure Auth** — JWT login/signup, token persisted via DataStore, restored on app relaunch
- **Gallery View** — Browse and re-analyze uploaded photos
- **USB Dev Workflow** — Uses `adb reverse` tunnel so the physical device reaches the dev server without Wi-Fi

---

## 🛠 Tech Stack

| Component | Technology | Version |
|---|---|---|
| **UI Framework** | Jetpack Compose (Material3) | BOM 2024.09.00 |
| **Architecture** | MVVM + StateFlow + Intent pattern | — |
| **Camera** | CameraX (Preview, ImageCapture, ImageAnalysis) | 1.5.0-alpha05 |
| **Object Detection** | TensorFlow Lite EfficientDet Lite2 | TFLite Task Vision 0.4.4 |
| **Networking** | Ktor Client (Android) | 3.1.2 |
| **Auth Persistence** | Jetpack DataStore (Preferences) | 1.1.1 |
| **Image Loading** | Coil Compose | 2.6.0 |
| **Language** | Kotlin | 2.0.21 |
| **Min / Target SDK** | 24 (Android 7.0) / 36 (Android 16) | — |

---

## 🚀 Getting Started

### Prerequisites

| Requirement | Notes |
|---|---|
| Android Studio Ladybug (2024.3+) | or newer |
| JDK 17+ | Required by Android Gradle Plugin 8.x |
| Android SDK 36 | Install via SDK Manager |
| ADB | Bundled with Android SDK platform-tools |

### 1. Clone & configure

```bash
git clone <repo-url>
cd AugmentedReality

# Copy the example and fill in your SDK path (and optionally your JDK path)
cp local.properties.example local.properties
```

Edit `local.properties`:
```properties
sdk.dir=/Users/<you>/Library/Android/sdk

# Only needed if your default Java is not 17:
# org.gradle.java.home=/path/to/jdk-17
```

### 2. Add the model file

The EfficientDet Lite2 model is not committed to the repo (binary asset, ~6 MB).
Download it from TensorFlow Hub and place it in the assets folder:

```bash
curl -L "https://storage.googleapis.com/tfhub-lite-models/tensorflow/lite-model/efficientdet/lite2/detection/metadata/1.tflite" \
     -o app/src/main/assets/efficientdet_lite2.tflite
```

### 3. Build & run

```bash
./gradlew :app:assembleDebug          # build only
./gradlew :app:installDebug           # build + install to connected device
```

> `installDebug` automatically runs `adb reverse tcp:8080 tcp:8080` before each install so your physical device can reach the local Ktor dev server on port 8080 over USB — no Wi-Fi needed.

---

## 🖥 API Server Setup (optional)

The app works fully offline for detection. The backend is only needed for photo upload, gallery, and server-side analysis.

```bash
cd notesServerLdapJwt
docker compose up -d
```

| Container | Description | Port |
|---|---|---|
| `ktor-app` | Kotlin/Ktor REST API | 8080 |
| `openldap` | LDAP user directory | 1389 |
| `ml` | Python FastAPI detection service | — |

**API endpoints:**

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/user` | Sign up |
| `POST` | `/api/auth` | Login → JWT |
| `POST` | `/api/upload/{name}` | Upload photo |
| `GET` | `/api/upload` | List uploaded photos |
| `POST` | `/api/ai/detect-bytes` | Server-side object detection |
| `GET` | `/api/ai/detect-name/{filename}` | Detect by filename |

---

## 📱 App Walkthrough

### Camera Screen
- Live preview with real-time bounding box overlay
- Front/back camera toggle — detections clear on switch
- Capture button — saves to device, uploads to server, shows server-side detection result

### Gallery Screen
- Grid of all photos uploaded to the server
- Tap any photo to run server-side ML analysis
- Prompts for login automatically if not signed in

### Auth
- Sign up / login / logout
- JWT token persisted in DataStore and restored on next launch

---

## 🏗 Architecture

```
UI Layer (Jetpack Compose)
        │  collectAsState / handle(intent)
        ▼
CameraViewModel  (AndroidViewModel + viewModelScope)
        │
        ├── CameraX  (Preview · ImageCapture · ImageAnalysis)
        ├── LabelFilter  (pure Kotlin object, fully unit-tested)
        ├── ApiClient  (Ktor, JWT Bearer auth)
        ├── TokenStore  (DataStore, persists JWT + username)
        └── MediaStoreSaver  (device MediaStore integration)
```

### Key design choices

| Problem | Solution |
|---|---|
| Concurrent bind/unbind races on camera | `cameraOpsMutex: Mutex` serialises all camera ops |
| Correct aspect ratio on all devices | `ViewPort.FIT` + `SurfaceRequest.TransformationInfo` drives `aspectRatio()` |
| Label noise from EfficientDet | `LabelFilter.ALIASES` maps 13 common misdetections to 5 target classes |
| Session persistence across launches | `TokenStore.sessionFlow` collected in `init {}` restores auth state |
| Physical device + local dev server | `adb reverse tcp:8080 tcp:8080` automated in Gradle `doFirst` |

---

## 📂 Project Structure

```
app/src/main/
├── java/com/example/augmentedreality/
│   ├── MainActivity.kt                  # Edge-to-edge Compose entry point
│   ├── ui/
│   │   ├── CameraContract.kt            # Immutable UiState + sealed CameraIntent
│   │   ├── CameraViewModel.kt           # Camera lifecycle, detection pipeline, auth
│   │   ├── CameraScreen.kt              # Camera preview + bounding box Canvas overlay
│   │   └── GalleryScreen.kt             # Photo grid + auth dialog
│   ├── detection/
│   │   └── LabelFilter.kt               # COCO-80 label filtering & alias map
│   ├── net/
│   │   ├── ApiClient.kt                 # Ktor HTTP client, all API calls
│   │   └── TokenStore.kt                # DataStore JWT + username persistence
│   └── data/
│       └── MediaStoreSaver.kt           # MediaStore photo capture
├── assets/
│   └── efficientdet_lite2.tflite        # TFLite model (not in VCS — download separately)
└── res/
    └── xml/network_security_config.xml  # Cleartext HTTP only for localhost / 10.0.2.2
```

---

## 🧪 Tests

```bash
./gradlew :app:testDebugUnitTest    # unit tests — JVM only, no device needed
./gradlew :app:connectedCheck       # instrumented tests — requires device/emulator
./gradlew :app:lint                 # static analysis
```

**`LabelFilterTest`** — 30 unit tests covering:
- Label pass-through for all 5 allowed classes
- Alias mapping (13 aliases → canonical labels)
- Filtering of non-allowed COCO-80 classes
- Edge cases: null, blank string, case-insensitivity, leading/trailing whitespace
- COCO-80 list integrity (exactly 80 entries, all aliases resolve to allowed labels)

---

## 🔐 Security

- Cleartext HTTP allowed only for `localhost` and `10.0.2.2` (dev only); all other traffic requires HTTPS
- JWT tokens stored in DataStore (not SharedPreferences)
- Bearer token attached to all authenticated API requests
- Runtime permissions for `CAMERA` and `READ_MEDIA_IMAGES`

---

## 📄 License

MIT License — see [LICENSE](LICENSE) for details.

---

## 👤 Author

**Jakob West**  
University of Utah — MSD Program  
CS 6018 — Mobile Software Development

*Last updated: April 2026*

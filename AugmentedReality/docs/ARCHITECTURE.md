# Architecture & Design Decisions

## Overview

This document explains the architectural decisions and design patterns used in the Augmented Reality Object Detection application.

## Architecture Pattern: MVVM (Model-View-ViewModel)

The project follows the **Model-View-ViewModel (MVVM)** architectural pattern, which is the recommended approach for Android applications using Jetpack Compose.

```
┌──────────────────────────────────────────────┐
│          UI Layer (Jetpack Compose)          │
│   - CameraScreen.kt (Camera UI)              │
│   - GalleryScreen.kt (Gallery UI)            │
└────────────────┬─────────────────────────────┘
                 │ observes
                 ↓
┌──────────────────────────────────────────────┐
│     ViewModel (Business Logic & State)       │
│   - CameraViewModel.kt                       │
│     • Camera lifecycle management            │
│     • Detection model orchestration          │
│     • Authentication state                   │
│     • Photo management                       │
└────────────────┬─────────────────────────────┘
                 │ uses
                 ↓
┌──────────────────────────────────────────────┐
│      Data Layer (APIs & Repositories)        │
│   - ApiClient.kt                             │
│   - TokenStore.kt                            │
│   - MediaStoreSaver.kt                       │
└────────────────┬─────────────────────────────┘
                 │ accesses
                 ↓
┌──────────────────────────────────────────────┐
External Services & Frameworks
│   - CameraX (camera control)
│   - TensorFlow Lite — EfficientDet Lite2 (on-device detection)
│   - Ktor Client (HTTP networking)
│   - DataStore (secure persistence)
└──────────────────────────────────────────────┘
```

### Why MVVM?

1. **Separation of Concerns** - UI logic separated from business logic
2. **Testability** - ViewModels can be tested independently of Compose UI
3. **State Management** - `StateFlow` provides reactive, observable state
4. **Lifecycle Aware** - ViewModel survives configuration changes
5. **Compose Integration** - Native support in Jetpack Compose

## Data Flow

### Camera Detection Pipeline

```
Camera Frame (YUV)
    ↓
ImageAnalysis use-case (CameraX)
    ↓
EfficientDet Lite2 (TFLite Task Vision)
    ↓
LabelFilter.normalize() — alias + allowlist filtering
    ↓
bounding boxes + confidence scores (5 classes only)
    ↓
StateFlow update in ViewModel
    ↓
Compose recomposes UI with overlay
```

### Authentication Flow

```
User Input (username/password)
    ↓
CameraViewModel.login() or signup()
    ↓
ApiClient.login() or ApiClient.signUp()
    ↓
POST /api/auth or /api/user
    ↓
JWT Token Response
    ↓
TokenStore.save(token)
    ↓
Token persisted in DataStore
    ↓
Included in all subsequent API requests via Bearer token
```

### Photo Capture & Upload

```
User taps "Save Photo" button
    ↓
CameraViewModel.takePhoto()
    ↓
ImageCapture.takePicture() (CameraX)
    ↓
MediaStoreSaver.savePhoto() (saves to device)
    ↓
ApiClient.uploadImageBytes() (upload to server)
    ↓
Photo accessible from gallery with token auth
```

## State Management: StateFlow

The app uses Kotlin `StateFlow` for reactive state management instead of `LiveData`:

```kotlin
// In CameraViewModel
private val _uiState = MutableStateFlow<CameraUiState>(
    CameraUiState(...)
)
val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()
```

### Advantages:

- Works seamlessly with coroutines
- No lifecycle-aware requirements
- Easy to test
- Efficient recomposition in Compose
- Can be collected as Flow in tests

## Contract Pattern: CameraContract

The UI state and intents are defined in `CameraContract.kt` using sealed types:

```kotlin
// UI state - all data needed for rendering
data class CameraUiState(
    val hasCamera: Boolean,
    val detections: List<Detection>,
    val lensPosition: LensPosition,
    ...
)

// User intents - all possible user actions
sealed interface CameraIntent {
    object ToggleLens : CameraIntent
    object CapturePhoto : CameraIntent
    data class OnPermissionsResult(val granted: Boolean) : CameraIntent
    ...
}
```

### Benefits:

- Clear contract between UI and ViewModel
- All state is explicit and immutable
- All user actions are typed
- Easy to test behavior

## Detection Model: EfficientDet Lite2 (TensorFlow Lite)

The app uses a single on-device detector — **EfficientDet Lite2** via the TFLite Task Vision library.

```kotlin
// In CameraViewModel
private val efficientDetLite2 by lazy {
    val opts = ObjectDetector.ObjectDetectorOptions.builder()
        .setScoreThreshold(0.3f)
        .setMaxResults(6)
        .build()
    ObjectDetector.createFromFileAndOptions(
        getApplication(), "efficientdet_lite2.tflite", opts
    )
}
```

Label filtering and alias resolution are handled by `LabelFilter` (a pure Kotlin object), which is fully unit-tested:

```kotlin
// LabelFilter.kt
object LabelFilter {
    val ALLOWED = setOf("cell phone", "bottle", "person", "laptop", "chair")
    val ALIASES = mapOf("refrigerator" to "laptop", "couch" to "chair", ...)

    fun normalize(raw: String?): String? { ... }
}
```

### Design Decision: single model, curated class list

- **Why EfficientDet Lite2 only?** On-device inference means the detection feature works without any network connection — no latency, no cost, no privacy concerns.
- **Why only 5 classes?** EfficientDet Lite2 predicts 80 COCO classes, but most are noisy in indoor scenes. Filtering to `person`, `laptop`, `cell phone`, `bottle`, `chair` dramatically reduces false positives and makes the overlay immediately useful.
- **Why an alias map?** The model frequently misclassifies laptops as `refrigerator` or `book`. The alias map redirects these to the correct canonical class rather than discarding the detection entirely.

## Coordinate Transformation: Complex Problem Solved

The app handles a complex coordinate transformation problem:

```
Camera sensor (1920x1440)
        ↓ (rotate based on device orientation)
Detection analyzer buffer coordinates
        ↓ (ML model outputs normalized 0.0-1.0)
Normalized bounding boxes
        ↓ (scale to preview surface resolution)
Preview/UI coordinates
        ↓
Render on Canvas
```

This is handled in:
   - `CameraScreen.kt` — applies transformations in Canvas drawing

**Why this matters**: Real-time detection requires pixel-perfect coordinate mapping to ensure overlays appear exactly where objects are detected, even as device rotates.

## Networking: Ktor Client

Uses Ktor Client for HTTP communication with these features:

- **JSON Serialization**: Kotlinx Serialization with automatic encoding/decoding
- **Logging**: DEBUG level logging shows all HTTP traffic (configurable)
- **Timeout Handling**: 30-second request timeout
- **Error Handling**: Custom `HttpStatusException` for non-2xx responses
- **Authentication**: JWT Bearer token added to protected requests

### Configuration (app/build.gradle.kts):

```gradle
buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8080\"")
```

This allows changing the backend URL without code changes (perfect for different environments).

## Persistence: DataStore over SharedPreferences

Token persistence uses DataStore instead of SharedPreferences:

```kotlin
// TokenStore.kt
class TokenStore(context: Context) {
    private val dataStore: DataStore<Preferences> = context.createDataStore("app_prefs")
    
    fun save(token: String) = dataStore.edit { settings ->
        settings[TOKEN_KEY] = token
    }
}
```

### Why DataStore?

- **Type-safe**: Compiler time safety for keys
- **Kotlin-first**: Uses suspend functions and coroutines
- **Modern**: Recommended replacement for SharedPreferences
- **Atomic operations**: All-or-nothing updates

## Critical Performance Considerations

### 1. ImageAnalysis Frame Rate
- Processes frames at ~30 FPS
- Uses `YUV_420_888` format for efficient memory
- Runs on background thread (doesn't block UI)

### 2. TensorFlow Lite Model
- `efficientdet_lite4` (~25 MB)
- ~90ms inference on modern devices
- Loaded once on first use (`by lazy`)
- Handles 80 object classes (COCO dataset)

### 3. Compose Recomposition
- `CameraScreen` recomposes when detections change
- Canvas rendering optimized for frequent updates
- Only recalculate transforms when rotation changes

## Testing Strategy

### Unit Tests (to add)
```
CameraViewModel
  ├── test authentication state changes
  ├── test detection list updates
  └── test coordinate transformations

TokenStore
  ├── test token persistence
  └── test token retrieval

ApiClient
  ├── test login error handling
  └── test upload retry logic
```

### Integration Tests (to add)
```
CameraScreen
  ├── test permission flow
  └── test UI state rendering

GalleryScreen
  └── test photo grid rendering
```

## Security

### API Authentication
- JWT tokens stored securely in DataStore
- Tokens transmitted via Bearer scheme
- No sensitive data logged (debug logging configurable)

### Network Security
- Network security configuration (XML) restricts cleartext to localhost only
- Production would enable HTTPS only

### Permissions
- Runtime permission requests for CAMERA and READ_MEDIA_IMAGES
- Graceful handling if permissions denied

## Potential Future Improvements

1. **Caching Layer** - Cache API responses in local database (Room)
2. **Offline Support** - Store detections locally, sync when online
3. **Custom Model Training** - Fine-tune EfficientDet on custom classes
4. **Real-time Video Recording** - Record camera feed with detection overlay
5. **Analytics** - Track detection accuracy over time
6. **Accessibility** - Content descriptions, dark mode, larger fonts
7. **Error Recovery** - Exponential backoff for failed uploads
8. **Privacy** - On-device-only mode without uploading

## Development Practices

- **Kotlin idioms** - Leverage Kotlin features (extension functions, data classes, sealed types)
- **Coroutine safety** - Use proper scope management (ViewModelScope)
- **Resource cleanup** - Proper camera and detector lifecycle
- **Error safety** - Try-catch wrapped async operations

---

**Key Insight**: This project demonstrates real-world Android development challenges:
- Camera integration and real-time processing
- ML inference (on-device and cloud)
- Async networking and authentication
- Complex coordinate transformations
- Reactive state management with Compose

---

Last Updated: April 2026

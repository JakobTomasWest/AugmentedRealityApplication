package com.example.augmentedreality.ui

import android.app.Application
import android.content.Context
import android.graphics.RectF
// import android.graphics.Bitmap
import android.net.Uri
import android.util.Rational
import android.view.Surface
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.Log
import androidx.camera.core.AspectRatio
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import java.util.concurrent.Executors
import androidx.camera.core.CameraSelector
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.example.augmentedreality.data.MediaStoreSaver
import com.example.augmentedreality.net.ApiClient
import com.example.augmentedreality.net.TokenStore
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.lifecycle.LifecycleOwner

import kotlin.getValue
// TFLite Task Vision (EfficientDet)
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import org.tensorflow.lite.task.vision.detector.ObjectDetector.ObjectDetectorOptions as TfLiteODOptions
import org.tensorflow.lite.support.label.Category
import android.view.WindowManager
import io.ktor.http.ContentType

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.firstOrNull
// import java.io.ByteArrayOutputStream

import com.example.augmentedreality.net.HttpStatusException
import com.example.augmentedreality.detection.LabelFilter

// RawDetection
data class RawDetection(
    val rectPx: androidx.compose.ui.geometry.Rect,
    val label: String,
    val score: Float
)


// ViewModel holds UI state and business logic for the camera screen.
class CameraViewModel(app: Application) : AndroidViewModel(app) {
    override fun onCleared() {
        super.onCleared()
        analysisExecutor.shutdown()
    }

    // Server client & auth storage — declared first so init block can safely use them
    private var lastRun = 0L
    private val api = ApiClient()
    private val tokenStore = TokenStore(getApplication())

    init {
        viewModelScope.launch {
            tokenStore.sessionFlow.collect { session ->
                token = session?.token
                username = session?.username
                _authState.value = if (session?.token != null) {
                    AuthState.SignedIn(session.username ?: "Signed in")
                } else {
                    AuthState.SignedOut
                }
            }
        }
    }
    @Volatile
    private var token: String? = null

    private var username: String? = null
    // Ensure that we have a JWT in memory or persisted storage.
    private suspend fun ensureToken(): String {
        token?.let { return it }
        val saved = tokenStore.currentSession()
        val savedToken = saved?.token
        if (savedToken != null) {
            token = savedToken
            username = saved.username
            return savedToken
        }
        throw IllegalStateException("Please sign in first")
    }
    suspend fun listPhotos(): List<String> {
        val t = ensureToken()
        return api.listPhotos(t)
    }

    suspend fun deletePhoto(name: String) {
        val t = ensureToken()
        api.deletePhoto(t, name)
    }

    fun photoUrl(name: String): String = api.photoUrl(name)

    fun currentTokenOrNull(): String? = token
    fun currentUsernameOrNull(): String? = username


    sealed interface AuthState {
        data object SignedOut : AuthState
        data class SignedIn(val username: String) : AuthState
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.SignedOut)
    val authState = _authState.asStateFlow()

    suspend fun login(username: String, password: String) {
        val t = api.login(username, password)
        this.username = username
        token = t
        tokenStore.save(t, username)
        _authState.value = AuthState.SignedIn(username)
    }

    suspend fun signUp(username: String, password: String) {
        try {
            api.signUp(username, password)
        } catch (e: HttpStatusException) {
            if (e.status.value == 409) {
                throw IllegalStateException("Username already taken")
            } else if (e.status.value == 400) {
                throw IllegalStateException("Sign up failed: invalid username or password")
            } else {
                throw IllegalStateException("Sign up failed (${e.status.value})")
            }
        }

        login(username, password)
    }

    fun logout(){
        token = null
        username = null
        _authState.value = AuthState.SignedOut
        viewModelScope.launch {
            runCatching { tokenStore.clear() }
                .onFailure { Log.w("CameraVM", "clear() failed", it) }

        }
    }


    // Backing, mutable state inside the VM
    private val _state = MutableStateFlow(CameraUiState())
    // RO view for Ui to collectAsState without being able to modify
    val state = _state.asStateFlow()

    // Matrix that maps analyzer buffer → sensor (really: inverse of sensorToBuffer for analyzer)
    private val _analyzerToSensor = MutableStateFlow(android.graphics.Matrix())  // analysis-buffer → sensor
    val analyzerToSensor = _analyzerToSensor.asStateFlow()
    private val _rawDetections = MutableStateFlow<List<RawDetection>>(emptyList())
    val rawDetections = _rawDetections.asStateFlow()

    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var currentOwner: LifecycleOwner? = null
    private var lastRotation: Int = Surface.ROTATION_0
    private val cameraOpsMutex = Mutex()
    // Single background thread for image analysis
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private val efficientDetLite2 by lazy {
        val opts = TfLiteODOptions.builder()
            .setScoreThreshold(0.3f)
            .setMaxResults(6)
            .build()
        ObjectDetector.createFromFileAndOptions(
            getApplication(),
            "efficientdet_lite2.tflite",
            opts
        )
    }

    /**
     * Walks all categories for a single detection and returns the best-scoring
     * label that is in [LabelFilter.ALLOWED] (after alias resolution), or null
     * if none qualify.
     */
    private fun preferredAllowedCategory(
        categories: List<Category>
    ): Pair<String, Float>? {
        var bestLabel: String? = null
        var bestScore = 0f

        for (category in categories) {
            val idx = category.index
            val rawLabel = when {
                !category.displayName.isNullOrBlank() -> category.displayName
                !category.label.isNullOrBlank()       -> category.label
                idx in LabelFilter.COCO80.indices     -> LabelFilter.COCO80[idx]
                else                                   -> null
            }
            val label = LabelFilter.normalize(rawLabel) ?: continue
            if (category.score > bestScore) {
                bestLabel = label
                bestScore = category.score
            }
        }

        return bestLabel?.let { it to bestScore }
    }



    private val orientationListener =
        object : android.view.OrientationEventListener(getApplication()) {
            override fun onOrientationChanged(orientation: Int) {
                val rot = displayRotation()
                if (rot == lastRotation) return
                lastRotation = rot

                // keep frames correct immediately, only if camera is active
                preview?.let { it.targetRotation = rot }
                imageCapture?.let { it.targetRotation = rot }
                imageAnalysis?.let { it.targetRotation = rot }
            }
        }


    fun startPreview(owner: LifecycleOwner) {
        currentOwner = owner
        viewModelScope.launch {
            cameraOpsMutex.withLock {
                try {
                    // Use suspend provider acquisition to avoid blocking the main thread.
                    val provider = cameraProvider ?: ProcessCameraProvider.awaitInstance(getApplication()).also {
                        cameraProvider = it
                    }

                    provider.unbindAll()
                    val previous = _state.value.surfaceRequest
                    previous?.willNotProvideSurface()
                    _state.value = _state.value.copy(surfaceRequest = null)

                    val selector = ResolutionSelector.Builder()
                        .setAspectRatioStrategy(
                            AspectRatioStrategy(
                                AspectRatio.RATIO_4_3,
                                AspectRatioStrategy.FALLBACK_RULE_AUTO
                            )
                        )
                        .build()

                    val p = Preview.Builder()
                        .setResolutionSelector(selector)
                        .build()
                        .also { preview = it }

                    val ic = ImageCapture.Builder()
                        .setResolutionSelector(selector)
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()
                        .also { imageCapture = it }

                    val ia = ImageAnalysis.Builder()
                        .setResolutionSelector(selector)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                        .setOutputImageRotationEnabled(false)
                        .build()
                        .also { imageAnalysis = it }

                    setEfficientDetLite2Analyzer(ia)

                    val rotation = displayRotation()
                    lastRotation = rotation
                    p.targetRotation = rotation
                    ic.targetRotation = rotation
                    ia.targetRotation = rotation

                    val vp = ViewPort.Builder(viewportRational(), rotation)
                        .setScaleType(ViewPort.FIT)
                        .build()

                    val group = UseCaseGroup.Builder()
                        .setViewPort(vp)
                        .addUseCase(p)
                        .addUseCase(ic)
                        .addUseCase(ia)
                        .build()

                    // Install provider before bind so the first frame can be requested immediately.
                    p.setSurfaceProvider { request ->
                        _state.value = _state.value.copy(surfaceRequest = request)
                    }

                    provider.bindToLifecycle(owner, cameraSelector(), group)
                    orientationListener.enable()
                } catch (t: Throwable) {
                    _state.value = _state.value.copy(message = "startPreview failed: ${t.message}")
                    _rawDetections.value = emptyList()
                }
            }
        }
    }


    // Stop the camera preview and clear the SurfaceRequest shown by the UI
    fun stopPreview(){
        viewModelScope.launch {
            cameraOpsMutex.withLock {
                val provider = cameraProvider ?: ProcessCameraProvider.awaitInstance(getApplication()).also {
                    cameraProvider = it
                }
                provider.unbindAll()
                orientationListener.disable()
                val previous = _state.value.surfaceRequest
                previous?.willNotProvideSurface()
                _state.value = _state.value.copy(surfaceRequest = null)
            }
        }
    }

    // Add intent handler to hellp UI talk to VM, handle events/process intents by updating state here
    fun handle(intent: CameraIntent) {
        when (intent) {

            //When the user taps flip button, toggle the lensFacing state
            CameraIntent.ToggleLens -> {
                val current = _state.value.lensFacing
                val next =
                    if (current == CameraSelector.LENS_FACING_BACK)
                        CameraSelector.LENS_FACING_FRONT
                    else
                        CameraSelector.LENS_FACING_BACK

                // Update the lensFacing in immutable state to trigger recomposition
                _state.value = _state.value.copy(lensFacing = next)
                _rawDetections.value = emptyList() // Clear detections on lens switch
            }

            // When the user taps the capture button, take and save a photo
            CameraIntent.CapturePhoto -> takePhoto()

            CameraIntent.ClearMessage -> {
                _state.value = _state.value.copy(message = null)
            }
            // Permissions result came back from teh Activity Result API
            is CameraIntent.OnPermissionsResult -> {
                _state.value = _state.value.copy(permissionsGranted = intent.granted)
                if (!intent.granted) {
                    stopPreview() // Ensure resources are released if permissions are revoked
                    _rawDetections.value = emptyList()
                }
            }

    // ...existing code...

        }


    }
    /**
     * Download a server photo by name, run detection w/ detectObjectsByName,
     * and return the top detected labels. Public for GalleryScreen.
     */

    suspend fun analyzeServerPhoto(name: String): String {
        val t = ensureToken()
        val dets = api.detectObjectsByName(t, name)
        return if (dets.isEmpty()) {
            "No objects detected"
        } else {
            dets.take(10).joinToString(separator = "\n") { "${it.label} ${(it.score * 100).toInt()}%" }
        }
    }

    suspend fun uploadSavedPhoto(savedUri: Uri): String {
        val token = ensureToken()
        val app = getApplication<Application>()
        val cr = app.contentResolver

        // Infer MIME & content type
        val mime = cr.getType(savedUri) ?: "image/jpeg"
        val contentType = when {
            mime.contains("png", true) -> ContentType.Image.PNG
            mime.contains("webp", true) -> ContentType.parse("image/webp")
            mime.contains("heic", true) || mime.contains("heif", true) -> ContentType.parse("image/heic")
            else -> ContentType.Image.JPEG
        }
        val ext = when {
            contentType == ContentType.Image.PNG -> "png"
            contentType.toString().contains("webp", true) -> "webp"
            contentType.toString().contains("heic", true) -> "heic"
            else -> "jpg"
        }
        val remoteName = "photo_${System.currentTimeMillis()}.$ext"

        // Read the image bytes
        val bytes = cr.openInputStream(savedUri)!!.use { it.readBytes() }

        // Upload via  ApiClient
        val reply = api.uploadImageBytes(
            token = token,
            bytes = bytes,
            remoteName = remoteName,
            contentType = contentType
        )
        val uploaded = reply.trim().trim('"').ifBlank {remoteName}
        return uploaded
    }

     // Server-side object detection on a saved photo. Reads bytes from the given Uri,
     // sends to Ktor /api/ai/detect-bytes, and returns a label summary.

    private suspend fun detectObjectsOnSavedPhoto(savedUri: Uri): String {
        val t = ensureToken()
        val app = getApplication<Application>()
        val cr = app.contentResolver

        val mime = cr.getType(savedUri) ?: "image/jpeg"
        val ct = when {
            mime.contains("png", true) -> ContentType.Image.PNG
            mime.contains("webp", true) -> ContentType.parse("image/webp")
            mime.contains("heic", true) || mime.contains("heif", true) -> ContentType.parse("image/heic")
            else -> ContentType.Image.JPEG
        }
        val ext = when {
            ct == ContentType.Image.PNG -> "png"
            ct.toString().contains("webp", true) -> "webp"
            ct.toString().contains("heic", true) -> "heic"
            else -> "jpg"
        }
        val fname = "photo_${System.currentTimeMillis()}.$ext"

        val data = withContext(Dispatchers.IO) { cr.openInputStream(savedUri)!!.use { it.readBytes() } }
        val dets = api.detectObjectsPhotoBytes(
            token = t,
            imageBytes = data,
            filename = fname,
            contentType = ct
        )
        return if (dets.isEmpty()) "No objects detected"
        else dets.take(10).joinToString { "${it.label} ${(it.score * 100).toInt()}%" }
    }

    // Take a photo with ImageCapture and save it to ARPreview by MediaStoreSaver
    private fun takePhoto(){
        val imageCapture = imageCapture ?: return //no image capture configured
        viewModelScope.launch {
            val savedUri = try {
                MediaStoreSaver.savePhoto(getApplication(), imageCapture)
            } catch (e: Exception) {
                _state.value = _state.value.copy(message = "Save failed: ${e.message}")
                return@launch
            }

            if (savedUri == null) {
                _state.value = _state.value.copy(message = "Save failed: URI")
                return@launch
            }

            val uploadedName = try {
                withContext(Dispatchers.IO) { uploadSavedPhoto(savedUri) }
            } catch (e: HttpStatusException) {
                if (e.status.value == 401) {
                    logout()
                    _state.value = _state.value.copy(
                        message = "Saved locally. Please sign in again to upload."
                    )
                } else {
                    _state.value = _state.value.copy(
                        message = "Saved locally, upload failed: ${e.message}"
                    )
                }
                return@launch
            } catch (e: Exception) {
                _state.value = _state.value.copy(message = "Saved locally, upload failed: ${e.message}")
                return@launch
            }

            val url = api.photoUrl(uploadedName)

            val labels = runCatching {
                withContext(Dispatchers.IO) { detectObjectsOnSavedPhoto(savedUri) }
            }.getOrElse { e ->
                "Detection failed: ${e.message}"
            }

            _state.value = _state.value.copy(
                message = "Uploaded $uploadedName\n$url\nDetected: $labels"
            )
        }
    }




    private fun setEfficientDetLite2Analyzer(analysis: ImageAnalysis) {
        analysis.setAnalyzer(analysisExecutor) { imageProxy ->
            try {

                val now = System.currentTimeMillis()
                if (now - lastRun < 80) { // ~12 FPS cap
                    return@setAnalyzer
                }
                lastRun = now

                // Run EfficientDet Lite2 on the current frame
                val bmp = imageProxy.toBitmap()
                val tensorImage = org.tensorflow.lite.support.image.TensorImage.fromBitmap(bmp)
                val detections = efficientDetLite2.detect(tensorImage) // sync call

                val bufferToSensor = android.graphics.Matrix().apply {
                    imageProxy.imageInfo.sensorToBufferTransformMatrix.invert(this)
                }

                val raw = buildList {
                    for (det in detections) {
                        val (label, score) = preferredAllowedCategory(det.categories) ?: continue

                        Log.d("DETECT", "label=$label score=$score")
                        val minScore = when (label) {
                            "person" -> 0.28f
                            "chair" -> 0.26f
                            "bottle" -> 0.22f
                            "cell phone" -> 0.18f
                            "laptop" -> 0.18f
                            else -> 0.25f
                        }
                        if (score < minScore) continue
                        val r = det.boundingBox
                        val sensorRect = RectF(r.left, r.top, r.right, r.bottom).also {
                            bufferToSensor.mapRect(it)
                        }
                        add(
                            RawDetection(
                                rectPx = androidx.compose.ui.geometry.Rect(
                                    sensorRect.left,
                                    sensorRect.top,
                                    sensorRect.right,
                                    sensorRect.bottom
                                ),
                                label = label,
                                score = score
                            )
                        )
                    }
                }
                _rawDetections.value = raw

                // Keep your overlay in sync: analyzerBuffer -> sensor
                _analyzerToSensor.value = bufferToSensor
            } catch (t: Throwable) {
                _state.value = _state.value.copy(message = "EfficientDet Lite2 error: ${t.message}")
            } finally {
                imageProxy.close()
            }
        }
    }



    /* Build a cameraSelector for matching our current choice in state */
    private fun cameraSelector(): CameraSelector =
        CameraSelector.Builder()
            .requireLensFacing(_state.value.lensFacing)
            .build()



    private fun displayRotation(): Int {
        val app = getApplication<Application>()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val dm = app.getSystemService(DisplayManager::class.java)
            val d = dm?.displays?.firstOrNull()
            d?.rotation ?: Surface.ROTATION_0
        } else {
            @Suppress("DEPRECATION")
            (app.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                .defaultDisplay.rotation
        }
    }

    private fun viewportRational(): Rational {
        val dm = getApplication<Application>().resources.displayMetrics
        val width = dm.widthPixels.coerceAtLeast(1)
        val height = dm.heightPixels.coerceAtLeast(1)
        return Rational(width, height)
    }

}

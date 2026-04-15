package com.example.augmentedreality.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.augmentedreality.net.ApiClient
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    modifier: Modifier = Modifier,
    vm: CameraViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authState by vm.authState.collectAsState()
    var files by remember { mutableStateOf<List<String>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var showLogin by remember { mutableStateOf(false) }
    var selectedPhoto by remember { mutableStateOf<String?>(null) }
    var selectedAnalysis by remember { mutableStateOf<String?>(null) }
    var selectedDetections by remember { mutableStateOf<List<ApiClient.ServerDetection>>(emptyList()) }
    var showSpanish by remember { mutableStateOf(false) }
    var detailBusy by remember { mutableStateOf(false) }

    LaunchedEffect(authState) {
        when (authState) {
            is CameraViewModel.AuthState.SignedIn -> {
                runCatching { vm.listPhotos() }
                    .onSuccess {
                        files = it
                        error = null
                        showLogin = false
                    }
                    .onFailure { e ->
                        files = emptyList()
                        error = e.message ?: "Failed to load photos"
                        showLogin = true
                    }
            }
            CameraViewModel.AuthState.SignedOut -> {
                files = emptyList()
                showLogin = true
            }
        }
    }

    if (showLogin) {
        LoginDialog(
            onDismiss = { showLogin = false },
            onLogin = { user, pass ->
                scope.launch {
                    error = null
                    runCatching { vm.login(user, pass) }
                        .onSuccess { showLogin = false }
                        .onFailure { e -> error = "Login failed: ${e.message}" }
                }
            },
            onSignUp = { user, pass ->
                scope.launch {
                    error = null
                    runCatching { vm.signUp(user, pass) }
                        .onSuccess { showLogin = false }
                        .onFailure { e -> error = "Sign up failed: ${e.message}" }
                }
            },
            errorMessage = error
        )
    }

    Column(modifier = modifier) {
        val username = (authState as? CameraViewModel.AuthState.SignedIn)?.username
        Row(
            Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = if (username != null) "Hi, $username !" else "Welcome")
            TextButton(onClick = { showLogin = true }) { Text(if (username == null) "Login / Sign Up" else "Switch User") }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = {
                vm.logout()
                files = emptyList()
                showLogin = true
            }) { Text("Logout") }
        }

        if (error != null) {
            Text("Error: $error", modifier = Modifier.padding(16.dp))
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(120.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp)
            ) {
                items(files) { name ->
                    Column(Modifier.padding(4.dp)) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(vm.photoUrl(name))
                                .addHeader("Authorization", "Bearer ${vm.currentTokenOrNull()}")
                                .build(),
                            contentDescription = name,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(4.dp))
                        TextButton(onClick = {
                            selectedPhoto = name
                            selectedAnalysis = "Analyzing..."
                            selectedDetections = emptyList()
                            detailBusy = true
                            scope.launch {
                                runCatching { vm.analyzeServerPhotoDetections(name) }
                                    .onSuccess { result ->
                                        selectedDetections = result
                                        selectedAnalysis = if (result.isEmpty()) "No objects detected" else null
                                    }
                                    .onFailure { e -> selectedAnalysis = "Analyze failed: ${e.message}" }
                                detailBusy = false
                            }
                        }) { Text("Analyze") }
                    }
                }
            }
        }
    }

    val detailName = selectedPhoto
    if (detailName != null) {
        PhotoDetailDialog(
            name = detailName,
            imageModel = ImageRequest.Builder(context)
                .data(vm.photoUrl(detailName))
                .addHeader("Authorization", "Bearer ${vm.currentTokenOrNull()}")
                .build(),
            analysis = selectedAnalysis,
            detections = selectedDetections,
            showSpanish = showSpanish,
            busy = detailBusy,
            onToggleLanguage = { showSpanish = !showSpanish },
            onDelete = {
                selectedAnalysis = "Deleting..."
                detailBusy = true
                scope.launch {
                    runCatching { vm.deletePhoto(detailName) }
                        .onSuccess {
                            files = vm.listPhotos()
                            selectedPhoto = null
                            selectedAnalysis = null
                            selectedDetections = emptyList()
                        }
                        .onFailure { e ->
                            selectedAnalysis = "Delete failed: ${e.message}"
                        }
                    detailBusy = false
                }
            },
            onDismiss = {
                if (!detailBusy) {
                    selectedPhoto = null
                    selectedAnalysis = null
                    selectedDetections = emptyList()
                }
            }
        )
    }
}

@Composable
private fun PhotoDetailDialog(
    name: String,
    imageModel: ImageRequest,
    analysis: String?,
    detections: List<ApiClient.ServerDetection>,
    showSpanish: Boolean,
    busy: Boolean,
    onToggleLanguage: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(name) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                DetectionImage(
                    imageModel = imageModel,
                    name = name,
                    detections = detections.take(4),
                    showSpanish = showSpanish,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(if (showSpanish) "Español" else "English")
                    TextButton(onClick = onToggleLanguage) {
                        Text(if (showSpanish) "Show English" else "Mostrar español")
                    }
                }
                Text(analysis ?: detectionSummary(detections, showSpanish))
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = onDismiss
            ) { Text("Close") }
        },
        dismissButton = {
            TextButton(
                enabled = !busy,
                onClick = onDelete
            ) { Text("Delete") }
        }
    )
}

@Composable
private fun DetectionImage(
    imageModel: ImageRequest,
    name: String,
    detections: List<ApiClient.ServerDetection>,
    showSpanish: Boolean,
    modifier: Modifier = Modifier
) {
    var imageWidth by remember(name) { mutableStateOf(0) }
    var imageHeight by remember(name) { mutableStateOf(0) }

    Box(modifier = modifier) {
        AsyncImage(
            model = imageModel,
            contentDescription = name,
            contentScale = ContentScale.Fit,
            onSuccess = { state ->
                imageWidth = state.result.drawable.intrinsicWidth.coerceAtLeast(0)
                imageHeight = state.result.drawable.intrinsicHeight.coerceAtLeast(0)
            },
            modifier = Modifier.fillMaxSize()
        )

        Canvas(Modifier.fillMaxSize()) {
            val sourceWidth = detections.firstOrNull { it.imageWidth > 0 }?.imageWidth ?: imageWidth
            val sourceHeight = detections.firstOrNull { it.imageHeight > 0 }?.imageHeight ?: imageHeight
            if (sourceWidth <= 0 || sourceHeight <= 0) return@Canvas

            val scale = min(size.width / sourceWidth.toFloat(), size.height / sourceHeight.toFloat())
            val drawnWidth = sourceWidth * scale
            val drawnHeight = sourceHeight * scale
            val offsetX = (size.width - drawnWidth) / 2f
            val offsetY = (size.height - drawnHeight) / 2f

            detections.forEach { det ->
                val left = offsetX + det.left * scale
                val top = offsetY + det.top * scale
                val right = offsetX + det.right * scale
                val bottom = offsetY + det.bottom * scale
                val boxLeft = left.coerceIn(0f, size.width)
                val boxTop = top.coerceIn(0f, size.height)
                val boxRight = right.coerceIn(0f, size.width)
                val boxBottom = bottom.coerceIn(0f, size.height)
                val label = detectionLine(det, showSpanish)

                drawRect(
                    color = Color.Yellow,
                    topLeft = Offset(boxLeft, boxTop),
                    size = Size(
                        width = (boxRight - boxLeft).coerceAtLeast(1f),
                        height = (boxBottom - boxTop).coerceAtLeast(1f)
                    ),
                    style = Stroke(width = 3.dp.toPx())
                )

                drawIntoCanvas { canvas ->
                    val paint = android.graphics.Paint().apply {
                        color = android.graphics.Color.YELLOW
                        textSize = 13.dp.toPx()
                        isAntiAlias = true
                    }
                    canvas.nativeCanvas.drawText(
                        label,
                        boxLeft,
                        (boxTop - 5.dp.toPx()).coerceAtLeast(14.dp.toPx()),
                        paint
                    )
                }
            }
        }
    }
}

private fun detectionSummary(
    detections: List<ApiClient.ServerDetection>,
    showSpanish: Boolean
): String =
    if (detections.isEmpty()) {
        "No objects detected"
    } else {
        detections.take(10).joinToString(separator = "\n") { detectionLine(it, showSpanish) }
    }

private fun detectionLine(
    detection: ApiClient.ServerDetection,
    showSpanish: Boolean
): String {
    val confidence = (detection.score * 100).toInt()
    if (!showSpanish) return "${detection.label} $confidence%"

    val spanish = spanishLabelFor(detection.label)
    return if (spanish == detection.label) {
        "${detection.label} $confidence%"
    } else {
        "${detection.label} = $spanish $confidence%"
    }
}

private fun spanishLabelFor(label: String): String =
    spanishLabels[label.lowercase()] ?: label

private val spanishLabels = mapOf(
    "person" to "persona",
    "bicycle" to "bicicleta",
    "car" to "auto",
    "motorcycle" to "motocicleta",
    "airplane" to "avion",
    "bus" to "autobus",
    "train" to "tren",
    "truck" to "camion",
    "boat" to "barco",
    "traffic light" to "semaforo",
    "fire hydrant" to "hidrante",
    "stop sign" to "senal de alto",
    "parking meter" to "parquimetro",
    "bench" to "banca",
    "bird" to "pajaro",
    "cat" to "gato",
    "dog" to "perro",
    "horse" to "caballo",
    "sheep" to "oveja",
    "cow" to "vaca",
    "elephant" to "elefante",
    "bear" to "oso",
    "zebra" to "cebra",
    "giraffe" to "jirafa",
    "backpack" to "mochila",
    "umbrella" to "paraguas",
    "handbag" to "bolso",
    "tie" to "corbata",
    "suitcase" to "maleta",
    "frisbee" to "frisbee",
    "skis" to "esquis",
    "snowboard" to "tabla de nieve",
    "sports ball" to "pelota",
    "kite" to "cometa",
    "baseball bat" to "bate de beisbol",
    "baseball glove" to "guante de beisbol",
    "skateboard" to "patineta",
    "surfboard" to "tabla de surf",
    "tennis racket" to "raqueta de tenis",
    "bottle" to "botella",
    "wine glass" to "copa",
    "cup" to "taza",
    "fork" to "tenedor",
    "knife" to "cuchillo",
    "spoon" to "cuchara",
    "bowl" to "tazon",
    "banana" to "platano",
    "apple" to "manzana",
    "sandwich" to "sandwich",
    "orange" to "naranja",
    "broccoli" to "brocoli",
    "carrot" to "zanahoria",
    "hot dog" to "hot dog",
    "pizza" to "pizza",
    "donut" to "dona",
    "cake" to "pastel",
    "chair" to "silla",
    "couch" to "sofa",
    "potted plant" to "planta en maceta",
    "bed" to "cama",
    "dining table" to "mesa",
    "toilet" to "inodoro",
    "tv" to "televisor",
    "laptop" to "computadora portatil",
    "mouse" to "raton",
    "remote" to "control remoto",
    "keyboard" to "teclado",
    "cell phone" to "celular",
    "microwave" to "microondas",
    "oven" to "horno",
    "toaster" to "tostadora",
    "sink" to "lavabo",
    "refrigerator" to "refrigerador",
    "book" to "libro",
    "clock" to "reloj",
    "vase" to "florero",
    "scissors" to "tijeras",
    "teddy bear" to "oso de peluche",
    "hair drier" to "secador de pelo",
    "toothbrush" to "cepillo de dientes"
)

@Composable
private fun LoginDialog(
    onDismiss: () -> Unit,
    onLogin: (String, String) -> Unit,
    onSignUp: (String, String) -> Unit,
    errorMessage: String?
){
    var user by remember {mutableStateOf("")}
    var pass by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sign in") },
        text = {
            Column {
                OutlinedTextField(
                    value = user,
                    onValueChange = { user = it },
                    label = { Text("Username") }
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it },
                    label = { Text("Password") }
                )

                if (errorMessage != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = errorMessage,
                        modifier = Modifier.padding(4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onLogin(user.trim(), pass)})
            {Text("Login")}
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onSignUp(user.trim(), pass) }) { Text("Sign Up") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

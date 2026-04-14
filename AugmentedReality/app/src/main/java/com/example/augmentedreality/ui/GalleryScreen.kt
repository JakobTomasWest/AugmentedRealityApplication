package com.example.augmentedreality.ui

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch

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
                            detailBusy = true
                            scope.launch {
                                runCatching { vm.analyzeServerPhoto(name) }
                                    .onSuccess { result -> selectedAnalysis = result }
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
            busy = detailBusy,
            onAnalyze = {
                selectedAnalysis = "Analyzing..."
                detailBusy = true
                scope.launch {
                    runCatching { vm.analyzeServerPhoto(detailName) }
                        .onSuccess { result -> selectedAnalysis = result }
                        .onFailure { e -> selectedAnalysis = "Analyze failed: ${e.message}" }
                    detailBusy = false
                }
            },
            onDelete = {
                selectedAnalysis = "Deleting..."
                detailBusy = true
                scope.launch {
                    runCatching { vm.deletePhoto(detailName) }
                        .onSuccess {
                            files = vm.listPhotos()
                            selectedPhoto = null
                            selectedAnalysis = null
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
    busy: Boolean,
    onAnalyze: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(name) },
        text = {
            Column {
                AsyncImage(
                    model = imageModel,
                    contentDescription = name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(analysis ?: "Tap Analyze to detect objects in this photo.")
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = onAnalyze
            ) { Text("Analyze") }
        },
        dismissButton = {
            Row {
                TextButton(
                    enabled = !busy,
                    onClick = onDelete
                ) { Text("Delete") }
                TextButton(
                    enabled = !busy,
                    onClick = onDismiss
                ) { Text("Close") }
            }
        }
    )
}

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

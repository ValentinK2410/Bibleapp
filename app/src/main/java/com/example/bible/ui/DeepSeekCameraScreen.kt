package com.example.bible.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bible.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeepSeekCameraScreen(
    viewModel: BibleViewModel,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val vision by viewModel.deepSeekVision.collectAsStateWithLifecycle()
    val hasKey by viewModel.deepSeekApiKey.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var useFront by remember { mutableStateOf(false) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var capturedJpeg by remember { mutableStateOf<ByteArray?>(null) }
    var capturing by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.clearDeepSeekVision() }
    }

    val previewBitmap = remember(capturedJpeg) {
        capturedJpeg?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.deepseek_camera_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    if (hasPermission && capturedJpeg == null) {
                        IconButton(onClick = { useFront = !useFront }) {
                            Icon(
                                Icons.Filled.Cameraswitch,
                                contentDescription = stringResource(R.string.deepseek_camera_flip),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            when {
                hasKey.isBlank() || vision.needsKey -> {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.deepseek_needs_key))
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onOpenSettings) {
                            Text(stringResource(R.string.deepseek_open_settings))
                        }
                    }
                }
                !hasPermission -> {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.deepseek_camera_permission))
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                            Text(stringResource(R.string.deepseek_camera_grant))
                        }
                    }
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        if (previewBitmap != null) {
                            Image(
                                bitmap = previewBitmap,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                            )
                        } else {
                            DeepSeekLivePreview(
                                useFront = useFront,
                                onImageCaptureReady = { imageCapture = it },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                    ) {
                        if (capturedJpeg == null) {
                            Text(
                                stringResource(R.string.deepseek_camera_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    val capture = imageCapture ?: return@Button
                                    capturing = true
                                    scope.launch {
                                        val jpeg = captureJpeg(
                                            context.cacheDir,
                                            capture,
                                            ContextCompat.getMainExecutor(context),
                                        )
                                        capturing = false
                                        if (jpeg == null || jpeg.isEmpty()) {
                                            android.widget.Toast.makeText(
                                                context,
                                                R.string.deepseek_camera_capture_failed,
                                                android.widget.Toast.LENGTH_SHORT,
                                            ).show()
                                        } else {
                                            capturedJpeg = jpeg
                                            viewModel.analyzeCameraJpeg(jpeg)
                                        }
                                    }
                                },
                                enabled = !capturing && imageCapture != null,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.deepseek_camera_capture))
                            }
                        } else {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        capturedJpeg = null
                                        viewModel.clearDeepSeekVision()
                                    },
                                ) {
                                    Text(stringResource(R.string.deepseek_camera_retake))
                                }
                                TextButton(
                                    onClick = {
                                        capturedJpeg?.let { viewModel.analyzeCameraJpeg(it) }
                                    },
                                    enabled = !vision.loading,
                                ) {
                                    Text(stringResource(R.string.deepseek_camera_decode))
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            when {
                                vision.loading -> {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.width(8.dp))
                                        Text(stringResource(R.string.deepseek_camera_decoding))
                                    }
                                }
                                vision.error != null -> Text(
                                    vision.error!!,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                vision.answer.isNotBlank() -> Text(
                                    vision.answer,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("RestrictedApi")
@Composable
private fun DeepSeekLivePreview(
    useFront: Boolean,
    onImageCaptureReady: (ImageCapture?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { ContextCompat.getMainExecutor(context) }
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    DisposableEffect(useFront, lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val listener = Runnable {
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            val selector = if (useFront) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
                onImageCaptureReady(imageCapture)
            } catch (_: Exception) {
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageCapture,
                    )
                    onImageCaptureReady(imageCapture)
                } catch (_: Exception) {
                    onImageCaptureReady(null)
                }
            }
        }
        cameraProviderFuture.addListener(listener, executor)
        onDispose {
            try {
                cameraProviderFuture.get().unbindAll()
            } catch (_: Exception) {
            }
            onImageCaptureReady(null)
        }
    }
    AndroidView(factory = { previewView }, modifier = modifier)
}

private suspend fun captureJpeg(
    cacheDir: File,
    imageCapture: ImageCapture,
    executor: java.util.concurrent.Executor,
): ByteArray? = withContext(Dispatchers.IO) {
    val file = File(cacheDir, "deepseek_cam_${System.currentTimeMillis()}.jpg")
    val ok = suspendCancellableCoroutine { cont ->
        val opts = ImageCapture.OutputFileOptions.Builder(file).build()
        imageCapture.takePicture(
            opts,
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    if (cont.isActive) cont.resume(true)
                }

                override fun onError(exc: ImageCaptureException) {
                    if (file.exists()) file.delete()
                    if (cont.isActive) cont.resume(false)
                }
            },
        )
    }
    val bytes = if (ok && file.exists()) file.readBytes() else null
    if (file.exists()) file.delete()
    bytes
}

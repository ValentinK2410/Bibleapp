package com.example.bible.ui.travel

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Мини-превью камеры и готовый [ImageCapture] для серийных снимков по карте. */
@SuppressLint("RestrictedApi")
@Composable
fun TravelBurstCameraPreview(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onImageCaptureReady: (ImageCapture?) -> Unit,
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
    DisposableEffect(enabled, lifecycleOwner) {
        if (!enabled) {
            onImageCaptureReady(null)
            return@DisposableEffect onDispose { }
        }
        val hasCam =
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        if (!hasCam) {
            onImageCaptureReady(null)
            return@DisposableEffect onDispose { }
        }
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val listener = Runnable {
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
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
        cameraProviderFuture.addListener(listener, executor)
        onDispose {
            try {
                cameraProviderFuture.get().unbindAll()
            } catch (_: Exception) {
            }
            onImageCaptureReady(null)
        }
    }

    if (enabled) {
        Box(modifier = modifier) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

suspend fun ImageCapture.captureToFileSuspend(
    outputFile: java.io.File,
    executor: java.util.concurrent.Executor,
): Boolean = suspendCancellableCoroutine { cont ->
    val opts = ImageCapture.OutputFileOptions.Builder(outputFile).build()
    takePicture(
        opts,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                if (cont.isActive) cont.resume(true)
            }

            override fun onError(exc: ImageCaptureException) {
                if (outputFile.exists()) outputFile.delete()
                if (cont.isActive) cont.resume(false)
            }
        },
    )
}

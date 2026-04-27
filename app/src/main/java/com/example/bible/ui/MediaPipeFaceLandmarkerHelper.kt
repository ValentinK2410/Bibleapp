package com.example.bible.ui

import android.content.Context
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.MediaImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult

/**
 * Обёртка над MediaPipe Face Landmarker для кадров CameraX.
 * Режим [RunningMode.IMAGE] + [FaceLandmarker.detect]: для живой камеры каждый кадр независимый,
 * так стабильнее, чем VIDEO/detectForVideo с индексом кадра.
 * Модель: assets [MODEL_ASSET].
 */
class MediaPipeFaceLandmarkerHelper private constructor(
    private val landmarker: FaceLandmarker,
) : AutoCloseable {

    fun detectFaceLandmarks(imageProxy: ImageProxy): FaceLandmarkerResult? {
        val mediaImage = imageProxy.image ?: return null
        val rotation = imageProxy.imageInfo.rotationDegrees
        val mpImage: MPImage = MediaImageBuilder(mediaImage).build()
        val opts = ImageProcessingOptions.builder().setRotationDegrees(rotation).build()
        return try {
            landmarker.detect(mpImage, opts)
        } catch (t: Throwable) {
            Log.w(TAG, "detect failed", t)
            null
        } finally {
            runCatching { mpImage.close() }
        }
    }

    override fun close() {
        runCatching { landmarker.close() }
    }

    companion object {
        private const val TAG = "MediaPipeFace"
        const val MODEL_ASSET = "face_landmarker.task"

        /**
         * Возвращает helper или null, если файла модели нет в assets или инициализация не удалась
         * (тогда приложение не падает — остаётся только ML Kit).
         */
        fun createOrNull(context: Context): MediaPipeFaceLandmarkerHelper? {
            val app = context.applicationContext
            if (!modelAssetPresent(app)) {
                Log.w(TAG, "Asset $MODEL_ASSET not found — add to app/src/main/assets (see class KDoc)")
                return null
            }
            val landmarker = runCatching {
                FaceLandmarker.createFromOptions(
                    app,
                    FaceLandmarker.FaceLandmarkerOptions.builder()
                        .setBaseOptions(
                            BaseOptions.builder()
                                .setModelAssetPath(MODEL_ASSET)
                                .build(),
                        )
                        .setRunningMode(RunningMode.IMAGE)
                        .setNumFaces(1)
                        .setOutputFaceBlendshapes(false)
                        .setOutputFacialTransformationMatrixes(false)
                        .build(),
                )
            }.getOrElse { t ->
                Log.e(TAG, "FaceLandmarker.createFromOptions failed", t)
                return null
            }
            return MediaPipeFaceLandmarkerHelper(landmarker)
        }

        private fun modelAssetPresent(context: Context): Boolean =
            runCatching {
                context.assets.open(MODEL_ASSET).use { stream ->
                    if (stream.read() == -1) return@runCatching false
                }
                true
            }.getOrDefault(false)

        /** Публичная проверка: без файла в assets режим MediaPipe тихо совпадает с ML Kit. */
        fun isModelAssetPresent(context: Context): Boolean =
            modelAssetPresent(context.applicationContext)
    }
}

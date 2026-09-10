package com.example.bible.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.bible.R
import java.util.Locale

class AiSpeechToText(
    val available: Boolean,
    val listening: Boolean,
    val start: () -> Unit,
    val stop: () -> Unit,
)

@Composable
fun rememberAiSpeechToText(
    onPartial: (String) -> Unit,
    onFinal: (String) -> Unit,
): AiSpeechToText {
    val context = LocalContext.current
    val available = remember(context) {
        SpeechRecognizer.isRecognitionAvailable(context)
    }
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var listening by remember { mutableStateOf(false) }
    val onPartialState = rememberUpdatedState(onPartial)
    val onFinalState = rememberUpdatedState(onFinal)

    DisposableEffect(available, context) {
        if (!available) {
            onDispose { }
        } else {
            val sr = SpeechRecognizer.createSpeechRecognizer(context)
            sr.setRecognitionListener(
                object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        listening = true
                    }

                    override fun onBeginningOfSpeech() {}

                    override fun onRmsChanged(rmsdB: Float) {}

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {}

                    override fun onError(error: Int) {
                        listening = false
                        val msg = when (error) {
                            SpeechRecognizer.ERROR_AUDIO ->
                                context.getString(R.string.ai_ask_voice_error_audio)
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                                context.getString(R.string.ai_ask_voice_error_permission)
                            SpeechRecognizer.ERROR_NETWORK,
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                            ->
                                context.getString(R.string.ai_ask_voice_error_network)
                            SpeechRecognizer.ERROR_NO_MATCH,
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                            ->
                                context.getString(R.string.ai_ask_voice_error_no_match)
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                                context.getString(R.string.ai_ask_voice_error_busy)
                            SpeechRecognizer.ERROR_CLIENT -> return
                            else -> context.getString(R.string.ai_ask_voice_error)
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }

                    override fun onResults(results: Bundle?) {
                        listening = false
                        val text = results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            ?.trim()
                            .orEmpty()
                        if (text.isNotEmpty()) {
                            onFinalState.value(text)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val text = partialResults
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            ?.trim()
                            .orEmpty()
                        if (text.isNotEmpty()) {
                            onPartialState.value(text)
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                },
            )
            recognizer = sr
            onDispose {
                listening = false
                runCatching { sr.cancel() }
                runCatching { sr.destroy() }
                recognizer = null
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            beginListening(recognizer) { listening = it }
        } else {
            Toast.makeText(
                context,
                R.string.ai_ask_voice_need_mic,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    fun requestOrStart() {
        if (!available) {
            Toast.makeText(context, R.string.ai_ask_voice_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            beginListening(recognizer) { listening = it }
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    return AiSpeechToText(
        available = available,
        listening = listening,
        start = { requestOrStart() },
        stop = {
            listening = false
            runCatching { recognizer?.stopListening() }
            runCatching { recognizer?.cancel() }
        },
    )
}

private fun beginListening(
    recognizer: SpeechRecognizer?,
    setListening: (Boolean) -> Unit,
) {
    val sr = recognizer ?: return
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
        )
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }
    setListening(true)
    runCatching { sr.startListening(intent) }
        .onFailure { setListening(false) }
}

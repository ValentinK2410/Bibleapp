package com.example.bible.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.hardware.ConsumerIrManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.Location
import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.nfc.tech.NfcA
import android.nfc.tech.IsoDep
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.util.Log
import android.net.wifi.WifiManager
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.nio.charset.StandardCharsets
import java.util.Locale
import androidx.compose.ui.graphics.Color as ComposeColor
import com.example.bible.R
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlin.math.round
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sqrt
import kotlin.text.Charsets

/**
 * «Лаборатория» для экспериментов: что дают API Android для камеры, звука, датчиков, навигации, связи.
 * Не заменяет отдельные экраны с ML Kit/мимикой — здесь сырые датчики и системные менеджеры.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExperimentSensorLabScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val localView = LocalView.current
    val scope = rememberCoroutineScope()
    val pm = remember { context.packageManager }

    var vibrateLoopJob by remember { mutableStateOf<Job?>(null) }
    var vibLoopKind by remember { mutableStateOf(0) } // 0 = выкл, 1 = короткие, 2 = плотный гул
    val defaultVibrator = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                ?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            vibrateLoopJob?.cancel()
            defaultVibrator?.cancel()
            vibLoopKind = 0
        }
    }

    var fineLoc by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var activityRec by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACTIVITY_RECOGNITION,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var btScan by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_SCAN,
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            },
        )
    }
    var btConnect by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT,
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            },
        )
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { m ->
        fineLoc = m[Manifest.permission.ACCESS_FINE_LOCATION] == true
        activityRec = m[Manifest.permission.ACTIVITY_RECOGNITION] == true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            btScan = m[Manifest.permission.BLUETOOTH_SCAN] == true
            btConnect = m[Manifest.permission.BLUETOOTH_CONNECT] == true
        }
    }

    fun requestForSection(which: String) {
        val list = buildList {
            when (which) {
                "loc" -> if (!fineLoc) add(Manifest.permission.ACCESS_FINE_LOCATION)
                "steps" -> if (!activityRec) add(Manifest.permission.ACTIVITY_RECOGNITION)
                "bt" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (!btScan) add(Manifest.permission.BLUETOOTH_SCAN)
                    if (!btConnect) add(Manifest.permission.BLUETOOTH_CONNECT)
                }
            }
        }.toTypedArray()
        if (list.isNotEmpty()) permLauncher.launch(list)
    }

    // --- Датчики: движение, окружение, шаги
    var accel by remember { mutableStateOf(Triple(0f, 0f, 0f)) }
    var gyro by remember { mutableStateOf(Triple(0f, 0f, 0f)) }
    var gravity by remember { mutableStateOf(Triple(0f, 0f, 0f)) }
    var linearAcc by remember { mutableStateOf(Triple(0f, 0f, 0f)) }
    var magnet by remember { mutableStateOf(Triple(0f, 0f, 0f)) }
    var compassAzimuthDeg by remember { mutableStateOf(0f) }
    var light by remember { mutableStateOf(0f) }
    var pressureHpa by remember { mutableStateOf(0f) }
    var proxRaw by remember { mutableStateOf(0f) }
    var relHumidity by remember { mutableStateOf(0f) }
    var ambientTempC by remember { mutableStateOf(0f) }
    var stepsSinceBoot by remember { mutableStateOf<Float?>(null) }

    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val hasRotationVector = remember { sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null }
    val hasMagnet = remember { sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null }
    val hasBaro = remember { sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE) != null }
    val hasProx = remember { sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY) != null }
    val hasGravity = remember { sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY) != null }
    val hasLinearAcc = remember { sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) != null }
    val hasRelHum = remember { sensorManager.getDefaultSensor(Sensor.TYPE_RELATIVE_HUMIDITY) != null }
    val hasAmbientTemp = remember { sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE) != null }
    val hasAccel = remember { sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null }
    val hasGyro = remember { sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null }
    val hasLightSensor = remember { sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT) != null }
    val hasStepCounter = remember { sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null }

    var enAccel by rememberSaveable { mutableStateOf(true) }
    var enGyro by rememberSaveable { mutableStateOf(true) }
    var enGravity by rememberSaveable { mutableStateOf(true) }
    var enLinear by rememberSaveable { mutableStateOf(true) }
    var enLight by rememberSaveable { mutableStateOf(true) }
    var enCompass by rememberSaveable { mutableStateOf(true) }
    var enMag by rememberSaveable { mutableStateOf(true) }
    var enBaro by rememberSaveable { mutableStateOf(true) }
    var enProx by rememberSaveable { mutableStateOf(true) }
    var enHum by rememberSaveable { mutableStateOf(true) }
    var enTemp by rememberSaveable { mutableStateOf(true) }
    var enSteps by rememberSaveable { mutableStateOf(true) }

    DisposableEffect(
        activityRec,
        enAccel,
        enGyro,
        enGravity,
        enLinear,
        enLight,
        enCompass,
        enMag,
        enBaro,
        enProx,
        enHum,
        enTemp,
        enSteps,
    ) {
        val acc = object : SensorEventListener {
            private val rotMat = FloatArray(9)
            private val orient = FloatArray(3)

            override fun onSensorChanged(e: SensorEvent) {
                when (e.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER ->
                        accel = Triple(e.values[0], e.values[1], e.values[2])
                    Sensor.TYPE_GYROSCOPE ->
                        gyro = Triple(e.values[0], e.values[1], e.values[2])
                    Sensor.TYPE_GRAVITY ->
                        gravity = Triple(e.values[0], e.values[1], e.values[2])
                    Sensor.TYPE_LINEAR_ACCELERATION ->
                        linearAcc = Triple(e.values[0], e.values[1], e.values[2])
                    Sensor.TYPE_MAGNETIC_FIELD ->
                        magnet = Triple(e.values[0], e.values[1], e.values[2])
                    Sensor.TYPE_LIGHT -> light = e.values[0]
                    Sensor.TYPE_PROXIMITY -> proxRaw = e.values[0]
                    Sensor.TYPE_PRESSURE -> pressureHpa = e.values[0]
                    Sensor.TYPE_RELATIVE_HUMIDITY -> relHumidity = e.values[0]
                    Sensor.TYPE_AMBIENT_TEMPERATURE -> ambientTempC = e.values[0]
                    Sensor.TYPE_STEP_COUNTER -> stepsSinceBoot = e.values[0]
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(rotMat, e.values)
                        SensorManager.getOrientation(rotMat, orient)
                        var a = Math.toDegrees(orient[0].toDouble())
                        if (a < 0) a += 360.0
                        compassAzimuthDeg = a.toFloat()
                    }
                }
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
        }
        val ui = SensorManager.SENSOR_DELAY_UI
        fun register(type: Int) {
            sensorManager.getDefaultSensor(type)?.also { sensorManager.registerListener(acc, it, ui) }
        }
        if (enAccel) register(Sensor.TYPE_ACCELEROMETER)
        if (enGyro) register(Sensor.TYPE_GYROSCOPE)
        if (enLight) register(Sensor.TYPE_LIGHT)
        if (enCompass) register(Sensor.TYPE_ROTATION_VECTOR)
        if (enMag) register(Sensor.TYPE_MAGNETIC_FIELD)
        if (enBaro) register(Sensor.TYPE_PRESSURE)
        if (enProx) register(Sensor.TYPE_PROXIMITY)
        if (enGravity) register(Sensor.TYPE_GRAVITY)
        if (enLinear) register(Sensor.TYPE_LINEAR_ACCELERATION)
        if (enHum) register(Sensor.TYPE_RELATIVE_HUMIDITY)
        if (enTemp) register(Sensor.TYPE_AMBIENT_TEMPERATURE)
        if (enSteps && activityRec) {
            sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)?.also {
                sensorManager.registerListener(acc, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }
        onDispose {
            sensorManager.unregisterListener(acc)
        }
    }

    // --- Микрофон: амплитуда
    var micDb by remember { mutableFloatStateOf(0f) }
    var micEnabled by remember { mutableStateOf(false) }
    val micPerm = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }
    var micPermState by remember { mutableStateOf(micPerm) }
    val micPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { micPermState = it }

    LaunchedEffect(micEnabled, micPermState) {
        if (!micEnabled || !micPermState) return@LaunchedEffect
        val minBuf = AudioRecord.getMinBufferSize(
            44100,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuf < 0) return@LaunchedEffect
        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                44100,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf * 2,
            )
        } catch (e: SecurityException) {
            Log.w("SensorLab", "AudioRecord", e)
            return@LaunchedEffect
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return@LaunchedEffect
        }
        rec.startRecording()
        val buf = ShortArray(512)
        while (isActive && micEnabled) {
            val n = rec.read(buf, 0, buf.size)
            if (n > 0) {
                var sum = 0.0
                for (i in 0 until n) {
                    val s = buf[i].toInt()
                    sum += s * s
                }
                val rms = sqrt(sum / n).toFloat()
                val norm = (rms / 5000f).coerceIn(0f, 1f)
                micDb = norm
            }
            delay(50)
        }
        rec.stop()
        rec.release()
        micDb = 0f
    }

    // --- GPS
    var locText by remember { mutableStateOf("—") }
    LaunchedEffect(fineLoc) { /* noop — обновим по кнопке */ }

    // --- Фонарик
    var torchEnabled by remember { mutableStateOf(false) }
    var torchStrobe by rememberSaveable { mutableStateOf(false) }
    var torchBrightness by remember { mutableFloatStateOf(1f) }
    var strobeHzStr by rememberSaveable { mutableStateOf("2") }
    var strobeOnMsStr by rememberSaveable { mutableStateOf("100") }
    var strobeTotalStr by rememberSaveable { mutableStateOf("0") }
    val camPerm = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }
    var camPermState by remember { mutableStateOf(camPerm) }
    val camPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { camPermState = it }
    val cameraManager = remember { context.getSystemService(Context.CAMERA_SERVICE) as CameraManager }
    val torchCameraId = remember { mutableStateOf<String?>(null) }
    fun getTorchCameraId(): String? {
        torchCameraId.value?.let { return it }
        val id = runCatching {
            cameraManager.cameraIdList.firstOrNull { cid ->
                val ch = cameraManager.getCameraCharacteristics(cid)
                ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: cameraManager.cameraIdList.getOrNull(0)
        }.getOrNull()
        torchCameraId.value = id
        return id
    }
    fun setTorchModePhysical(on: Boolean) {
        val id = getTorchCameraId() ?: return
        runCatching { cameraManager.setTorchMode(id, on) }
    }

    // --- Маппинг: один сигнал (0—1) → тон, цвет, график, TTS, фонарик
    var mappingEnabled by rememberSaveable { mutableStateOf(false) }
    var mapInputOrdinal by rememberSaveable { mutableStateOf(0) }
    var sinkGraph by rememberSaveable { mutableStateOf(false) }
    var sinkColor by rememberSaveable { mutableStateOf(false) }
    var sinkTone by rememberSaveable { mutableStateOf(false) }
    var sinkTts by rememberSaveable { mutableStateOf(false) }
    var sinkTorch by rememberSaveable { mutableStateOf(false) }
    var mapMenuExpanded by remember { mutableStateOf(false) }
    val appCtx = context.applicationContext
    val mapGraphPoints = remember { mutableStateListOf<Float>() }
    val toneSynth = remember(appCtx) { SensorLabToneSynthesizer(appCtx) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(appCtx) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(appCtx) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val e = engine
                e?.setLanguage(Locale("ru", "RU"))
            }
        }
        tts = engine
        onDispose {
            tts = null
            runCatching {
                engine?.stop()
                engine?.shutdown()
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose { toneSynth.stop() }
    }
    val getNorm: () -> Float = {
        if (!mappingEnabled) {
            0f
        } else {
            runCatching {
                SensorLabInputMapping.toUnit01(
                    SensorLabInput.fromId(mapInputOrdinal),
                    micDb = micDb,
                    light = light,
                    accel = accel,
                    gyro = gyro,
                    linearAcc = linearAcc,
                    gravity = gravity,
                    magnet = magnet,
                    compassAzimuthDeg = compassAzimuthDeg,
                    pressureHpa = pressureHpa,
                    proxRaw = proxRaw,
                    relHumidity = relHumidity,
                    ambientTempC = ambientTempC,
                    stepsSinceBoot = stepsSinceBoot,
                )
            }.getOrElse { 0.5f }
        }
    }
    val latestGetNorm = rememberUpdatedState(getNorm)
    val latestMapOn = rememberUpdatedState(mappingEnabled)
    val latestSinkGraph = rememberUpdatedState(sinkGraph)
    val latestSinkTone = rememberUpdatedState(sinkTone)
    val latestSinkTts = rememberUpdatedState(sinkTts)
    val latestTts = rememberUpdatedState(tts)
    val mappedNormUi: Float = if (mappingEnabled) getNorm() else 0f
    val effectiveTorchBrightness: Float =
        if (mappingEnabled && sinkTorch && camPermState && torchEnabled && !torchStrobe) {
            (0.1f + 0.9f * mappedNormUi).coerceIn(0.1f, 1f)
        } else {
            torchBrightness
        }
    val latestEffTorch = rememberUpdatedState(effectiveTorchBrightness)
    val srcLabelRes = intArrayOf(
        R.string.experiment_sensor_map_src_mic,
        R.string.experiment_sensor_map_src_light,
        R.string.experiment_sensor_map_src_accel,
        R.string.experiment_sensor_map_src_gyro,
        R.string.experiment_sensor_map_src_linear,
        R.string.experiment_sensor_map_src_gravity,
        R.string.experiment_sensor_map_src_mag,
        R.string.experiment_sensor_map_src_compass,
        R.string.experiment_sensor_map_src_pressure,
        R.string.experiment_sensor_map_src_prox,
        R.string.experiment_sensor_map_src_humid,
        R.string.experiment_sensor_map_src_temp,
        R.string.experiment_sensor_map_src_steps,
    )
    val mapSourceCollectionActive: Boolean = when (mapInputOrdinal.coerceIn(0, srcLabelRes.lastIndex)) {
        0 -> micEnabled && micPermState
        1 -> enLight
        2 -> enAccel
        3 -> enGyro
        4 -> enLinear
        5 -> enGravity
        6 -> enMag
        7 -> enCompass
        8 -> enBaro
        9 -> enProx
        10 -> enHum
        11 -> enTemp
        12 -> enSteps && activityRec
        else -> true
    }
    LaunchedEffect(mappingEnabled) {
        if (!mappingEnabled) {
            mapGraphPoints.clear()
        }
    }
    LaunchedEffect(Unit) {
        var lastTtsN = -1f
        var lastTtsTime = 0L
        while (isActive) {
            val mapOn = latestMapOn.value
            // Нормировка: только при включённом сопоставлении. Иначе — середина (чтобы «Тон» слышали без сопоставления).
            val n = if (mapOn) {
                runCatching { (latestGetNorm.value)() }.getOrElse { 0.5f }
            } else {
                0.5f
            }
            if (mapOn && latestSinkGraph.value) {
                mapGraphPoints.add(n)
                while (mapGraphPoints.size > 200) {
                    mapGraphPoints.removeAt(0)
                }
            }
            if (latestSinkTone.value) {
                runCatching {
                    toneSynth.start()
                    toneSynth.frequencyHz = sensorLabToneFrequencyHz(n)
                }
            } else {
                runCatching { toneSynth.stop() }
            }
            if (mapOn && latestSinkTts.value) {
                val te = latestTts.value
                if (te != null) {
                    val now = System.currentTimeMillis()
                    if (kotlin.math.abs(n - lastTtsN) > 0.04f && now - lastTtsTime > 1_800L) {
                        runCatching {
                            te.speak(
                                String.format(Locale.ROOT, "%.2f", n),
                                TextToSpeech.QUEUE_FLUSH,
                                null,
                                "sensor_map",
                            )
                        }
                        lastTtsN = n
                        lastTtsTime = now
                    }
                }
            }
            delay(40L)
        }
    }
    LaunchedEffect(
        camPermState,
        torchEnabled,
        torchStrobe,
        strobeHzStr,
        strobeOnMsStr,
        strobeTotalStr,
    ) {
        if (!camPermState || !torchEnabled) {
            setTorchModePhysical(false)
            return@LaunchedEffect
        }
        val id = getTorchCameraId() ?: return@LaunchedEffect
        try {
            if (torchStrobe) {
                val hz = strobeHzStr.replace(',', '.').toDoubleOrNull()?.coerceIn(0.2, 25.0) ?: 2.0
                val period = (1000.0 / hz).toLong().coerceIn(2L, 60_000L)
                var onDuration = round(
                    (strobeOnMsStr.replace(',', '.').toDoubleOrNull() ?: (period * 0.5)),
                ).toLong()
                onDuration = onDuration.coerceIn(1L, (period - 1).coerceAtLeast(1L))
                val offDuration = (period - onDuration).coerceAtLeast(0L)
                val totalLimitMs = (strobeTotalStr.toLongOrNull() ?: 0L) * 1000L
                if (totalLimitMs > 0L) {
                    var acc = 0L
                    while (isActive && acc < totalLimitMs) {
                        runCatching { cameraManager.setTorchMode(id, true) }
                        delay(onDuration)
                        if (!isActive) break
                        runCatching { cameraManager.setTorchMode(id, false) }
                        if (offDuration > 0) delay(offDuration)
                        if (!isActive) break
                        acc += period
                    }
                    if (isActive) {
                        torchEnabled = false
                    }
                } else {
                    while (isActive) {
                        runCatching { cameraManager.setTorchMode(id, true) }
                        delay(onDuration)
                        if (!isActive) break
                        runCatching { cameraManager.setTorchMode(id, false) }
                        if (offDuration > 0) delay(offDuration)
                    }
                }
            } else {
                while (isActive) {
                    val tb = latestEffTorch.value
                    if (tb >= 0.95f) {
                        runCatching { cameraManager.setTorchMode(id, true) }
                        delay(40L)
                    } else {
                        val periodMs = 5L
                        val onMs = (periodMs * tb).toLong().coerceIn(1L, periodMs)
                        val offMs = (periodMs - onMs).coerceAtLeast(0L)
                        runCatching { cameraManager.setTorchMode(id, true) }
                        delay(onMs)
                        if (!isActive) break
                        runCatching { cameraManager.setTorchMode(id, false) }
                        if (offMs > 0) delay(offMs)
                    }
                }
            }
        } finally {
            runCatching { cameraManager.setTorchMode(id, false) }
        }
    }
    DisposableEffect(Unit) {
        onDispose { setTorchModePhysical(false) }
    }

    // --- Bluetooth / WiFi / NFC
    val hasIr = remember { pm.hasSystemFeature(PackageManager.FEATURE_CONSUMER_IR) }
    val irManager = remember {
        context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
    }
    val irUsable = hasIr && irManager != null
    var irCarrierHz by rememberSaveable { mutableStateOf("38000") }
    var irPatternText by rememberSaveable {
        mutableStateOf("30000, 10000, 1000, 1000, 1000, 1000, 1000, 1000")
    }
    var irTextPayload by rememberSaveable { mutableStateOf("1") }
    var irStatus by remember { mutableStateOf<String?>(null) }
    var irStatusError by remember { mutableStateOf(false) }
    val hasNfc = remember { pm.hasSystemFeature(PackageManager.FEATURE_NFC) }
    val nfcAdapter = remember { NfcAdapter.getDefaultAdapter(context) }
    val nfcText = when {
        !hasNfc || nfcAdapter == null -> stringResource(R.string.experiment_sensor_nfc_unavailable)
        !nfcAdapter.isEnabled -> stringResource(R.string.experiment_sensor_nfc_off)
        else -> stringResource(R.string.experiment_sensor_nfc_on)
    }
    var nfcReadEnabled by rememberSaveable { mutableStateOf(false) }
    var nfcReadResult by remember { mutableStateOf<String?>(null) }
    val nfcReadMainHandler = remember { Handler(Looper.getMainLooper()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val nfcCanRead = hasNfc && nfcAdapter != null && nfcAdapter.isEnabled

    DisposableEffect(nfcReadEnabled, nfcCanRead) {
        if (!nfcReadEnabled || !nfcCanRead) {
            return@DisposableEffect onDispose { }
        }
        val act = context.findComponentActivity() ?: return@DisposableEffect onDispose { }
        val adapter = nfcAdapter ?: return@DisposableEffect onDispose { }
        val flags = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V
        val callback = NfcAdapter.ReaderCallback { tag ->
            val s = readNfcTagToString(tag)
            nfcReadMainHandler.post { nfcReadResult = s }
        }
        val obs = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    if (adapter.isEnabled) {
                        runCatching { adapter.enableReaderMode(act, callback, flags, null) }
                            .onFailure { e -> Log.w("SensorLab", "NFC enableReaderMode", e) }
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    runCatching { adapter.disableReaderMode(act) }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && adapter.isEnabled) {
            runCatching { adapter.enableReaderMode(act, callback, flags, null) }
        }
        onDispose {
            runCatching { adapter.disableReaderMode(act) }
            lifecycleOwner.lifecycle.removeObserver(obs)
        }
    }

    var btText by remember { mutableStateOf("—") }
    var wifiText by remember { mutableStateOf("—") }
    var sensorList by remember { mutableStateOf<List<String>>(emptyList()) }

    fun refreshBluetooth() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && (!btScan || !btConnect)) {
                btText = context.getString(R.string.experiment_sensor_bt_perm_needed)
                return
            }
            @Suppress("DEPRECATION")
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val btAdapter = manager?.adapter
            if (btAdapter == null) {
                btText = context.getString(R.string.experiment_sensor_bt_none)
                return
            }
            if (!btAdapter.isEnabled) {
                btText = context.getString(R.string.experiment_sensor_bt_disabled)
                return
            }
            @Suppress("MissingPermission", "InlinedApi")
            val n = try {
                btAdapter.bondedDevices.size
            } catch (e: SecurityException) {
                Log.w("SensorLab", "bondedDevices", e)
                -1
            }
            if (n < 0) {
                btText = context.getString(R.string.experiment_sensor_bt_perm_needed)
                return
            }
            btText = context.getString(R.string.experiment_sensor_bt_ok, n)
        } catch (e: Exception) {
            Log.w("SensorLab", "refreshBluetooth", e)
            btText = context.getString(
                R.string.experiment_sensor_comms_io_error,
                e.message ?: e.javaClass.simpleName,
            )
        }
    }

    fun refreshWifi() {
        try {
            @Suppress("DEPRECATION")
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wifi == null) {
                wifiText = "—"
                return
            }
            @Suppress("DEPRECATION")
            val ci = runCatching { wifi.connectionInfo }.getOrNull()
            var ssid = ci?.ssid?.trim('"') ?: "—"
            if (ssid.isEmpty() || ssid.equals("null", true)) {
                ssid = "—"
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (ssid.equals("<unknown ssid>", true) || ssid == "0x") {
                    ssid = if (fineLoc) {
                        "—"
                    } else {
                        context.getString(R.string.experiment_sensor_wifi_ssid_hidden)
                    }
                }
            }
            val rssi = ci?.rssi ?: 0
            val link = if (Build.VERSION.SDK_INT >= 21) (ci?.linkSpeed ?: 0) else 0
            wifiText = context.getString(R.string.experiment_sensor_wifi_line, ssid, rssi, link)
        } catch (e: Exception) {
            Log.w("SensorLab", "refreshWifi", e)
            wifiText = context.getString(
                R.string.experiment_sensor_comms_io_error,
                e.message ?: e.javaClass.simpleName,
            )
        }
    }

    fun refreshComms() {
        refreshBluetooth()
        refreshWifi()
    }

    LaunchedEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorList = sm.getSensorList(Sensor.TYPE_ALL).map { s ->
            "${s.name} (type ${s.type}, ${s.stringType})"
        }
    }
    LaunchedEffect(fineLoc, btScan, btConnect) {
        refreshComms()
    }

    fun stopVibrationLoop() {
        vibrateLoopJob?.cancel()
        defaultVibrator?.cancel()
        vibrateLoopJob = null
        vibLoopKind = 0
    }

    fun startVibrationLoop(dense: Boolean) {
        val v = defaultVibrator
        if (v == null || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB && !v.hasVibrator())) {
            localView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            return
        }
        vibrateLoopJob?.cancel()
        runCatching { v.cancel() }
        vibLoopKind = if (dense) 2 else 1
        vibrateLoopJob = scope.launch {
            try {
                while (isActive) {
                    if (dense) {
                        v.vibrate(VibrationEffect.createOneShot(22, 255))
                        delay(5L)
                    } else {
                        v.vibrate(VibrationEffect.createOneShot(90, 210))
                        delay(300L)
                    }
                }
            } finally {
                withContext(NonCancellable) {
                    runCatching { v.cancel() }
                }
            }
        }
    }

    val micBar by animateFloatAsState(micDb, label = "mic")

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.experiment_sensor_lab_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.experiment_sensor_lab_intro),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            // Камера + что делают разработчики
            Text(stringResource(R.string.experiment_sensor_section_camera_dev), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.experiment_sensor_section_camera_dev_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Звук
            Text(stringResource(R.string.experiment_sensor_section_audio_dev), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.experiment_sensor_section_audio_dev_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.experiment_sensor_mic_live), style = MaterialTheme.typography.labelLarge)
            if (!micPermState) {
                FilledTonalButton(
                    onClick = { micPermLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                ) {
                    Text(stringResource(R.string.experiment_sensor_grant_mic))
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.experiment_sensor_mic_toggle))
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = micEnabled,
                        onCheckedChange = { micEnabled = it },
                    )
                }
                LinearProgressIndicator(
                    progress = { micBar },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            // Сырые датчики (строки фиксированной высоты — показания не сдвигают соседние)
            val numberLocale = remember {
                val c = context.resources.configuration
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    c.locales[0] ?: Locale.getDefault()
                } else {
                    @Suppress("DEPRECATION")
                    c.locale
                }
            }
            Text(stringResource(R.string.experiment_sensor_section_motion), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.experiment_sensor_toggle_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val paused = stringResource(R.string.experiment_sensor_value_paused)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = {
                        enAccel = true
                        enGyro = true
                        enGravity = true
                        enLinear = true
                        enLight = true
                        enCompass = true
                        enMag = true
                        enBaro = true
                        enProx = true
                        enHum = true
                        enTemp = true
                        enSteps = true
                    },
                ) { Text(stringResource(R.string.experiment_sensor_all_on)) }
                TextButton(
                    onClick = {
                        enAccel = false
                        enGyro = false
                        enGravity = false
                        enLinear = false
                        enLight = false
                        enCompass = false
                        enMag = false
                        enBaro = false
                        enProx = false
                        enHum = false
                        enTemp = false
                        enSteps = false
                    },
                ) { Text(stringResource(R.string.experiment_sensor_all_off)) }
            }
            SensorMotionToggleRow(
                label = stringResource(R.string.experiment_sensor_lbl_accel),
                activeValue = formatXyz2(numberLocale, accel.first, accel.second, accel.third),
                missingValue = stringResource(R.string.experiment_sensor_not_on_device) + " (ACCEL)",
                pausedValue = paused,
                hardwareAvailable = hasAccel,
                collectionEnabled = enAccel,
                onCollectionChange = { enAccel = it },
            )
            SensorMotionToggleRow(
                label = stringResource(R.string.experiment_sensor_lbl_gyro),
                activeValue = formatXyz3(numberLocale, gyro.first, gyro.second, gyro.third),
                missingValue = stringResource(R.string.experiment_sensor_not_on_device) + " (GYRO)",
                pausedValue = paused,
                hardwareAvailable = hasGyro,
                collectionEnabled = enGyro,
                onCollectionChange = { enGyro = it },
            )
            SensorMotionToggleRow(
                label = stringResource(R.string.experiment_sensor_lbl_gravity),
                activeValue = formatXyz2(numberLocale, gravity.first, gravity.second, gravity.third),
                missingValue = stringResource(R.string.experiment_sensor_not_on_device) + " (GRAVITY)",
                pausedValue = paused,
                hardwareAvailable = hasGravity,
                collectionEnabled = enGravity,
                onCollectionChange = { enGravity = it },
            )
            SensorMotionToggleRow(
                label = stringResource(R.string.experiment_sensor_lbl_linear),
                activeValue = formatXyz2(numberLocale, linearAcc.first, linearAcc.second, linearAcc.third),
                missingValue = stringResource(R.string.experiment_sensor_not_on_device) + " (LINEAR)",
                pausedValue = paused,
                hardwareAvailable = hasLinearAcc,
                collectionEnabled = enLinear,
                onCollectionChange = { enLinear = it },
            )
            SensorMotionToggleRow(
                label = stringResource(R.string.experiment_sensor_lbl_light),
                activeValue = String.format(numberLocale, "%6.1f", light),
                missingValue = stringResource(R.string.experiment_sensor_not_on_device) + " (LIGHT)",
                pausedValue = paused,
                hardwareAvailable = hasLightSensor,
                collectionEnabled = enLight,
                onCollectionChange = { enLight = it },
            )
            SensorMotionToggleRow(
                label = stringResource(R.string.experiment_sensor_lbl_compass),
                activeValue = String.format(numberLocale, "%3.0f°", compassAzimuthDeg),
                missingValue = stringResource(R.string.experiment_sensor_not_on_device) + " (ROTATION)",
                pausedValue = paused,
                hardwareAvailable = hasRotationVector,
                collectionEnabled = enCompass,
                onCollectionChange = { enCompass = it },
            )
            SensorMotionToggleRow(
                label = stringResource(R.string.experiment_sensor_lbl_mag),
                activeValue = formatXyz1(numberLocale, magnet.first, magnet.second, magnet.third),
                missingValue = stringResource(R.string.experiment_sensor_not_on_device) + " (MAGNET)",
                pausedValue = paused,
                hardwareAvailable = hasMagnet,
                collectionEnabled = enMag,
                onCollectionChange = { enMag = it },
            )
            SensorMotionToggleRow(
                label = stringResource(R.string.experiment_sensor_lbl_baro),
                activeValue = String.format(numberLocale, "%6.1f", pressureHpa),
                missingValue = stringResource(R.string.experiment_sensor_not_on_device) + " (PRESSURE)",
                pausedValue = paused,
                hardwareAvailable = hasBaro,
                collectionEnabled = enBaro,
                onCollectionChange = { enBaro = it },
            )
            SensorMotionToggleRow(
                label = stringResource(R.string.experiment_sensor_lbl_prox),
                activeValue = String.format(numberLocale, "%5.2f", proxRaw),
                missingValue = stringResource(R.string.experiment_sensor_not_on_device) + " (PROX)",
                pausedValue = paused,
                hardwareAvailable = hasProx,
                collectionEnabled = enProx,
                onCollectionChange = { enProx = it },
            )
            SensorMotionToggleRow(
                label = stringResource(R.string.experiment_sensor_lbl_humid),
                activeValue = String.format(numberLocale, "%5.1f", relHumidity),
                missingValue = stringResource(R.string.experiment_sensor_not_on_device) + " (HUMID)",
                pausedValue = paused,
                hardwareAvailable = hasRelHum,
                collectionEnabled = enHum,
                onCollectionChange = { enHum = it },
            )
            SensorMotionToggleRow(
                label = stringResource(R.string.experiment_sensor_lbl_temp),
                activeValue = String.format(numberLocale, "%+5.1f", ambientTempC),
                missingValue = stringResource(R.string.experiment_sensor_not_on_device) + " (TEMP)",
                pausedValue = paused,
                hardwareAvailable = hasAmbientTemp,
                collectionEnabled = enTemp,
                onCollectionChange = { enTemp = it },
            )
            if (!activityRec) {
                FilledTonalButton(onClick = { requestForSection("steps") }) {
                    Text(stringResource(R.string.experiment_sensor_steps_perm))
                }
            } else {
                SensorMotionToggleRow(
                    label = stringResource(R.string.experiment_sensor_lbl_steps),
                    activeValue = String.format(numberLocale, "%.0f", stepsSinceBoot ?: 0f),
                    missingValue = stringResource(R.string.experiment_sensor_not_on_device) + " (STEPS)",
                    pausedValue = paused,
                    hardwareAvailable = hasStepCounter,
                    collectionEnabled = enSteps,
                    onCollectionChange = { enSteps = it },
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            // Маппинг: вход → выходы
            Text(stringResource(R.string.experiment_sensor_section_map), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.experiment_sensor_map_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.experiment_sensor_map_how_pick),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                stringResource(R.string.experiment_sensor_map_prereq_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 6.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.experiment_sensor_map_enable), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = mappingEnabled,
                    onCheckedChange = { mappingEnabled = it },
                )
            }
            if (mappingEnabled) {
                ExposedDropdownMenuBox(
                    expanded = mapMenuExpanded,
                    onExpandedChange = { mapMenuExpanded = it },
                ) {
                    val idx = mapInputOrdinal.coerceIn(0, srcLabelRes.lastIndex)
                    OutlinedTextField(
                        value = stringResource(srcLabelRes[idx]),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.experiment_sensor_map_source)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(mapMenuExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                    )
                    DropdownMenu(
                        expanded = mapMenuExpanded,
                        onDismissRequest = { mapMenuExpanded = false },
                        modifier = Modifier.heightIn(max = 320.dp),
                    ) {
                        srcLabelRes.forEachIndexed { i, r ->
                            DropdownMenuItem(
                                text = { Text(stringResource(r)) },
                                onClick = {
                                    mapInputOrdinal = i
                                    mapMenuExpanded = false
                                },
                            )
                        }
                    }
                }
                if (!mapSourceCollectionActive) {
                    Text(
                        stringResource(R.string.experiment_sensor_map_prereq_warn),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Text(
                    stringResource(R.string.experiment_sensor_map_value, mappedNormUi),
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    FilterChip(
                        selected = sinkGraph,
                        onClick = { sinkGraph = !sinkGraph },
                        label = { Text(stringResource(R.string.experiment_sensor_map_sink_graph)) },
                    )
                    FilterChip(
                        selected = sinkColor,
                        onClick = { sinkColor = !sinkColor },
                        label = { Text(stringResource(R.string.experiment_sensor_map_sink_color)) },
                    )
                    FilterChip(
                        selected = sinkTone,
                        onClick = { sinkTone = !sinkTone },
                        label = { Text(stringResource(R.string.experiment_sensor_map_sink_tone)) },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    FilterChip(
                        selected = sinkTts,
                        onClick = { sinkTts = !sinkTts },
                        label = { Text(stringResource(R.string.experiment_sensor_map_sink_tts)) },
                    )
                    FilterChip(
                        selected = sinkTorch,
                        onClick = { sinkTorch = !sinkTorch },
                        label = { Text(stringResource(R.string.experiment_sensor_map_sink_torch)) },
                    )
                }
                if (sinkTts && tts == null) {
                    Text(
                        stringResource(R.string.experiment_sensor_map_tts_unavailable),
                        color = MaterialTheme.colorScheme.outline,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                if (torchStrobe && sinkTorch) {
                    Text(
                        stringResource(R.string.experiment_sensor_map_torch_only_steady),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                if (sinkColor) {
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .background(
                                lerp(
                                    ComposeColor(0xFF1A237E),
                                    ComposeColor(0xFFFF6F00),
                                    mappedNormUi,
                                ),
                            ),
                    )
                }
                if (sinkGraph) {
                    val strokeColor = MaterialTheme.colorScheme.primary
                    Spacer(Modifier.height(4.dp))
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                    ) {
                        val graphPoints = mapGraphPoints.toList()
                        if (graphPoints.isEmpty()) return@Canvas
                        val w = size.width
                        val h = size.height
                        val n = graphPoints.size
                        val p = Path()
                        if (n == 1) {
                            val y = h * (1f - graphPoints[0].coerceIn(0f, 1f))
                            p.moveTo(0f, y)
                            p.lineTo(w, y)
                        } else {
                            graphPoints.forEachIndexed { i, v ->
                                val x = w * (i.toFloat() / (n - 1).toFloat().coerceAtLeast(1f))
                                val y = h * (1f - v.coerceIn(0f, 1f))
                                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                            }
                        }
                        drawPath(
                            p,
                            color = strokeColor,
                            style = Stroke(width = 2.dp.toPx()),
                        )
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            // GPS
            Text(stringResource(R.string.experiment_sensor_section_gps), style = MaterialTheme.typography.titleMedium)
            Text(locText, style = MaterialTheme.typography.bodySmall)
            if (!fineLoc) {
                FilledTonalButton(onClick = { requestForSection("loc") }) {
                    Text(stringResource(R.string.experiment_sensor_gps_perm))
                }
            } else {
                FilledTonalButton(
                    onClick = {
                        @SuppressLint("MissingPermission")
                        val client = LocationServices.getFusedLocationProviderClient(context)
                        client.getCurrentLocation(
                            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                            com.google.android.gms.tasks.CancellationTokenSource().token,
                        ).addOnSuccessListener { l: Location? ->
                            locText = if (l == null) {
                                context.getString(R.string.experiment_sensor_gps_empty)
                            } else {
                                context.getString(
                                    R.string.experiment_sensor_gps_line,
                                    l.latitude,
                                    l.longitude,
                                    l.accuracy,
                                )
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.experiment_sensor_gps_refresh))
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            // Фонарик
            Text(stringResource(R.string.experiment_sensor_section_torch), style = MaterialTheme.typography.titleMedium)
            if (!camPermState) {
                FilledTonalButton(
                    onClick = { camPermLauncher.launch(Manifest.permission.CAMERA) },
                ) {
                    Text(stringResource(R.string.experiment_sensor_torch_perm))
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.experiment_sensor_torch_on))
                    Spacer(Modifier.width(8.dp))
                    Switch(checked = torchEnabled, onCheckedChange = { torchEnabled = it })
                }
                if (torchEnabled) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.experiment_sensor_torch_strobe))
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = torchStrobe,
                            onCheckedChange = { torchStrobe = it },
                        )
                    }
                    if (torchStrobe) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = strobeHzStr,
                            onValueChange = { strobeHzStr = it },
                            singleLine = true,
                            label = { Text(stringResource(R.string.experiment_sensor_torch_freq_hz)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = strobeOnMsStr,
                            onValueChange = { strobeOnMsStr = it },
                            singleLine = true,
                            label = { Text(stringResource(R.string.experiment_sensor_torch_pulse_ms)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = strobeTotalStr,
                            onValueChange = { strobeTotalStr = it },
                            singleLine = true,
                            label = { Text(stringResource(R.string.experiment_sensor_torch_total_s)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Spacer(Modifier.height(4.dp))
                        val torchPercentText = if (mappingEnabled && sinkTorch && !torchStrobe) {
                            (effectiveTorchBrightness * 100f).toInt()
                        } else {
                            (torchBrightness * 100f).toInt()
                        }
                        Text(
                            stringResource(
                                R.string.experiment_sensor_torch_brightness_pct,
                                torchPercentText.coerceIn(1, 100),
                            ),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        if (mappingEnabled && sinkTorch && !torchStrobe) {
                            Text(
                                stringResource(R.string.experiment_sensor_map_value, mappedNormUi),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                        Slider(
                            value = torchBrightness,
                            onValueChange = { torchBrightness = it },
                            valueRange = 0.1f..1f,
                            steps = 17,
                            enabled = !(mappingEnabled && sinkTorch && !torchStrobe),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            stringResource(R.string.experiment_sensor_torch_brightness_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            // ИК
            Text(stringResource(R.string.experiment_sensor_section_ir), style = MaterialTheme.typography.titleMedium)
            Text(
                if (hasIr) {
                    stringResource(R.string.experiment_sensor_ir_yes)
                } else {
                    stringResource(R.string.experiment_sensor_ir_no)
                },
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                stringResource(R.string.experiment_sensor_ir_how),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (irUsable) {
                val irErrPattern = stringResource(R.string.experiment_sensor_ir_error_pattern)
                val irErrEmpty = stringResource(R.string.experiment_sensor_ir_error_empty)
                OutlinedTextField(
                    value = irCarrierHz,
                    onValueChange = { s -> irCarrierHz = s.filter { it.isDigit() } },
                    label = { Text(stringResource(R.string.experiment_sensor_ir_freq)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )
                FilledTonalButton(
                    onClick = {
                        val hz = irCarrierHz.toIntOrNull() ?: 38_000
                        val p = intArrayOf(30_000, 10_000, 1_000, 1_000, 1_000, 1_000, 1_000, 1_000)
                        irManager?.let { ir ->
                            scope.launch {
                                val err = withContext(Dispatchers.IO) {
                                    runCatching { ir.transmit(hz, p) }.exceptionOrNull()
                                }
                                if (err == null) {
                                    irStatus = context.getString(R.string.experiment_sensor_ir_ok)
                                    irStatusError = false
                                } else {
                                    irStatus = context.getString(
                                        R.string.experiment_sensor_ir_error_transmit,
                                        err.message ?: "",
                                    )
                                    irStatusError = true
                                }
                            }
                        }
                    },
                    modifier = Modifier.padding(vertical = 4.dp),
                ) {
                    Text(stringResource(R.string.experiment_sensor_ir_send_demo))
                }
                OutlinedTextField(
                    value = irPatternText,
                    onValueChange = { irPatternText = it },
                    label = { Text(stringResource(R.string.experiment_sensor_ir_pattern)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )
                FilledTonalButton(
                    onClick = {
                        val parsed = parseIrPattern(irPatternText)
                        if (parsed == null) {
                            irStatus = irErrPattern
                            irStatusError = true
                            return@FilledTonalButton
                        }
                        val hz = irCarrierHz.toIntOrNull() ?: 38_000
                        irManager?.let { ir ->
                            scope.launch {
                                val err = withContext(Dispatchers.IO) {
                                    runCatching { ir.transmit(hz, parsed) }.exceptionOrNull()
                                }
                                if (err == null) {
                                    irStatus = context.getString(R.string.experiment_sensor_ir_ok)
                                    irStatusError = false
                                } else {
                                    irStatus = context.getString(
                                        R.string.experiment_sensor_ir_error_transmit,
                                        err.message ?: "",
                                    )
                                    irStatusError = true
                                }
                            }
                        }
                    },
                    modifier = Modifier.padding(vertical = 4.dp),
                ) {
                    Text(stringResource(R.string.experiment_sensor_ir_send_pattern))
                }
                OutlinedTextField(
                    value = irTextPayload,
                    onValueChange = { n ->
                        val b = n.toByteArray(Charsets.UTF_8)
                        if (b.size <= 8) {
                            irTextPayload = n
                        } else {
                            var s = n
                            while (s.isNotEmpty() && s.toByteArray(Charsets.UTF_8).size > 8) {
                                s = s.dropLast(1)
                            }
                            irTextPayload = s
                        }
                    },
                    label = { Text(stringResource(R.string.experiment_sensor_ir_text_encode)) },
                    minLines = 1,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )
                FilledTonalButton(
                    onClick = {
                        val bytes = irTextPayload.toByteArray(Charsets.UTF_8)
                        if (bytes.isEmpty()) {
                            irStatus = irErrEmpty
                            irStatusError = true
                            return@FilledTonalButton
                        }
                        val p = irEncodeUtf8ToPattern(bytes, maxBytes = 8)
                        val hz = irCarrierHz.toIntOrNull() ?: 38_000
                        irManager?.let { ir ->
                            scope.launch {
                                val err = withContext(Dispatchers.IO) {
                                    runCatching { ir.transmit(hz, p) }.exceptionOrNull()
                                }
                                if (err == null) {
                                    irStatus = context.getString(R.string.experiment_sensor_ir_ok)
                                    irStatusError = false
                                } else {
                                    irStatus = context.getString(
                                        R.string.experiment_sensor_ir_error_transmit,
                                        err.message ?: "",
                                    )
                                    irStatusError = true
                                }
                            }
                        }
                    },
                    modifier = Modifier.padding(vertical = 4.dp),
                ) {
                    Text(stringResource(R.string.experiment_sensor_ir_send_text))
                }
                if (!irStatus.isNullOrBlank()) {
                    Text(
                        irStatus!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (irStatusError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
            }
            // NFC
            Text(stringResource(R.string.experiment_sensor_section_nfc), style = MaterialTheme.typography.titleMedium)
            Text(nfcText, style = MaterialTheme.typography.bodySmall)
            if (hasNfc && nfcAdapter != null) {
                Text(
                    stringResource(R.string.experiment_sensor_nfc_read_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (nfcAdapter.isEnabled) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.experiment_sensor_nfc_read_toggle),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = nfcReadEnabled,
                            onCheckedChange = { nfcReadEnabled = it },
                        )
                    }
                    if (nfcReadEnabled) {
                        if (nfcReadResult.isNullOrBlank()) {
                            Text(
                                stringResource(R.string.experiment_sensor_nfc_read_waiting),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            SelectionContainer {
                                Text(
                                    nfcReadResult!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }
                    }
                    if (!nfcReadResult.isNullOrBlank()) {
                        TextButton(onClick = { nfcReadResult = null }) {
                            Text(stringResource(R.string.experiment_sensor_nfc_read_clear))
                        }
                    }
                }
            }
            // Wi‑Fi
            Text(stringResource(R.string.experiment_sensor_section_wifi), style = MaterialTheme.typography.titleMedium)
            if (!fineLoc) {
                FilledTonalButton(onClick = { requestForSection("loc") }) {
                    Text(stringResource(R.string.experiment_sensor_wifi_gps_for_ssid))
                }
            }
            Text(wifiText, style = MaterialTheme.typography.bodySmall)
            // Bluetooth
            Text(stringResource(R.string.experiment_sensor_section_bt), style = MaterialTheme.typography.titleMedium)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && (!btScan || !btConnect)) {
                FilledTonalButton(onClick = { requestForSection("bt") }) {
                    Text(stringResource(R.string.experiment_sensor_bt_perm_btn))
                }
            }
            Text(btText, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { refreshComms() }) {
                Text(stringResource(R.string.experiment_sensor_refresh_comms))
            }
            // Вибрация
            Text(stringResource(R.string.experiment_sensor_section_vibrate), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.experiment_sensor_vibrate_loop_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (vibLoopKind != 0) {
                Text(
                    text = if (vibLoopKind == 2) {
                        stringResource(R.string.experiment_sensor_vibrate_loop_active_dense)
                    } else {
                        stringResource(R.string.experiment_sensor_vibrate_loop_active_short)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    modifier = Modifier.weight(1f),
                    onClick = { startVibrationLoop(dense = false) },
                ) { Text(stringResource(R.string.experiment_sensor_vibrate_loop_short), maxLines = 2) }
                FilledTonalButton(
                    modifier = Modifier.weight(1f),
                    onClick = { startVibrationLoop(dense = true) },
                ) { Text(stringResource(R.string.experiment_sensor_vibrate_loop_dense), maxLines = 2) }
            }
            TextButton(
                onClick = { stopVibrationLoop() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.experiment_sensor_vibrate_loop_stop))
            }
            FilledTonalButton(
                onClick = {
                    localView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    localView.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    val v = defaultVibrator
                    if (v == null || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB && !v.hasVibrator())) {
                        localView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        return@FilledTonalButton
                    }
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
                        } else {
                            v.vibrate(VibrationEffect.createOneShot(300, 255))
                        }
                    } catch (e: Exception) {
                        Log.w("SensorLab", "Vibrate", e)
                        localView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    }
                },
            ) {
                Text(stringResource(R.string.experiment_sensor_vibrate_test))
            }
            // Список датчиков
            Text(stringResource(R.string.experiment_sensor_section_list), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.experiment_sensor_list_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            sensorList.forEach { line ->
                Text(line, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(vertical = 2.dp))
            }
        }
    }
}

private fun parseIrPattern(s: String): IntArray? {
    val parts = s.split(',', ';', ' ', '\n', '\t')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    val nums = parts.mapNotNull { it.toIntOrNull() }
    if (nums.size < 2 || nums.size % 2 != 0) {
        return null
    }
    if (nums.any { it <= 0 }) {
        return null
    }
    return nums.toIntArray()
}

/**
 * Кодирует до [maxBytes] байт UTF-8 в демо-паттерн (синхро + 8 бит/байт).
 * Приметнику нужен согласованный декодер; иначе это просто пачка ИК-импульсов.
 */
private fun irEncodeUtf8ToPattern(utf8: ByteArray, maxBytes: Int = 8): IntArray {
    val n = minOf(utf8.size, maxBytes)
    val b = if (n <= 0) byteArrayOf() else utf8.copyOf(n)
    val out = ArrayList<Int>(2 + 16 * b.size + 2)
    out.add(2_000)
    out.add(2_000)
    for (byte in b) {
        for (i in 7 downTo 0) {
            val bit = (byte.toInt() shr i) and 1
            out.add(300)
            out.add(200 + bit * 500)
        }
    }
    out.add(300)
    out.add(20_000)
    return out.toIntArray()
}

private tailrec fun Context.findComponentActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findComponentActivity()
    else -> null
}

private fun readNfcTagToString(tag: Tag): String {
    val sb = StringBuilder()
    val uid = tag.id
    if (uid.isNotEmpty()) {
        sb.append("UID: ").append(uid.toHexColon()).append('\n')
    } else {
        sb.append("UID: —\n")
    }
    sb.append("Технологии: ").append(tag.techList.joinToString { s -> s.substringAfterLast('.') })
    val ndef = runCatching { Ndef.get(tag) }.getOrNull()
    if (ndef != null) {
        runCatching {
            ndef.connect()
            try {
                val ndefMessage = runCatching { ndef.ndefMessage }.getOrNull() ?: ndef.cachedNdefMessage
                if (ndefMessage == null) {
                    sb.append("\n\n--- NDEF ---\n(пусто)")
                } else {
                    val recs = ndefMessage.records
                    sb.append("\n\n--- NDEF (").append(recs.size).append(" з.) ---\n")
                    recs.forEachIndexed { i, r ->
                        sb.append(ndefRecordLine(r, i))
                    }
                }
            } finally {
                runCatching { ndef.close() }
            }
        }.onFailure { e ->
            sb.append("\n\nNDEF: ").append(e.message ?: e.javaClass.simpleName)
            runCatching { ndef.close() }
        }
    } else {
        if (NdefFormatable.get(tag) != null) {
            sb.append(
                "\n\n(NdefFormatable: метка может быть пуста или NDEF ожидает записи; подробный дамп NDEF ниже, если нет).",
            )
        } else {
            sb.append("\n\n(NDEF не смонтирован на этой технологии; см. NfcA/IsoDep, если доступны).")
        }
    }
    runCatching { NfcA.get(tag) }.getOrNull()?.let { a ->
        runCatching {
            a.connect()
            try {
                val atqa = a.atqa
                val sak = 0xFF and a.sak.toInt()
                sb.append("\n\nNfcA: ATQA=").append(atqa?.toHexColon() ?: "—")
                sb.append(" SAK=$sak")
            } finally {
                a.close()
            }
        }
    }
    runCatching { IsoDep.get(tag) }.getOrNull()?.let { iso ->
        runCatching {
            iso.connect()
            try {
                val h = iso.historicalBytes
                if (h != null && h.isNotEmpty()) {
                    sb.append("\n\nIsoDep, истор. байты: ").append(h.toHexColon())
                }
            } finally {
                iso.close()
            }
        }
    }
    return sb.toString()
}

private fun ByteArray.toHexColon(): String =
    joinToString(":") { String.format("%02X", 0xFF and it.toInt()) }

/** Подмножество NdefRecord URI: первый октет — индекс префикса. */
private val NdefUriPrefix = arrayOf(
    "",
    "http://www.",
    "https://www.",
    "http://",
    "https://",
    "tel:",
    "mailto:",
    "ftp://anonymous:anonymous@",
    "ftp://ftp.",
    "ftps://",
    "sftp://",
    "smb://",
    "nfs://",
    "ftp://",
    "dav://",
    "news:",
    "telnet://",
    "imap:",
    "rtsp://",
    "urn:",
    "pop:",
    "sip:",
    "sips:",
    "tftp:",
    "btspp://",
    "btl2cap://",
    "btgoep://",
    "tcpobex://",
    "irdaobex://",
    "file://",
    "urn:nfc:",
    "urn:epc:",
    "urn:epc:tag",
    "urn:epc:pat",
    "urn:epc:raw",
    "urn:epc:",
    "urn:nfc:raw",
)

/** AAR, RFC 4006 — при записи через [NdefRecord.createApplicationRecord]. */
private val NdefRtdAndroidPkg = "android.com:pkg".toByteArray(StandardCharsets.US_ASCII)

@Suppress("DEPRECATION")
private fun ndefRecordLine(rec: NdefRecord, index: Int): String {
    return when {
        rec.tnf == NdefRecord.TNF_WELL_KNOWN && rec.type != null &&
            (NdefRecord.RTD_TEXT contentEquals rec.type) -> {
            val p = rec.payload
            if (p.isEmpty()) {
                "[$index] Текст: (пусто)\n"
            } else {
                val langLen = p[0].toInt() and 0x3F
                val isUtf16 = (p[0].toInt() and 0x80) != 0
                val enc = if (isUtf16) {
                    StandardCharsets.UTF_16
                } else {
                    StandardCharsets.UTF_8
                }
                val off = 1 + langLen
                val text = if (off < p.size) {
                    String(p, off, p.size - off, enc)
                } else {
                    "(некорректная RTD Text)"
                }
                "[$index] Текст: $text\n"
            }
        }
        rec.tnf == NdefRecord.TNF_WELL_KNOWN && rec.type != null &&
            (NdefRecord.RTD_URI contentEquals rec.type) -> {
            val p = rec.payload
            if (p.isEmpty()) {
                "[$index] URI: (пусто)\n"
            } else {
                val code = 0xFF and p[0].toInt()
                val prefix = if (code in NdefUriPrefix.indices) NdefUriPrefix[code] else ""
                val path = if (p.size > 1) {
                    String(p, 1, p.size - 1, StandardCharsets.UTF_8)
                } else {
                    ""
                }
                "[$index] URI: $prefix$path\n"
            }
        }
        rec.tnf == NdefRecord.TNF_WELL_KNOWN && rec.type != null &&
            (NdefRtdAndroidPkg contentEquals rec.type) -> {
            val pkg = runCatching { String(rec.payload) }.getOrNull() ?: "?"
            "[$index] Android-приложение: $pkg\n"
        }
        else -> {
            val typeS = rec.type?.let { String(it) } ?: "—"
            val idPart = if (rec.id == null || rec.id.isEmpty()) {
                "—"
            } else {
                rec.id.toHexColon()
            }
            "[$index] tnf=${rec.tnf} type=\"$typeS\" id=$idPart payload(hex)=${
            rec.payload.toHexColon()}\n"
        }
    }
}

/**
 * Подпись + значение; слева переключатель опроса (реальная подписка/отписка в SensorManager).
 * Фиксированная высота, моношрифт у чисел — без «прыжков».
 */
@Composable
private fun SensorMotionToggleRow(
    label: String,
    activeValue: String,
    missingValue: String,
    pausedValue: String,
    hardwareAvailable: Boolean,
    collectionEnabled: Boolean,
    onCollectionChange: (Boolean) -> Unit,
) {
    val canCollect = hardwareAvailable && collectionEnabled
    val text = when {
        !hardwareAvailable -> missingValue
        !collectionEnabled -> pausedValue
        else -> activeValue
    }
    val muted = !hardwareAvailable || !collectionEnabled
    val color = if (muted) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.width(56.dp),
            contentAlignment = Alignment.Center,
        ) {
            Switch(
                checked = collectionEnabled && hardwareAvailable,
                onCheckedChange = { if (hardwareAvailable) onCollectionChange(it) },
                enabled = hardwareAvailable,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            modifier = Modifier.width(120.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            fontFamily = if (muted) null else FontFamily.Monospace,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun formatXyz2(locale: Locale, x: Float, y: Float, z: Float): String =
    String.format(locale, "x=%+7.2f y=%+7.2f z=%+7.2f", x, y, z)

private fun formatXyz3(locale: Locale, x: Float, y: Float, z: Float): String =
    String.format(locale, "x=%+8.3f y=%+8.3f z=%+8.3f", x, y, z)

private fun formatXyz1(locale: Locale, x: Float, y: Float, z: Float): String =
    String.format(locale, "x=%+6.1f y=%+6.1f z=%+6.1f", x, y, z)

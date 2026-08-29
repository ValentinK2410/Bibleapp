package com.example.bible.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import com.example.bible.data.BibleTtsSampleSpeak
import com.example.bible.data.TtsUserSettings
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Key
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import com.example.bible.R
import com.example.bible.ui.theme.BibleAppThemePreset

private fun themePresetLabelRes(preset: BibleAppThemePreset): Int = when (preset) {
    BibleAppThemePreset.STANDARD -> R.string.theme_preset_standard
    BibleAppThemePreset.BRUTAL -> R.string.theme_preset_brutal
    BibleAppThemePreset.PINK -> R.string.theme_preset_pink
    BibleAppThemePreset.SKY -> R.string.theme_preset_sky
    BibleAppThemePreset.MEADOW -> R.string.theme_preset_meadow
    BibleAppThemePreset.PAPYRUS -> R.string.theme_preset_papyrus
    BibleAppThemePreset.LEATHER -> R.string.theme_preset_leather
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainSettingsScreen(
    isDark: Boolean,
    mimicControlEnabled: Boolean,
    mimicControlV2Enabled: Boolean,
    mimicCameraPreviewEnabled: Boolean,
    mimicFaceOverlayEnabled: Boolean,
    mimicMediaPipeFaceGeometryEnabled: Boolean,
    mimicVelocityVectorEnabled: Boolean,
    appThemePreset: BibleAppThemePreset,
    onAppThemePresetChange: (BibleAppThemePreset) -> Unit,
    onBack: () -> Unit,
    onToggleDarkMode: () -> Unit,
    onToggleMimicControl: () -> Unit,
    onToggleMimicControlV2: () -> Unit,
    onToggleMimicCameraPreview: () -> Unit,
    onToggleMimicFaceOverlay: () -> Unit,
    onToggleMimicMediaPipeFaceGeometry: () -> Unit,
    onToggleMimicVelocityVector: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenShareApp: () -> Unit,
    onOpenOfflineDownload: () -> Unit,
    onOpenNetworkRegion: () -> Unit,
    onOpenMenuOrder: () -> Unit,
    ttsUserSettings: TtsUserSettings,
    onTtsSpeechRateChange: (Float) -> Unit,
    onTtsPitchChange: (Float) -> Unit,
    onTtsEnginePackageChange: (String) -> Unit,
    onTtsPreferHighQualityChange: (Boolean) -> Unit,
    onOpenTtsSystemSettings: () -> Unit,
    deepSeekApiKey: String,
    deepSeekKeyTest: DeepSeekKeyTestUiState,
    onSaveDeepSeekApiKey: (String) -> Unit,
    onTestDeepSeekApiKey: (String) -> Unit,
) {
    var hintsExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var mimicOverlayGranted by remember {
        mutableStateOf(Settings.canDrawOverlays(context.applicationContext))
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                mimicOverlayGranted = Settings.canDrawOverlays(context.applicationContext)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    fun openOverlayPermissionSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        )
        context.startActivity(intent)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.main_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.main_settings_hints_toggle_title)) },
                trailingContent = {
                    Icon(
                        imageVector = if (hintsExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = stringResource(
                            if (hintsExpanded) {
                                R.string.main_settings_hints_toggle_collapse_cd
                            } else {
                                R.string.main_settings_hints_toggle_expand_cd
                            },
                        ),
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { hintsExpanded = !hintsExpanded },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.main_settings_theme_dark)) },
                supportingContent = if (hintsExpanded) {
                    { Text(stringResource(R.string.main_settings_theme_light)) }
                } else {
                    null
                },
                leadingContent = {
                    Icon(
                        if (isDark) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                trailingContent = {
                    Switch(
                        checked = isDark,
                        onCheckedChange = { wantDark ->
                            if (wantDark != isDark) onToggleDarkMode()
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.main_settings_mimic_control)) },
                supportingContent = if (hintsExpanded) {
                    { Text(stringResource(R.string.main_settings_mimic_control_hint)) }
                } else {
                    null
                },
                leadingContent = {
                    Icon(
                        Icons.Filled.Gesture,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                trailingContent = {
                    Switch(
                        checked = mimicControlEnabled,
                        onCheckedChange = { want ->
                            if (want != mimicControlEnabled) onToggleMimicControl()
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.main_settings_mimic_control_v2)) },
                supportingContent = if (hintsExpanded) {
                    { Text(stringResource(R.string.main_settings_mimic_control_v2_hint)) }
                } else {
                    null
                },
                leadingContent = {
                    Icon(
                        Icons.Filled.Navigation,
                        contentDescription = null,
                        tint = if (mimicControlEnabled) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        },
                    )
                },
                trailingContent = {
                    Switch(
                        checked = mimicControlV2Enabled,
                        onCheckedChange = { want ->
                            if (want != mimicControlV2Enabled) onToggleMimicControlV2()
                        },
                        enabled = mimicControlEnabled,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.main_settings_mimic_camera_preview)) },
                supportingContent = if (hintsExpanded) {
                    { Text(stringResource(R.string.main_settings_mimic_camera_preview_hint)) }
                } else {
                    null
                },
                leadingContent = {
                    Icon(
                        Icons.Filled.Videocam,
                        contentDescription = null,
                        tint = if (mimicControlEnabled) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        },
                    )
                },
                trailingContent = {
                    Switch(
                        checked = mimicCameraPreviewEnabled,
                        onCheckedChange = { want ->
                            if (want != mimicCameraPreviewEnabled) onToggleMimicCameraPreview()
                        },
                        enabled = mimicControlEnabled,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.main_settings_mimic_face_overlay)) },
                supportingContent = if (hintsExpanded) {
                    { Text(stringResource(R.string.main_settings_mimic_face_overlay_hint)) }
                } else {
                    null
                },
                leadingContent = {
                    Icon(
                        Icons.Outlined.Layers,
                        contentDescription = null,
                        tint = if (mimicControlEnabled) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        },
                    )
                },
                trailingContent = {
                    Switch(
                        checked = mimicFaceOverlayEnabled,
                        onCheckedChange = { want ->
                            if (want != mimicFaceOverlayEnabled) onToggleMimicFaceOverlay()
                        },
                        enabled = mimicControlEnabled,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.main_settings_mimic_mediapipe_geometry)) },
                supportingContent = if (hintsExpanded) {
                    { Text(stringResource(R.string.main_settings_mimic_mediapipe_geometry_hint)) }
                } else {
                    null
                },
                leadingContent = {
                    Icon(
                        Icons.Outlined.Layers,
                        contentDescription = null,
                        tint = if (mimicControlEnabled) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        },
                    )
                },
                trailingContent = {
                    Switch(
                        checked = mimicMediaPipeFaceGeometryEnabled,
                        onCheckedChange = { want ->
                            if (want != mimicMediaPipeFaceGeometryEnabled) onToggleMimicMediaPipeFaceGeometry()
                        },
                        enabled = mimicControlEnabled,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.main_settings_mimic_velocity_vector)) },
                supportingContent = if (hintsExpanded) {
                    { Text(stringResource(R.string.main_settings_mimic_velocity_vector_hint)) }
                } else {
                    null
                },
                leadingContent = {
                    Icon(
                        Icons.Filled.Navigation,
                        contentDescription = null,
                        tint = if (mimicControlEnabled) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        },
                    )
                },
                trailingContent = {
                    Switch(
                        checked = mimicVelocityVectorEnabled,
                        onCheckedChange = { want ->
                            if (want != mimicVelocityVectorEnabled) onToggleMimicVelocityVector()
                        },
                        enabled = mimicControlEnabled,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (mimicControlEnabled) {
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text(stringResource(R.string.main_settings_mimic_overlay_permission)) },
                    supportingContent = {
                        val overlayHintRes = when {
                            mimicOverlayGranted && hintsExpanded ->
                                R.string.main_settings_mimic_overlay_permission_hint_granted
                            mimicOverlayGranted ->
                                R.string.main_settings_mimic_overlay_permission_short_ok
                            hintsExpanded ->
                                R.string.main_settings_mimic_overlay_permission_hint_needed
                            else ->
                                R.string.main_settings_mimic_overlay_permission_short_needed
                        }
                        Text(stringResource(overlayHintRes))
                    },
                    leadingContent = {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = null,
                            tint = if (mimicOverlayGranted) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                    },
                    trailingContent = {
                        if (!mimicOverlayGranted) {
                            TextButton(onClick = { openOverlayPermissionSettings() }) {
                                Text(stringResource(R.string.main_settings_mimic_overlay_permission_open))
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !mimicOverlayGranted) { openOverlayPermissionSettings() },
                )
            }
            HorizontalDivider()
            Text(
                text = stringResource(R.string.main_settings_app_theme_section),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            if (hintsExpanded) {
                Text(
                    text = stringResource(R.string.main_settings_app_theme_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
                )
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
            ) {
                items(BibleAppThemePreset.entries.toList()) { preset ->
                    FilterChip(
                        selected = preset == appThemePreset,
                        onClick = {
                            if (preset != appThemePreset) onAppThemePresetChange(preset)
                        },
                        label = { Text(stringResource(themePresetLabelRes(preset))) },
                    )
                }
            }
            HorizontalDivider()
            Text(
                text = stringResource(R.string.main_settings_tts_section),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            if (hintsExpanded) {
                Text(
                    text = stringResource(R.string.main_settings_tts_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
                )
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.main_settings_tts_hq)) },
                supportingContent = if (hintsExpanded) {
                    { Text(stringResource(R.string.main_settings_tts_hq_hint)) }
                } else {
                    null
                },
                leadingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                trailingContent = {
                    Switch(
                        checked = ttsUserSettings.preferHighQuality,
                        onCheckedChange = { onTtsPreferHighQualityChange(it) },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(R.string.main_settings_tts_rate),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "%.2f".format(ttsUserSettings.speechRate),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Slider(
                value = ttsUserSettings.speechRate,
                onValueChange = onTtsSpeechRateChange,
                valueRange = 0.35f..2.2f,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(R.string.main_settings_tts_pitch),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "%.2f".format(ttsUserSettings.pitch),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Slider(
                value = ttsUserSettings.pitch,
                onValueChange = onTtsPitchChange,
                valueRange = 0.5f..1.4f,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            )
            OutlinedTextField(
                value = ttsUserSettings.enginePackage,
                onValueChange = onTtsEnginePackageChange,
                label = { Text(stringResource(R.string.main_settings_tts_engine)) },
                supportingText = if (hintsExpanded) {
                    { Text(stringResource(R.string.main_settings_tts_engine_hint)) }
                } else {
                    null
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.main_settings_tts_open_system)) },
                leadingContent = {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenTtsSystemSettings),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.main_settings_tts_test)) },
                leadingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { BibleTtsSampleSpeak.play(context) },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.share_app_title)) },
                supportingContent = if (hintsExpanded) {
                    { Text(stringResource(R.string.share_app_settings_subtitle)) }
                } else {
                    null
                },
                leadingContent = {
                    Icon(Icons.Filled.Share, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenShareApp),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.backup_title)) },
                supportingContent = if (hintsExpanded) {
                    { Text(stringResource(R.string.backup_settings_subtitle)) }
                } else {
                    null
                },
                leadingContent = {
                    Icon(Icons.Filled.Save, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenBackup),
            )
            ListItem(
                headlineContent = { Text("Предзагрузка для офлайна") },
                supportingContent = if (hintsExpanded) {
                    { Text("Кэш материалов изучения") }
                } else {
                    null
                },
                leadingContent = {
                    Icon(Icons.Filled.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenOfflineDownload),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.network_region_title)) },
                supportingContent = if (hintsExpanded) {
                    { Text(stringResource(R.string.network_region_settings_hint)) }
                } else {
                    null
                },
                leadingContent = {
                    Icon(Icons.Filled.Public, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenNetworkRegion),
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            DeepSeekSettingsSection(
                hintsExpanded = hintsExpanded,
                savedKey = deepSeekApiKey,
                keyTest = deepSeekKeyTest,
                onSave = onSaveDeepSeekApiKey,
                onTest = onTestDeepSeekApiKey,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            ListItem(
                headlineContent = { Text(stringResource(R.string.main_settings_menu_order)) },
                leadingContent = {
                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenMenuOrder),
            )
        }
    }
}

@Composable
private fun DeepSeekSettingsSection(
    hintsExpanded: Boolean,
    savedKey: String,
    keyTest: DeepSeekKeyTestUiState,
    onSave: (String) -> Unit,
    onTest: (String) -> Unit,
) {
    val context = LocalContext.current
    var draft by remember { mutableStateOf(savedKey) }
    LaunchedEffect(savedKey) {
        draft = savedKey
    }
    ListItem(
        headlineContent = { Text(stringResource(R.string.main_settings_deepseek_title)) },
        supportingContent = {
            Text(
                if (hintsExpanded) {
                    stringResource(R.string.main_settings_deepseek_hint)
                } else if (savedKey.isNotBlank()) {
                    stringResource(R.string.main_settings_deepseek_saved)
                } else {
                    stringResource(R.string.main_settings_deepseek_empty)
                },
            )
        },
        leadingContent = {
            Icon(
                Icons.Filled.Key,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = draft,
        onValueChange = { draft = it },
        label = { Text(stringResource(R.string.main_settings_deepseek_key_label)) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
    )
    TextButton(
        onClick = {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://platform.deepseek.com/api_keys")),
            )
        },
        modifier = Modifier.padding(horizontal = 8.dp),
    ) {
        Text(stringResource(R.string.main_settings_deepseek_get_key))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = { onSave(draft) }) {
            Text(stringResource(R.string.main_settings_deepseek_save))
        }
        TextButton(
            onClick = { onTest(draft) },
            enabled = !keyTest.loading,
        ) {
            Text(stringResource(R.string.main_settings_deepseek_test))
        }
        TextButton(onClick = {
            draft = ""
            onSave("")
        }) {
            Text(stringResource(R.string.main_settings_deepseek_clear))
        }
        if (keyTest.loading) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        }
    }
    keyTest.message?.let { msg ->
        Text(
            msg,
            color = if (keyTest.ok) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
    Spacer(Modifier.height(8.dp))
}

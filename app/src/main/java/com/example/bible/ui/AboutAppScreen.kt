package com.example.bible.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.bible.R
import com.example.bible.data.AppUpdateCatalog
import com.example.bible.data.RemoteAppVersion
import com.example.bible.data.WhatsNewRelease
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class LocalAppVersion(
    val versionCode: Long,
    val versionName: String,
)

private fun readLocalVersion(context: android.content.Context): LocalAppVersion {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        info.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        info.versionCode.toLong()
    }
    return LocalAppVersion(code, info.versionName.orEmpty())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutAppScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val local = remember { readLocalVersion(context) }
    val releases = remember { AppUpdateCatalog.loadWhatsNew(context) }

    var checking by remember { mutableStateOf(false) }
    var remote by remember { mutableStateOf<RemoteAppVersion?>(null) }
    var checkError by remember { mutableStateOf<String?>(null) }

    fun checkUpdate(manual: Boolean) {
        if (checking) return
        scope.launch {
            checking = true
            checkError = null
            try {
                val r = withContext(Dispatchers.IO) { AppUpdateCatalog.fetchRemote() }
                remote = r
                if (manual && r.versionCode <= local.versionCode) {
                    Toast.makeText(context, R.string.about_app_up_to_date, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                checkError = e.message ?: context.getString(R.string.about_app_check_failed)
                if (manual) {
                    Toast.makeText(context, checkError, Toast.LENGTH_LONG).show()
                }
            } finally {
                checking = false
            }
        }
    }

    LaunchedEffect(Unit) { checkUpdate(manual = false) }

    val updateAvailable = remote?.let { it.versionCode > local.versionCode } == true

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_app_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary,
                                ),
                            ),
                        )
                        .padding(20.dp),
                ) {
                    Column {
                        Text(
                            stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.about_app_version_line, local.versionName, local.versionCode),
                            color = Color.White.copy(alpha = 0.92f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (updateAvailable && remote != null) {
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.NewReleases,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    stringResource(
                                        R.string.about_app_update_available,
                                        remote!!.versionName,
                                    ),
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                            remote!!.notes.takeIf { it.isNotBlank() }?.let { notes ->
                                Text(
                                    notes,
                                    color = Color.White.copy(alpha = 0.9f),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = { checkUpdate(manual = true) },
                    enabled = !checking,
                    modifier = Modifier.weight(1f),
                ) {
                    if (checking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Text(
                        stringResource(R.string.about_app_check_update),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                if (updateAvailable && remote != null) {
                    Button(
                        onClick = {
                            val url = remote!!.apkUrl.ifBlank { AppUpdateCatalog.DEFAULT_APK_URL }
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(
                            stringResource(R.string.about_app_download),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(AppUpdateCatalog.RELEASES_PAGE)),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    stringResource(R.string.about_app_open_releases),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            checkError?.let { err ->
                Text(
                    err,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            HorizontalDivider()

            Text(
                stringResource(R.string.about_app_whats_new_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            releases.forEach { release ->
                ReleaseCard(release)
            }
        }
    }
}

@Composable
private fun ReleaseCard(release: WhatsNewRelease) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.about_app_release_version, release.versionName),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    release.date,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            release.highlights.forEach { block ->
                Spacer(Modifier.height(10.dp))
                Text(
                    block.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                block.items.forEach { item ->
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                            modifier = Modifier
                                .padding(top = 2.dp, end = 8.dp)
                                .size(16.dp),
                        )
                        Text(item, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (release.fixes.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.about_app_fixes_title),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                release.fixes.forEach { fix ->
                    Text(
                        "• $fix",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

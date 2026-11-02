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
import com.example.bible.BuildConfig
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
    val fromBuild = LocalAppVersion(
        versionCode = BuildConfig.VERSION_CODE.toLong(),
        versionName = BuildConfig.VERSION_NAME,
    )
    if (fromBuild.versionName.isNotBlank()) return fromBuild
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
    var offlineNotice by remember { mutableStateOf<String?>(null) }

    fun checkUpdate(manual: Boolean) {
        if (checking) return
        scope.launch {
            checking = true
            offlineNotice = null
            try {
                val result = withContext(Dispatchers.IO) { AppUpdateCatalog.checkRemote(context) }
                remote = result.version
                offlineNotice = result.networkError?.let { err ->
                    context.getString(R.string.about_app_offline_notice, err)
                }
                if (manual) {
                    when {
                        result.fromNetwork && result.version.versionCode <= local.versionCode ->
                            Toast.makeText(context, R.string.about_app_up_to_date, Toast.LENGTH_SHORT).show()
                        !result.fromNetwork ->
                            Toast.makeText(context, result.networkError, Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                offlineNotice = AppUpdateCatalog.networkErrorMessage(e.message)
                if (manual) {
                    Toast.makeText(context, offlineNotice, Toast.LENGTH_LONG).show()
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

            offlineNotice?.let { notice ->
                Text(
                    notice,
                    color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            AboutProgramOverviewCard()

            HorizontalDivider()

            val currentRelease = releases.firstOrNull { it.versionName == local.versionName }
            val olderReleases = releases.filter { it.versionName != local.versionName }

            if (currentRelease != null) {
                Text(
                    stringResource(R.string.about_app_whats_new_this_version),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                ReleaseCard(currentRelease)
            }

            if (olderReleases.isNotEmpty()) {
                Text(
                    stringResource(R.string.about_app_whats_new_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                olderReleases.forEach { release ->
                    ReleaseCard(release)
                }
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

private data class ProgramSectionInfo(
    val title: String,
    val description: String,
)

private val PROGRAM_SECTIONS = listOf(
    ProgramSectionInfo(
        title = "📖 Чтение и глубокое изучение Писания",
        description = "Офлайн-переводы (Синодальный, НРП, РБО, Кулаковых, WEB, подстрочник Винокурова), сравнение 2–3 переводов рядом, словарь Стронга, комментарии, перекрёстные ссылки, план «Библия за год», родословная, исторические карты и изучение библейских языков.",
    ),
    ProgramSectionInfo(
        title = "🎧 Озвучка и редактор таймкодов",
        description = "Более 8 дикторов, чтение на иврите и греческом, фоновая загрузка аудиоглав, настройка скорости, таймер сна и пословная/постиховая синхронизация через редактор таймкодов.",
    ),
    ProgramSectionInfo(
        title = "🏡 Попечительная о доме",
        description = "Система домашнего уюта и порядка: планирование уборки по этажам, комнатам и датам недели, утренние и вечерние привычки, статистика, выбор нежной палитры в шапке и фоновый таймер «15-минутка» (5/10/15/20 мин) с тиканьем и голосовым отсчётом.",
    ),
    ProgramSectionInfo(
        title = "👨‍👩‍👧 Детский раздел и Детская Библия",
        description = "Полка из всех 66 книг Библии для трёх возрастов (5–7, 8–10, 11–13 лет), сюжетные истории с картинками и записью голоса родителей, стих недели, молитвы, песни, раскраски, карточки героев, ценностей, азбука, цифры, космос, природа и развивающие игры (включая Брейн-ринг на 1000 вопросов).",
    ),
    ProgramSectionInfo(
        title = "🎵 Медиатека, песнопение и студия музыканта",
        description = "Офлайн-каталог видео и аудио с плейлистами и фоновой докачкой, сборник песен с аккордами и таймкодами строк, многодорожечная студия записи, хроматический тюнер, метроном и тренажёр нотной грамоты.",
    ),
    ProgramSectionInfo(
        title = "🗺️ Жизнь, община и путешествия",
        description = "Заметки со ссылками на стихи, ежедневник с будильником, микроблог, своя книга контактов с днями рождения, раздел «Моя церковь» и «Мои путешествия» с GPS-треками, геозонами и фото по маршруту.",
    ),
    ProgramSectionInfo(
        title = "🤖 ИИ-помощники и доступность",
        description = "Интеграция с DeepSeek и GigaChat (размышление над стихом, голосовой диалог, работа с фото), управление чтением мимикой лица через камеру и полный ZIP-бэкап всех пользовательских данных.",
    ),
)

@Composable
private fun AboutProgramOverviewCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "О приложении",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Многофункциональная офлайн-библиотека и семейный помощник на каждый день: чтение и изучение Библии, аудиокнига, детский образовательный центр, уход за домом, музыкальная студия и путевой дневник в одном приложении.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PROGRAM_SECTIONS.forEach { section ->
                Column(modifier = Modifier.padding(top = 4.dp)) {
                    Text(
                        section.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        section.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}


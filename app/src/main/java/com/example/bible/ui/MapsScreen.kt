package com.example.bible.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import com.example.bible.R
import com.example.bible.data.BibleMapCategory
import com.example.bible.data.BibleMapDefinition
import com.example.bible.data.BibleMapsCatalog
import com.example.bible.data.BibleMapsStorage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapsScreen(
    onBack: () -> Unit,
) {
    KeepScreenOnEffect()
    val context = LocalContext.current
    val downloadErrorText = stringResource(R.string.maps_download_error)
    val imageLoader = remember(context) {
        ImageLoader.Builder(context)
            .components {
                add(SvgDecoder.Factory())
            }
            .build()
    }
    var tab by remember { mutableIntStateOf(0) }
    val category = if (tab == 0) BibleMapCategory.OLD_TESTAMENT else BibleMapCategory.NEW_TESTAMENT
    val items = remember(category) { BibleMapsCatalog.byCategory(category) }
    var refreshTick by remember { mutableIntStateOf(0) }
    var viewerMap by remember { mutableStateOf<BibleMapDefinition?>(null) }
    val scope = rememberCoroutineScope()
    var batchBusy by remember { mutableStateOf(false) }
    var itemLoading by remember { mutableStateOf<String?>(null) }
    var batchError by remember { mutableStateOf<String?>(null) }

    fun isOffline(def: BibleMapDefinition): Boolean {
        refreshTick // read dep
        return BibleMapsStorage.isCached(context, def)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.maps_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            TabRow(selectedTabIndex = tab) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text(stringResource(R.string.maps_tab_ot)) },
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text(stringResource(R.string.maps_tab_nt)) },
                )
            }
            Text(
                stringResource(R.string.maps_sources_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                FilledTonalButton(
                    onClick = {
                        if (batchBusy) return@FilledTonalButton
                        batchError = null
                        batchBusy = true
                        scope.launch {
                            try {
                                for (def in items) {
                                    itemLoading = def.id
                                    BibleMapsStorage.download(context.applicationContext, def)
                                }
                                refreshTick++
                            } catch (e: Exception) {
                                batchError = e.message ?: downloadErrorText
                            } finally {
                                itemLoading = null
                                batchBusy = false
                            }
                        }
                    },
                    enabled = !batchBusy && items.isNotEmpty(),
                ) {
                    if (batchBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.size(8.dp))
                    } else {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                    }
                    Text(stringResource(R.string.maps_download_all_tab))
                }
            }
            batchError?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(16.dp),
            ) {
                items(items, key = { it.id }) { def ->
                    val thumbModel = remember(def.id, refreshTick) {
                        if (BibleMapsStorage.isCached(context, def)) BibleMapsStorage.localFile(context, def)
                        else def.remoteUrl
                    }
                    MapListCard(
                        imageLoader = imageLoader,
                        def = def,
                        imageModel = thumbModel,
                        isOffline = isOffline(def),
                        isLoading = itemLoading == def.id,
                        onOpen = { viewerMap = def },
                        onDownload = {
                            scope.launch {
                                itemLoading = def.id
                                batchError = null
                                try {
                                    BibleMapsStorage.download(context.applicationContext, def)
                                    refreshTick++
                                } catch (e: Exception) {
                                    batchError = e.message ?: downloadErrorText
                                } finally {
                                    itemLoading = null
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    viewerMap?.let { def ->
        val model = remember(def.id, refreshTick) {
            if (BibleMapsStorage.isCached(context, def)) BibleMapsStorage.localFile(context, def)
            else def.remoteUrl
        }
        Dialog(
            onDismissRequest = { viewerMap = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
            ) {
                ZoomableAsyncImage(
                    imageLoader = imageLoader,
                    model = model,
                    contentDescription = def.titleRu,
                    modifier = Modifier.fillMaxSize(),
                )
                TextButton(
                    onClick = { viewerMap = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                ) {
                    Text(stringResource(R.string.maps_close_viewer), color = Color.White)
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(12.dp),
                ) {
                    Text(
                        stringResource(R.string.maps_zoom_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        def.titleRu,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

private const val MAP_ZOOM_MIN = 0.5f
private const val MAP_ZOOM_MAX = 6f
private const val MAP_PAN_LIMIT_PX = 6000f

@Composable
private fun ZoomableAsyncImage(
    imageLoader: ImageLoader,
    model: Any,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    fun clampOffset(o: Offset): Offset = Offset(
        o.x.coerceIn(-MAP_PAN_LIMIT_PX, MAP_PAN_LIMIT_PX),
        o.y.coerceIn(-MAP_PAN_LIMIT_PX, MAP_PAN_LIMIT_PX),
    )

    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(MAP_ZOOM_MIN, MAP_ZOOM_MAX)
        offset = clampOffset(offset + panChange)
    }

    Box(modifier = modifier) {
        AsyncImage(
            model = model,
            imageLoader = imageLoader,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
                .transformable(
                    state = transformableState,
                    lockRotationOnZoomPan = true,
                ),
        )
        TextButton(
            onClick = {
                scale = 1f
                offset = Offset.Zero
            },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp),
        ) {
            Text(stringResource(R.string.maps_reset_zoom), color = Color.White)
        }
    }
}

@Composable
private fun MapListCard(
    imageLoader: ImageLoader,
    def: BibleMapDefinition,
    imageModel: Any,
    isOffline: Boolean,
    isLoading: Boolean,
    onOpen: () -> Unit,
    onDownload: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            AsyncImage(
                model = imageModel,
                imageLoader = imageLoader,
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
            )
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Map, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(8.dp))
                Text(
                    def.titleRu,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                def.attributionRu,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (isOffline) stringResource(R.string.maps_saved_offline)
                    else stringResource(R.string.maps_online_only),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isOffline) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FilledTonalButton(
                    onClick = onDownload,
                    enabled = !isLoading,
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.size(8.dp))
                    }
                    Text(stringResource(R.string.maps_download_one))
                }
            }
        }
    }
}

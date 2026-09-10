package com.example.bible.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.bible.R
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

private const val YEAR_MIN = 1900

/** Однозначный светлый цвет строк барабана (виден на тёмном фоне диалога). */
private val WheelTextLight = Color(0xFFF0F0F0)

private fun clampYmd(y: Int, m: Int, d: Int): Triple<Int, Int, Int> {
    val ym = YearMonth.of(y, m.coerceIn(1, 12))
    val maxDay = ym.lengthOfMonth()
    val dayClamped = d.coerceIn(1, maxDay)
    return Triple(ym.year, ym.monthValue, dayClamped)
}

private fun LazyListLayoutInfo.indexNearestViewportCenter(defaultIndex: Int, count: Int): Int {
    if (count <= 0) return 0
    val visible = visibleItemsInfo
    if (visible.isEmpty()) return defaultIndex.coerceIn(0, count - 1)
    val vh =
        viewportSize.takeIf { it.height != 0 }?.height?.toFloat()
            ?: (viewportEndOffset - viewportStartOffset).takeIf { it > 1 }?.toFloat()
            ?: return defaultIndex.coerceIn(0, count - 1)
    val centerPx = viewportStartOffset + vh / 2f
    val best =
        visible.minByOrNull { item ->
            val mid = item.offset + item.size / 2f
            abs(mid - centerPx)
        }
    return (best?.index ?: defaultIndex).coerceIn(0, count - 1)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VerticalLightSnapWheelIndex(
    count: Int,
    selectedIndex: Int,
    onSelectedIndexCommitted: (Int) -> Unit,
    wheelHeight: Dp,
    itemHeight: Dp,
    modifier: Modifier = Modifier,
    formatLabel: (Int) -> String,
    keySeed: Any = Unit,
) {
    if (count <= 0) return

    val safeIndex = remember(selectedIndex, count, keySeed) { selectedIndex.coerceIn(0, count - 1) }

    val listState = rememberLazyListState()
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val padV = (wheelHeight - itemHeight) / 2
    val onCommitStable = rememberUpdatedState(onSelectedIndexCommitted)

    var lastEmitted by remember(keySeed) { mutableIntStateOf(-1) }
    var syncingFromParent by remember(keySeed, count) { mutableStateOf(true) }

    LaunchedEffect(safeIndex, count, keySeed) {
        if (count <= 0) return@LaunchedEffect
        syncingFromParent = true
        lastEmitted = safeIndex.coerceIn(0, count - 1)
        val target = lastEmitted
        runCatching { listState.scrollToItem(index = target, scrollOffset = 0) }
        delay(96)
        syncingFromParent = false
    }

    LazyColumn(
        modifier =
            modifier
                .height(wheelHeight)
                .fillMaxWidth(),
        state = listState,
        flingBehavior = flingBehavior,
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(vertical = padV),
    ) {
        items(count, key = { it }) { index ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(itemHeight),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    formatLabel(index),
                    color = WheelTextLight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }

    LaunchedEffect(listState, count, keySeed) {
        if (count <= 0) return@LaunchedEffect
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .filter { scrolling -> !scrolling }
            .collect {
                delay(72)
                if (syncingFromParent) return@collect
                val idx = listState.layoutInfo.indexNearestViewportCenter(listState.firstVisibleItemIndex, count)
                val clamped = idx.coerceIn(0, count - 1)
                if (clamped != lastEmitted) {
                    lastEmitted = clamped
                    onCommitStable.value.invoke(clamped)
                }
            }
    }
}

@Composable
fun ContactBirthWheelRow(
    year: Int,
    month: Int,
    day: Int,
    maxYear: Int,
    onYmdChange: (Int, Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val onChangeStable = rememberUpdatedState(onYmdChange)
    val yStable = rememberUpdatedState(year)
    val mStable = rememberUpdatedState(month)
    val dStable = rememberUpdatedState(day)
    val maxYearStable = rememberUpdatedState(maxYear)

    fun emitTriple(ny: Int, nm: Int, nd: Int) {
        val (cy, cm, cd) = clampYmd(ny, nm, nd)
        onChangeStable.value(cy, cm, cd)
    }

    val monthDisplayed =
        remember {
            val loc = Locale.forLanguageTag("ru")
            (1..12).map { ix ->
                Month.of(ix).getDisplayName(TextStyle.SHORT_STANDALONE, loc)
            }
        }

    val yearCount = remember(maxYear) { max(maxYear - YEAR_MIN + 1, 1) }
    val yearIndex = remember(yearCount, year) {
        (year - YEAR_MIN).coerceIn(0, max(yearCount - 1, 0))
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            Modifier
                .weight(1f)
                .padding(end = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.contacts_birth_wheel_year),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            VerticalLightSnapWheelIndex(
                count = yearCount,
                selectedIndex = yearIndex,
                wheelHeight = 164.dp,
                itemHeight = 44.dp,
                modifier = Modifier.fillMaxWidth(),
                formatLabel = { ix -> "${YEAR_MIN + ix}" },
                keySeed = maxYear,
                onSelectedIndexCommitted = { ix ->
                    val ny = YEAR_MIN + ix
                    emitTriple(ny.coerceIn(YEAR_MIN, maxYearStable.value), mStable.value, dStable.value)
                },
            )
        }

        Column(
            Modifier
                .weight(1.15f)
                .padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.contacts_birth_wheel_month),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            VerticalLightSnapWheelIndex(
                count = 12,
                selectedIndex = (month.coerceIn(1, 12) - 1),
                wheelHeight = 164.dp,
                itemHeight = 44.dp,
                modifier = Modifier.fillMaxWidth(),
                formatLabel = { ix -> monthDisplayed[ix] },
                keySeed = "m12",
                onSelectedIndexCommitted = { ix ->
                    emitTriple(yStable.value, (ix + 1).coerceIn(1, 12), dStable.value)
                },
            )
        }

        Column(
            Modifier
                .weight(1f)
                .padding(start = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.contacts_birth_wheel_day),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            val maxDay = YearMonth.of(year, month.coerceIn(1, 12)).lengthOfMonth()
            VerticalLightSnapWheelIndex(
                count = maxDay,
                selectedIndex = (day.coerceIn(1, maxDay) - 1),
                wheelHeight = 164.dp,
                itemHeight = 44.dp,
                modifier = Modifier.fillMaxWidth(),
                formatLabel = { ix -> "${ix + 1}" },
                keySeed = Triple(year, month, maxDay),
                onSelectedIndexCommitted = { ix ->
                    emitTriple(yStable.value, mStable.value, ix + 1)
                },
            )
        }
    }
}

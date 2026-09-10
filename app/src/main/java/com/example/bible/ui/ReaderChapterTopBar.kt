package com.example.bible.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

/** Высота строки инструментов под статус-баром. */
val ReaderTopBarHeight = 48.dp

/** Размер кнопки и значка в шапке читалки. */
val ReaderTopBarIconButtonSize = 36.dp
val ReaderTopBarIconSize = 22.dp

/** Цвета тёмной шапки читалки (включая зону status bar). */
object ReaderTopBarColors {
    val Background = Color(0xFF1C1C1E)
    val Content = Color(0xFFF2F2F7)
    val ContentMuted = Color(0xFFAEAEB2)
    val Accent = Color(0xFF64B5F6)
    val PillBackground = Color(0xFF3A3A3C)
}

/** Светлые системные иконки (часы, батарея) на тёмном фоне шапки. */
@Composable
private fun ReaderTopBarStatusBarEffect(restoreLightStatusBarIcons: Boolean) {
    val view = LocalView.current
    DisposableEffect(restoreLightStatusBarIcons) {
        if (!view.isInEditMode) {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = false
            onDispose {
                controller.isAppearanceLightStatusBars = restoreLightStatusBarIcons
            }
        } else {
            onDispose { }
        }
    }
}

/** Короткий заголовок главы в шапке читалки: «Гал 1:2». */
fun readerChapterTitle(bookId: String, chapterNum: Int, verse: Int = 1): String {
    val abbr = com.example.bible.data.BibleCanon.byId(bookId)?.abbrRu ?: bookId
    return "$abbr $chapterNum:${verse.coerceAtLeast(1)}"
}

/** Компактная шапка главы: тёмный фон + status bar, три зоны в одной строке. */
@Composable
fun ReaderChapterTopBar(
    navigationIcon: @Composable () -> Unit,
    centerContent: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    restoreLightStatusBarIcons: Boolean = true,
    includeStatusBar: Boolean = true,
) {
    if (includeStatusBar) {
        ReaderTopBarStatusBarEffect(restoreLightStatusBarIcons)
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = ReaderTopBarColors.Background,
        shadowElevation = 4.dp,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(ReaderTopBarColors.Background),
        ) {
            if (includeStatusBar) {
                Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            }
            CompositionLocalProvider(LocalContentColor provides ReaderTopBarColors.Content) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ReaderTopBarHeight)
                        .padding(end = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(contentAlignment = Alignment.CenterStart) {
                        navigationIcon()
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 2.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        centerContent()
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                        content = actions,
                    )
                }
            }
        }
    }
}

/** Заголовок главы со стрелками «пред./след.» вплотную к тексту. */
@Composable
fun ReaderChapterNavTitle(
    title: String,
    onTitleClick: () -> Unit,
    onPrevChapter: (() -> Unit)?,
    onNextChapter: (() -> Unit)?,
    prevContentDescription: String,
    nextContentDescription: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Row(
            modifier = Modifier
                .background(
                    color = ReaderTopBarColors.PillBackground,
                    shape = RoundedCornerShape(999.dp),
                )
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            ReaderTopIconButton(
                onClick = { onPrevChapter?.invoke() },
                enabled = onPrevChapter != null,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = prevContentDescription,
                    modifier = Modifier.size(ReaderTopBarIconSize),
                    tint = if (onPrevChapter != null) {
                        ReaderTopBarColors.Accent
                    } else {
                        ReaderTopBarColors.ContentMuted.copy(alpha = 0.35f)
                    },
                )
            }
            Text(
                text = title,
                modifier = Modifier
                    .widthIn(max = 108.dp)
                    .clickable(onClick = onTitleClick)
                    .padding(horizontal = 4.dp),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                color = ReaderTopBarColors.Content,
            )
            ReaderTopIconButton(
                onClick = { onNextChapter?.invoke() },
                enabled = onNextChapter != null,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = nextContentDescription,
                    modifier = Modifier.size(ReaderTopBarIconSize),
                    tint = if (onNextChapter != null) {
                        ReaderTopBarColors.Accent
                    } else {
                        ReaderTopBarColors.ContentMuted.copy(alpha = 0.35f)
                    },
                )
            }
        }
    }
}

@Composable
fun ReaderTopIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showTimemarkBadge: Boolean = false,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.size(ReaderTopBarIconButtonSize),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(ReaderTopBarIconButtonSize),
        ) {
            Box(Modifier.size(ReaderTopBarIconSize), contentAlignment = Alignment.Center) {
                content()
            }
        }
        if (showTimemarkBadge) {
            TimemarkIconBadge()
        }
    }
}

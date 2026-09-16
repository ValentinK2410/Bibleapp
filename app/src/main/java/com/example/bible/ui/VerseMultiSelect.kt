package com.example.bible.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.bible.R

@Stable
class VerseMultiSelectState {
    var selectedVerses by mutableStateOf<Set<Int>?>(null)
        private set

    val isActive: Boolean get() = selectedVerses != null

    val count: Int get() = selectedVerses?.size ?: 0

    fun isSelected(verse: Int): Boolean = selectedVerses?.contains(verse) == true

    fun start(verse: Int) {
        selectedVerses = setOf(verse)
    }

    fun toggle(verse: Int) {
        val current = selectedVerses
        if (current == null) {
            selectedVerses = setOf(verse)
            return
        }
        val next = if (verse in current) current - verse else current + verse
        selectedVerses = next.takeIf { it.isNotEmpty() }
    }

    fun clear() {
        selectedVerses = null
    }
}

@Composable
fun rememberVerseMultiSelectState(): VerseMultiSelectState = remember { VerseMultiSelectState() }

fun verseNumberClick(
    verseNumber: Int,
    multiSelect: VerseMultiSelectState,
    openActions: () -> Unit,
) {
    if (multiSelect.isActive) {
        multiSelect.toggle(verseNumber)
    } else {
        openActions()
    }
}

fun verseNumberLongClick(
    verseNumber: Int,
    multiSelect: VerseMultiSelectState,
    onImageLongPress: (() -> Unit)?,
) {
    if (onImageLongPress != null) {
        onImageLongPress()
    } else {
        multiSelect.start(verseNumber)
    }
}

@Composable
fun Modifier.verseNumberSelectionHighlight(selected: Boolean): Modifier {
    if (!selected) return this
    return this
        .clip(RoundedCornerShape(4.dp))
        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
        .padding(horizontal = 2.dp, vertical = 1.dp)
}

@Composable
fun VerseMultiSelectBottomBar(
    selectedCount: Int,
    onCopy: () -> Unit,
    onCancel: () -> Unit,
    onReflectGigaChat: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
    ) {
        val compactPad = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
        val compactMod = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 36.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(
                onClick = onCancel,
                modifier = compactMod,
                contentPadding = compactPad,
            ) {
                Text(
                    stringResource(R.string.verse_multi_select_cancel),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
            }
            if (onReflectGigaChat != null) {
                TextButton(
                    onClick = onReflectGigaChat,
                    modifier = compactMod,
                    contentPadding = compactPad,
                ) {
                    Text(
                        stringResource(R.string.verse_multi_select_gigachat),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                    )
                }
            }
            TextButton(
                onClick = onCopy,
                modifier = compactMod,
                contentPadding = compactPad,
            ) {
                Text(
                    stringResource(R.string.verse_multi_select_copy, selectedCount),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
            }
        }
    }
}

fun copyVersesToClipboard(
    context: Context,
    bookName: String,
    chapter: Int,
    verseNumbers: Set<Int>,
    verseTextsByNumber: Map<Int, String>,
) {
    if (verseNumbers.isEmpty()) return
    val text = VerseShareText.format(bookName, chapter, verseNumbers, verseTextsByNumber)
    if (text.isBlank()) return
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("verse", text))
    Toast.makeText(context, R.string.verse_copied, Toast.LENGTH_SHORT).show()
}

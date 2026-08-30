package com.example.bible.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bible.R
import com.example.bible.data.DeepSeekPassageScope

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeepSeekVerseDialog(
    viewModel: BibleViewModel,
    target: VerseActionTarget,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    chapterVerseCount: Int = 0,
    chapterVerseTexts: Map<Int, String> = emptyMap(),
) {
    val state by viewModel.deepSeekChat.collectAsStateWithLifecycle()
    var followUp by remember { mutableStateOf("") }
    var scope by remember { mutableStateOf(DeepSeekPassageScope.VERSE) }
    var rangeStart by remember(target.ref.verse) { mutableStateOf(target.ref.verse.toString()) }
    val lastVerseHint = maxOf(
        chapterVerseCount,
        chapterVerseTexts.keys.maxOrNull() ?: 0,
        target.ref.verse,
    )
    var rangeEnd by remember(target.ref.verse, lastVerseHint) {
        mutableStateOf(lastVerseHint.coerceAtLeast(target.ref.verse).toString())
    }

    fun parsedRange(): Pair<Int, Int>? {
        val a = rangeStart.toIntOrNull() ?: return null
        val b = rangeEnd.toIntOrNull() ?: return null
        if (a < 1 || b < 1) return null
        return minOf(a, b) to maxOf(a, b)
    }

    fun ask(selected: DeepSeekPassageScope = scope) {
        val (from, to) = parsedRange() ?: (target.ref.verse to target.ref.verse)
        viewModel.askDeepSeekAboutPassage(
            translation = target.ref.translation,
            bookId = target.ref.bookId,
            bookName = target.bookName,
            chapter = target.ref.chapter,
            verse = target.ref.verse,
            verseText = target.verseText.ifBlank {
                chapterVerseTexts[target.ref.verse].orEmpty()
            },
            scope = selected,
            rangeStart = from,
            rangeEnd = to,
            fallbackChapterTexts = chapterVerseTexts,
        )
    }

    LaunchedEffect(target.ref.toKey(), target.verseText, scope) {
        if (scope == DeepSeekPassageScope.RANGE) return@LaunchedEffect
        ask(scope)
    }

    val title = when (scope) {
        DeepSeekPassageScope.VERSE -> stringResource(
            R.string.deepseek_verse_title,
            target.bookName,
            target.ref.chapter,
            target.ref.verse,
        )
        DeepSeekPassageScope.RANGE -> {
            val (from, to) = parsedRange() ?: (target.ref.verse to target.ref.verse)
            stringResource(
                R.string.deepseek_range_title,
                target.bookName,
                target.ref.chapter,
                from,
                to,
            )
        }
        DeepSeekPassageScope.CHAPTER -> stringResource(
            R.string.deepseek_chapter_title,
            target.bookName,
            target.ref.chapter,
        )
        DeepSeekPassageScope.BOOK -> stringResource(
            R.string.deepseek_book_title,
            target.bookName,
        )
    }

    AlertDialog(
        onDismissRequest = {
            viewModel.clearDeepSeekChat()
            onDismiss()
        },
        title = { Text(title) },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    DeepSeekScopeChip(
                        label = stringResource(R.string.deepseek_scope_verse),
                        selected = scope == DeepSeekPassageScope.VERSE,
                        onClick = { scope = DeepSeekPassageScope.VERSE },
                    )
                    DeepSeekScopeChip(
                        label = stringResource(R.string.deepseek_scope_range),
                        selected = scope == DeepSeekPassageScope.RANGE,
                        onClick = { scope = DeepSeekPassageScope.RANGE },
                    )
                    DeepSeekScopeChip(
                        label = stringResource(R.string.deepseek_scope_chapter),
                        selected = scope == DeepSeekPassageScope.CHAPTER,
                        onClick = { scope = DeepSeekPassageScope.CHAPTER },
                    )
                    DeepSeekScopeChip(
                        label = stringResource(R.string.deepseek_scope_book),
                        selected = scope == DeepSeekPassageScope.BOOK,
                        onClick = { scope = DeepSeekPassageScope.BOOK },
                    )
                }
                if (scope == DeepSeekPassageScope.RANGE) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = rangeStart,
                            onValueChange = { rangeStart = it.filter { ch -> ch.isDigit() }.take(3) },
                            label = { Text(stringResource(R.string.deepseek_range_from)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = rangeEnd,
                            onValueChange = { rangeEnd = it.filter { ch -> ch.isDigit() }.take(3) },
                            label = { Text(stringResource(R.string.deepseek_range_to)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(
                        stringResource(R.string.deepseek_range_hint, lastVerseHint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (scope == DeepSeekPassageScope.BOOK) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.deepseek_book_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
                when {
                    state.needsKey -> Text(stringResource(R.string.deepseek_needs_key))
                    state.loading && state.answer.isBlank() -> CircularProgressIndicator()
                    state.error != null && state.answer.isBlank() ->
                        Text(state.error!!, color = MaterialTheme.colorScheme.error)
                    else -> {
                        if (state.answer.isNotBlank()) {
                            Text(state.answer, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (state.loading) {
                            Spacer(Modifier.height(12.dp))
                            CircularProgressIndicator()
                        }
                        state.error?.takeIf { state.answer.isNotBlank() }?.let { err ->
                            Spacer(Modifier.height(8.dp))
                            Text(err, color = MaterialTheme.colorScheme.error)
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = followUp,
                            onValueChange = { followUp = it },
                            label = { Text(stringResource(R.string.deepseek_follow_up)) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            enabled = !state.loading && !state.needsKey,
                        )
                    }
                }
            }
        },
        confirmButton = {
            when {
                state.needsKey -> TextButton(onClick = onOpenSettings) {
                    Text(stringResource(R.string.deepseek_open_settings))
                }
                else -> {
                    val sendFollow = followUp.isNotBlank()
                    TextButton(
                        onClick = {
                            if (sendFollow) {
                                viewModel.askDeepSeekFollowUp(followUp.trim())
                                followUp = ""
                            } else if (scope == DeepSeekPassageScope.RANGE) {
                                ask(DeepSeekPassageScope.RANGE)
                            }
                        },
                        enabled = !state.loading && (
                            sendFollow ||
                                (scope == DeepSeekPassageScope.RANGE && parsedRange() != null)
                            ),
                    ) {
                        Text(
                            if (sendFollow) {
                                stringResource(R.string.deepseek_send)
                            } else {
                                stringResource(R.string.deepseek_ask)
                            },
                        )
                    }
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    viewModel.clearDeepSeekChat()
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.timemark_close))
            }
        },
    )
}

@Composable
private fun DeepSeekScopeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
    )
}

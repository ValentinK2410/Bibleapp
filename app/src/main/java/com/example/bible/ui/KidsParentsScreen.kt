package com.example.bible.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bible.data.AzbukaProgressRepository
import com.example.bible.data.KidsBibleStories
import com.example.bible.data.KidsChild
import com.example.bible.data.KidsFamilyStore
import com.example.bible.data.kidsStars
import com.example.bible.data.suggestedKidsAgeMode
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun KidsParentsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val family by KidsFamilyStore.state(context).collectAsStateWithLifecycle()
    val repo = remember { AzbukaProgressRepository(context.applicationContext) }
    val azbukaPoints by repo.points.collectAsStateWithLifecycle(initialValue = 0)
    val letters by repo.lettersOpenedCount.collectAsStateWithLifecycle(initialValue = 0)
    val brain by repo.brainRingAnswered.collectAsStateWithLifecycle(initialValue = emptySet())
    var editor by remember { mutableStateOf<KidsChild?>(null) }
    var creating by remember { mutableStateOf(false) }
    var resetId by remember { mutableStateOf<String?>(null) }
    KidsSectionScaffold("Родителям", onBack, calm = true) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ParentHeading(Icons.Filled.ChildCare, "Дети")
            Text("Можно добавить сколько угодно детей. Имя и возраст хранятся только на этом устройстве. Возраст подсказывает режим 3–5 или 6–10.")
            family.children.forEach { child ->
                val progress = family.progressOf(child.id)
                val active = child.id == family.activeChildId
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(14.dp),
                ) {
                    Text(
                        "${child.name}, ${child.age} ${ageWord(child.age)} · ${suggestedKidsAgeMode(child.age).title}" +
                            if (active) " · сейчас играет" else "",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    )
                    Text("Звёзды: ${kidsStars(progress.points)} · истории: ${progress.stories.size} · игры: ${progress.gameWins.values.sum()} · мини-игры: ${progress.miniStars.size} · стихи: ${progress.versesDone.size}")
                    Text("Сегодня в разделе: ${progress.minutesByDay[LocalDate.now().toString()] ?: 0} мин · всего по дням: ${progress.minutesByDay.values.sum()} мин")
                    Row {
                        TextButton(onClick = { scope.launch { KidsFamilyStore.setActiveChild(context, child.id) } }) { Text("Этот играет") }
                        TextButton(onClick = { editor = child }) { Text("Изменить") }
                        TextButton(onClick = { scope.launch { KidsFamilyStore.removeChild(context, child.id) } }) { Text("Удалить") }
                        TextButton(onClick = { resetId = child.id }) { Text("Сбросить") }
                    }
                    progress.feed.take(5).forEach { Text("• $it", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            if (family.children.isEmpty()) {
                Text("Пока никого нет. Прогресс без выбранного ребёнка копится в общей корзине «семья».")
            }
            Button(onClick = { creating = true }) {
                Icon(Icons.Filled.PersonAdd, contentDescription = null)
                Text("  Добавить ребёнка")
            }
            Text("Общие звёзды раздела (как раньше): ${kidsStars(azbukaPoints)}. Букв азбуки: $letters. Вопросов брейн-ринга: ${brain.size}.", fontSize = 13.sp)

            ParentHeading(Icons.Filled.Edit, "Режим")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { scope.launch { KidsFamilyStore.setAgeMode(context, "auto") } }) { Text(if (family.ageMode == "auto") "• По возрасту" else "По возрасту") }
                TextButton(onClick = { scope.launch { KidsFamilyStore.setAgeMode(context, "small") } }) { Text(if (family.ageMode == "small") "• 3–5" else "3–5") }
                TextButton(onClick = { scope.launch { KidsFamilyStore.setAgeMode(context, "big") } }) { Text(if (family.ageMode == "big") "• 6–10" else "6–10") }
            }
            Text("Сейчас: ${family.resolvedMode().title}. Смена с детского хаба тоже закрыта примером.")

            ParentHeading(Icons.Filled.WbTwilight, "Пасха")
            Row {
                TextButton(onClick = { scope.launch { KidsFamilyStore.setEasterCalendar(context, "orthodox") } }) {
                    Text(if (family.easterCalendar == "orthodox") "• Православная" else "Православная")
                }
                TextButton(onClick = { scope.launch { KidsFamilyStore.setEasterCalendar(context, "western") } }) {
                    Text(if (family.easterCalendar == "western") "• Западная" else "Западная")
                }
            }

            ParentHeading(Icons.Filled.Schedule, "Время и вечер")
            var limit by remember(family.timeLimitMinutes) { mutableStateOf(family.timeLimitMinutes.toString()) }
            OutlinedTextField(
                value = limit,
                onValueChange = { limit = it.filter(Char::isDigit).take(3) },
                label = { Text("Мягкий лимит минут в день, 0 — без лимита") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = { scope.launch { KidsFamilyStore.setTimeLimit(context, limit.toIntOrNull() ?: 0) } }) { Text("Сохранить лимит") }
            TextButton(onClick = {
                scope.launch { KidsFamilyStore.setEveningReminder(context, !family.eveningReminder, family.eveningHour) }
            }) { Text(if (family.eveningReminder) "Напоминание о вечерней молитве включено" else "Включить напоминание в разделе") }
            Text("Напоминание срабатывает внутри раздела «Детям», без фоновой службы.", fontSize = 13.sp)

            ParentHeading(Icons.Filled.Bedtime, "Скорость озвучки")
            Row {
                listOf(0f to "Как в режиме", 0.75f to "Медленно", 0.9f to "Обычно", 1.05f to "Быстрее").forEach { (rate, label) ->
                    TextButton(onClick = { scope.launch { KidsFamilyStore.setTtsRate(context, rate) } }) {
                        Text(if (family.ttsRate == rate) "• $label" else label)
                    }
                }
            }

            ParentHeading(Icons.Filled.ChildCare, "Советы к историям")
            KidsBibleStories.all.filter { it.parentTips.isNotEmpty() }.take(8).forEach { story ->
                Text(story.title, fontWeight = FontWeight.Bold)
                story.parentTips.forEach { Text("• $it", fontSize = 14.sp) }
            }
            Text("Остальные советы открываются в карточке истории кнопкой «Как обсудить с ребёнком».", fontSize = 13.sp)

            ParentHeading(Icons.Filled.Edit, "Как устроен раздел")
            Text("Истории лежат в коде рядом с прежними одиннадцатью: так проще не потерять поля возраста, завета и вопросов. Картинки, мелодии и контуры раскрасок — заглушки, пока файлов нет. Всё прогресс-данные остаются на устройстве.")
        }
    }
    if (creating || editor != null) {
        ChildEditorDialog(
            initial = editor,
            onDismiss = {
                creating = false
                editor = null
            },
            onSave = { name, age ->
                scope.launch {
                    if (editor == null) KidsFamilyStore.addChild(context, name, age)
                    else KidsFamilyStore.updateChild(context, editor!!.id, name, age)
                }
                creating = false
                editor = null
            },
        )
    }
    resetId?.let { id ->
        AlertDialog(
            onDismissRequest = { resetId = null },
            title = { Text("Сбросить прогресс?") },
            text = { Text("Истории, звёзды и стихи этого ребёнка очистятся на устройстве.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { KidsFamilyStore.resetProgress(context, id) }
                    resetId = null
                }) { Text("Сбросить") }
            },
            dismissButton = { TextButton(onClick = { resetId = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun ChildEditorDialog(
    initial: KidsChild?,
    onDismiss: () -> Unit,
    onSave: (String, Int) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var age by remember { mutableStateOf(initial?.age?.toString().orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Новый ребёнок" else "Ребёнок") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, label = { Text("Имя") }, singleLine = true)
                OutlinedTextField(
                    value = age,
                    onValueChange = { age = it.filter(Char::isDigit).take(2) },
                    label = { Text("Возраст") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, age.toIntOrNull() ?: 0) },
                enabled = name.isNotBlank(),
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun ParentHeading(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
    Row(
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    }
}

private fun ageWord(age: Int): String {
    val mod10 = age % 10
    val mod100 = age % 100
    return when {
        mod10 == 1 && mod100 != 11 -> "год"
        mod10 in 2..4 && mod100 !in 12..14 -> "года"
        else -> "лет"
    }
}

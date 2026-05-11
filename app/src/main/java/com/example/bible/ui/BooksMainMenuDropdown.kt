package com.example.bible.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Church
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import com.example.bible.R
import com.example.bible.data.BooksMainMenuOrder
import com.example.bible.data.TranslationId
import com.example.bible.data.narratorForTranslation
/**
 * Пункты главного меню экрана книг в порядке [menuOrder] (без «Настройки», переводов и разделителей).
 */
@Composable
fun BooksMainMenuOrderedItems(
    menuOrder: List<String>,
    translation: TranslationId,
    narratorId: String,
    closeMenu: () -> Unit,
    navController: NavHostController,
    onShowTextSizeDialog: () -> Unit,
    onShowBookNarratorPicker: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    menuOrder.forEach { id ->
        when (id) {
            BooksMainMenuOrder.SEARCH -> DropdownMenuItem(
                text = { Text(stringResource(R.string.search_title)) },
                onClick = { closeMenu(); navController.navigate("search") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            )
            BooksMainMenuOrder.BOOKMARKS -> DropdownMenuItem(
                text = { Text(stringResource(R.string.bookmarks_title)) },
                onClick = { closeMenu(); navController.navigate("bookmarks") },
                leadingIcon = { Icon(Icons.Default.BookmarkBorder, contentDescription = null) },
            )
            BooksMainMenuOrder.HISTORY -> DropdownMenuItem(
                text = { Text("История чтения") },
                onClick = { closeMenu(); navController.navigate("history") },
                leadingIcon = { Icon(Icons.Default.History, contentDescription = null) },
            )
            BooksMainMenuOrder.NOTES -> DropdownMenuItem(
                text = { Text("Заметки") },
                onClick = { closeMenu(); navController.navigate("notes") },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
            )
            BooksMainMenuOrder.CONTACTS -> DropdownMenuItem(
                text = { Text(stringResource(R.string.contacts_title)) },
                onClick = { closeMenu(); navController.navigate("app_contacts") },
                leadingIcon = { Icon(Icons.Default.Contacts, contentDescription = null, tint = primary) },
            )
            BooksMainMenuOrder.DUAL -> DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_dual_bible)) },
                onClick = { closeMenu(); navController.navigate("dual") },
                leadingIcon = { Icon(Icons.Default.ViewModule, contentDescription = null) },
            )
            BooksMainMenuOrder.MAPS -> DropdownMenuItem(
                text = { Text(stringResource(R.string.maps_title)) },
                onClick = { closeMenu(); navController.navigate("maps") },
                leadingIcon = { Icon(Icons.Default.Map, contentDescription = null, tint = primary) },
            )
            BooksMainMenuOrder.TRAVEL -> DropdownMenuItem(
                text = { Text(stringResource(R.string.travel_menu)) },
                onClick = { closeMenu(); navController.navigate("my_travels") },
                leadingIcon = { Icon(Icons.Default.Explore, contentDescription = null, tint = primary) },
            )
            BooksMainMenuOrder.GENEALOGY -> DropdownMenuItem(
                text = { Text(stringResource(R.string.genealogy_menu)) },
                onClick = { closeMenu(); navController.navigate("genealogy") },
                leadingIcon = { Icon(Icons.Default.AccountTree, contentDescription = null, tint = primary) },
            )
            BooksMainMenuOrder.OTHER_BOOKS -> DropdownMenuItem(
                text = { Text(stringResource(R.string.other_books_menu)) },
                onClick = { closeMenu(); navController.navigate("other_books") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = primary) },
            )
            BooksMainMenuOrder.STRONGS -> DropdownMenuItem(
                text = { Text(stringResource(R.string.strongs_menu)) },
                onClick = { closeMenu(); navController.navigate("strongs") },
                leadingIcon = { Icon(Icons.Default.FormatListNumbered, contentDescription = null, tint = primary) },
            )
            BooksMainMenuOrder.SEMANTIC_LEXICON -> DropdownMenuItem(
                text = { Text(stringResource(R.string.semantic_lexicon_menu)) },
                onClick = { closeMenu(); navController.navigate("semantic_lexicon") },
                leadingIcon = {
                    Icon(Icons.Default.FormatColorFill, contentDescription = null, tint = primary)
                },
            )
            BooksMainMenuOrder.LANGUAGE_STUDY -> DropdownMenuItem(
                text = { Text(stringResource(R.string.language_study_menu)) },
                onClick = { closeMenu(); navController.navigate("language_study") },
                leadingIcon = { Icon(Icons.Filled.Translate, contentDescription = null, tint = primary) },
            )
            BooksMainMenuOrder.TIMEMARK -> DropdownMenuItem(
                text = { Text(stringResource(R.string.timemark_menu)) },
                onClick = { closeMenu(); navController.navigate("timemark_editor") },
                leadingIcon = {
                    Icon(Icons.Default.Schedule, contentDescription = null, tint = primary)
                },
            )
            BooksMainMenuOrder.MEDIA -> DropdownMenuItem(
                text = { Text("Медиа") },
                onClick = { closeMenu(); navController.navigate("media") },
                leadingIcon = { Icon(Icons.Filled.PermMedia, contentDescription = null, tint = primary) },
            )
            BooksMainMenuOrder.NARRATOR -> DropdownMenuItem(
                text = { Text("Озвучка: ${narratorForTranslation(translation, narratorId).name}") },
                onClick = { closeMenu(); onShowBookNarratorPicker() },
                leadingIcon = { Icon(Icons.Default.Headphones, contentDescription = null, tint = primary) },
            )
            BooksMainMenuOrder.READING_PLAN -> DropdownMenuItem(
                text = { Text("План чтения") },
                onClick = { closeMenu(); navController.navigate("reading_plan") },
                leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null, tint = primary) },
            )
            BooksMainMenuOrder.NETWORK_REGION -> DropdownMenuItem(
                text = { Text(stringResource(R.string.network_region_title)) },
                onClick = { closeMenu(); navController.navigate("network_region") },
                leadingIcon = { Icon(Icons.Default.Public, contentDescription = null, tint = primary) },
            )
            BooksMainMenuOrder.KIDS -> DropdownMenuItem(
                text = { Text("Детям") },
                onClick = { closeMenu(); navController.navigate("kids") },
                leadingIcon = {
                    Icon(Icons.Filled.School, contentDescription = null, tint = primary)
                },
            )
            BooksMainMenuOrder.EXPERIMENT -> DropdownMenuItem(
                text = { Text(stringResource(R.string.experiment_title)) },
                onClick = { closeMenu(); navController.navigate("experiment") },
                leadingIcon = {
                    Icon(Icons.Filled.FlashOn, contentDescription = null, tint = primary)
                },
            )
            BooksMainMenuOrder.TEXT_SIZE -> DropdownMenuItem(
                text = { Text("Размер текста") },
                onClick = { closeMenu(); onShowTextSizeDialog() },
                leadingIcon = { Icon(Icons.Filled.FormatSize, contentDescription = null) },
            )
            BooksMainMenuOrder.MY_CHURCH -> DropdownMenuItem(
                text = { Text(stringResource(R.string.church_my_title)) },
                onClick = { closeMenu(); navController.navigate("my_church") },
                leadingIcon = { Icon(Icons.Filled.Church, contentDescription = null, tint = primary) },
            )
        }
    }
}

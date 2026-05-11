package com.example.bible.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.bible.R

enum class LanguageStudyCode(val routeArg: String, @StringRes val titleRes: Int) {
    ENGLISH("english", R.string.language_study_lang_english),
    IRIT("irit", R.string.language_study_lang_irit),
    GREEK("greek", R.string.language_study_lang_greek),
    ARABIC("arabic", R.string.language_study_lang_arabic),
    ;

    companion object {
        fun parse(routeArg: String): LanguageStudyCode? = entries.find { it.routeArg == routeArg }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageStudyHubScreen(onBack: () -> Unit, onOpenLanguage: (LanguageStudyCode) -> Unit) {
    val scroll = androidx.compose.material3.TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.language_study_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Top,
        ) {
            items(LanguageStudyCode.entries.toList()) { lang ->
                val title = stringResource(lang.titleRes)
                ListItem(
                    headlineContent = { Text(title) },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = null)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenLanguage(lang) },
                )
                HorizontalDivider()
            }
        }
    }
}

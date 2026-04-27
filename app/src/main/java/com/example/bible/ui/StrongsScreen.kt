package com.example.bible.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.bible.R
import com.example.bible.data.StrongsDictionary
import com.example.bible.data.StrongsEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StrongsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val dict = remember { StrongsDictionary(context) }
    var query by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var entry by remember { mutableStateOf<StrongsEntry?>(null) }

    fun runSearch() {
        error = null
        entry = null
        val code = StrongsDictionary.parseUserInput(query)
        if (code == null) {
            error = context.getString(R.string.strongs_invalid_input)
            return
        }
        val e = dict.lookup(code)
        if (e == null) {
            error = context.getString(R.string.strongs_not_found, code)
            return
        }
        entry = e
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.strongs_title)) },
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
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.strongs_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.strongs_search_hint)) },
                singleLine = true,
            )
            OutlinedButton(
                onClick = { runSearch() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.strongs_search_button))
            }
            error?.let { err ->
                Text(
                    err,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            entry?.let { e ->
                Spacer(Modifier.height(4.dp))
                Text(
                    e.code,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                StrongsLexiconSection(entry = e)
            }
        }
    }
}

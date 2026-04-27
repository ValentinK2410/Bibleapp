package com.example.bible.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bible.R
import com.example.bible.data.StrongsEntry

/**
 * Единый блок «словарь Стронга»: оригинальная лемма, транслитерация, значение на русском.
 */
@Composable
fun StrongsLexiconSection(
    entry: StrongsEntry,
    modifier: Modifier = Modifier,
    /** Форма слова в подстрочнике (может отличаться от леммы по огласовкам). */
    originalInVerse: String? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.strongs_section_title),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))

        Text(
            text = stringResource(R.string.strongs_lemma_label),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = entry.lemma.ifBlank { "—" },
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 28.sp,
        )

        if (!originalInVerse.isNullOrBlank() && originalInVerse != entry.lemma) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.strongs_form_in_text, originalInVerse),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontStyle = FontStyle.Italic,
            )
        }

        if (entry.translit.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = entry.translit,
                fontSize = 15.sp,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (entry.pronunciation.isNotBlank()) {
            Text(
                text = "[${entry.pronunciation}]",
                fontSize = 13.sp,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.strongs_number_label),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = entry.code + when {
                entry.code.startsWith("G") -> " (${stringResource(R.string.strongs_lang_greek)})"
                entry.code.startsWith("H") -> " (${stringResource(R.string.strongs_lang_hebrew)})"
                else -> ""
            },
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )

        if (entry.definition.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            StrongsDetailRow(
                label = stringResource(R.string.strongs_definition_ru),
                value = entry.definition,
            )
        }

        if (entry.shouldShowKjvUsageRu() && entry.kjvUsage.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            StrongsDetailRow(
                label = stringResource(R.string.strongs_kjv_shades_ru),
                value = entry.kjvUsage,
            )
        }

        if (entry.origin.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            StrongsDetailRow(
                label = stringResource(R.string.strongs_origin_label),
                value = entry.origin,
            )
        }
    }
}

@Composable
private fun StrongsDetailRow(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceContainerLow,
                RoundedCornerShape(8.dp),
            )
            .padding(12.dp),
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 22.sp,
        )
    }
}

@Composable
fun StrongsNotFoundHint(strongCode: String) {
    Spacer(Modifier.height(12.dp))
    HorizontalDivider()
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.strongs_not_found, strongCode),
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontStyle = FontStyle.Italic,
    )
}

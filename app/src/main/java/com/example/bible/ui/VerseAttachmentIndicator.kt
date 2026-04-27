package com.example.bible.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.bible.R
import com.example.bible.data.AttachmentKind
import com.example.bible.data.VerseAttachment
import com.example.bible.data.VerseAttachmentStore
import com.example.bible.data.VerseRef
import com.example.bible.data.resolveFile

/**
 * Иконка и миниатюры вложений у стиха: при наличии картинок показывается фрагмент изображения;
 * для не-изображений — значок скрепки. Нажатие на миниатюру открывает предпросмотр.
 */
@Composable
fun VerseAttachmentIndicator(
    verseRef: VerseRef,
    modifier: Modifier = Modifier,
    iconSize: Dp = 12.dp,
    thumbSize: Dp = 22.dp,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    thumbBorderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    onImageClick: (VerseAttachment) -> Unit = {},
) {
    val context = LocalContext.current
    val store = remember { VerseAttachmentStore.get(context) }
    val indexTick by store.attachmentIndexVersion.collectAsState()
    val attachments = remember(verseRef, indexTick) { store.listFor(verseRef) }
    if (attachments.isEmpty()) return

    val images = remember(attachments) {
        attachments.filter { it.kind() == AttachmentKind.Image }
    }
    val hasNonImage = remember(attachments) {
        attachments.any { it.kind() != AttachmentKind.Image }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (hasNonImage) {
            Icon(
                imageVector = Icons.Filled.AttachFile,
                contentDescription = stringResource(R.string.verse_attachment_has_file),
                modifier = Modifier.size(iconSize),
                tint = iconTint,
            )
        }
        images.take(3).forEach { att ->
            val file = remember(att.id, indexTick) { att.resolveFile(context) }
            if (!file.isFile) return@forEach
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(file)
                    .crossfade(true)
                    .build(),
                contentDescription = att.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(thumbSize)
                    .clip(RoundedCornerShape(4.dp))
                    .border(0.5.dp, thumbBorderColor, RoundedCornerShape(4.dp))
                    .clickable { onImageClick(att) },
            )
        }
        if (images.size > 3) {
            Text(
                text = "+${images.size - 3}",
                fontSize = 9.sp,
                color = iconTint,
            )
        }
    }
}

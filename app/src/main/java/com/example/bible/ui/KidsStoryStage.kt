package com.example.bible.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.bible.R
import com.example.bible.data.KidsBibleStory

internal fun kidsStoryImage(id: String): Int = when (id) {
    "creation" -> R.drawable.kids_story_creation
    "noah" -> R.drawable.kids_story_noah
    "abraham" -> R.drawable.kids_story_abraham
    "joseph" -> R.drawable.kids_story_joseph
    "moses-sea" -> R.drawable.kids_story_moses_sea
    "david" -> R.drawable.kids_story_david
    "daniel" -> R.drawable.kids_story_daniel
    "jonah" -> R.drawable.kids_story_jonah
    "nativity" -> R.drawable.kids_story_nativity
    "five-loaves" -> R.drawable.kids_story_five_loaves
    "lost-sheep" -> R.drawable.kids_story_lost_sheep
    else -> R.drawable.kids_story_creation
}

/** Картинка конкретного абзаца. У каждой истории пять абзацев, индексы 0–4. */
internal fun kidsStoryParagraphImage(id: String, index: Int): Int {
    val n = index.coerceIn(0, 4)
    return when (id) {
        "creation" -> when (n) {
            0 -> R.drawable.kids_story_creation_0
            1 -> R.drawable.kids_story_creation_1
            2 -> R.drawable.kids_story_creation_2
            3 -> R.drawable.kids_story_creation_3
            else -> R.drawable.kids_story_creation_4
        }
        "noah" -> when (n) {
            0 -> R.drawable.kids_story_noah_0
            1 -> R.drawable.kids_story_noah_1
            2 -> R.drawable.kids_story_noah_2
            3 -> R.drawable.kids_story_noah_3
            else -> R.drawable.kids_story_noah_4
        }
        "abraham" -> when (n) {
            0 -> R.drawable.kids_story_abraham_0
            1 -> R.drawable.kids_story_abraham_1
            2 -> R.drawable.kids_story_abraham_2
            3 -> R.drawable.kids_story_abraham_3
            else -> R.drawable.kids_story_abraham_4
        }
        "joseph" -> when (n) {
            0 -> R.drawable.kids_story_joseph_0
            1 -> R.drawable.kids_story_joseph_1
            2 -> R.drawable.kids_story_joseph_2
            3 -> R.drawable.kids_story_joseph_3
            else -> R.drawable.kids_story_joseph_4
        }
        "moses-sea" -> when (n) {
            0 -> R.drawable.kids_story_moses_sea_0
            1 -> R.drawable.kids_story_moses_sea_1
            2 -> R.drawable.kids_story_moses_sea_2
            3 -> R.drawable.kids_story_moses_sea_3
            else -> R.drawable.kids_story_moses_sea_4
        }
        "david" -> when (n) {
            0 -> R.drawable.kids_story_david_0
            1 -> R.drawable.kids_story_david_1
            2 -> R.drawable.kids_story_david_2
            3 -> R.drawable.kids_story_david_3
            else -> R.drawable.kids_story_david_4
        }
        "daniel" -> when (n) {
            0 -> R.drawable.kids_story_daniel_0
            1 -> R.drawable.kids_story_daniel_1
            2 -> R.drawable.kids_story_daniel_2
            3 -> R.drawable.kids_story_daniel_3
            else -> R.drawable.kids_story_daniel_4
        }
        "jonah" -> when (n) {
            0 -> R.drawable.kids_story_jonah_0
            1 -> R.drawable.kids_story_jonah_1
            2 -> R.drawable.kids_story_jonah_2
            3 -> R.drawable.kids_story_jonah_3
            else -> R.drawable.kids_story_jonah_4
        }
        "nativity" -> when (n) {
            0 -> R.drawable.kids_story_nativity_0
            1 -> R.drawable.kids_story_nativity_1
            2 -> R.drawable.kids_story_nativity_2
            3 -> R.drawable.kids_story_nativity_3
            else -> R.drawable.kids_story_nativity_4
        }
        "five-loaves" -> when (n) {
            0 -> R.drawable.kids_story_five_loaves_0
            1 -> R.drawable.kids_story_five_loaves_1
            2 -> R.drawable.kids_story_five_loaves_2
            3 -> R.drawable.kids_story_five_loaves_3
            else -> R.drawable.kids_story_five_loaves_4
        }
        "lost-sheep" -> when (n) {
            0 -> R.drawable.kids_story_lost_sheep_0
            1 -> R.drawable.kids_story_lost_sheep_1
            2 -> R.drawable.kids_story_lost_sheep_2
            3 -> R.drawable.kids_story_lost_sheep_3
            else -> R.drawable.kids_story_lost_sheep_4
        }
        else -> R.drawable.kids_story_creation
    }
}

/**
 * Картинка абзаца. При смене абзаца во время чтения одна иллюстрация плавно переходит в следующую.
 */
@Composable
internal fun KidsStoryStage(
    story: KidsBibleStory,
    sceneIndex: Int,
    lessonMode: Boolean,
    modifier: Modifier = Modifier,
) {
    val index = if (lessonMode) {
        story.paragraphs.lastIndex.coerceAtLeast(0)
    } else {
        sceneIndex.coerceIn(0, story.paragraphs.lastIndex.coerceAtLeast(0))
    }
    Crossfade(
        targetState = index,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "story-picture",
        modifier = modifier.clipToBounds(),
    ) { frame ->
        Image(
            painter = painterResource(kidsStoryParagraphImage(story.id, frame)),
            contentDescription = story.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

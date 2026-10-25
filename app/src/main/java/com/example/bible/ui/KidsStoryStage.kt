package com.example.bible.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
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
    else -> newStoryCover(id)
}

/**
 * Картинка сцены. У старых историй пять кадров, лишний индекс берёт последний.
 * У новых историй кадра может не быть — тогда 0 и на экране остаётся заглушка.
 */
internal fun kidsStoryParagraphImage(id: String, index: Int): Int {
    if (index < 0) return 0
    val n = index
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
        else -> newStoryFrame(id, index)
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
    sceneCount: Int,
    modifier: Modifier = Modifier,
) {
    val last = (sceneCount - 1).coerceAtLeast(0)
    val index = if (lessonMode) last else sceneIndex.coerceIn(0, last)
    Crossfade(
        targetState = index,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "story-picture",
        modifier = modifier.clipToBounds(),
    ) { frame ->
        val image = kidsStoryParagraphImage(story.id, frame)
        if (image != 0) {
            Image(
                painter = painterResource(image),
                contentDescription = story.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            KidsStoryPlaceholder(
                emoji = story.emoji,
                gradient = story.gradient.map { Color(it) },
                caption = "Иллюстрация скоро · kids_story_${story.id}_$frame.webp",
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
internal fun KidsStoryPlaceholder(
    emoji: String,
    gradient: List<Color>,
    caption: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.background(Brush.linearGradient(gradient)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, fontSize = 64.sp)
            Text(
                caption,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}
internal fun newStoryCover(id: String): Int = when (id) {
        "adam_eve" -> R.drawable.kids_story_adam_eve
        "annunciation" -> R.drawable.kids_story_annunciation
        "babel" -> R.drawable.kids_story_babel
        "baby_moses" -> R.drawable.kids_story_baby_moses
        "baptism" -> R.drawable.kids_story_baptism
        "bartimaeus" -> R.drawable.kids_story_bartimaeus
        "boy_jesus" -> R.drawable.kids_story_boy_jesus
        "burning_bush" -> R.drawable.kids_story_burning_bush
        "cana" -> R.drawable.kids_story_cana
        "commandments" -> R.drawable.kids_story_commandments
        "easter" -> R.drawable.kids_story_easter
        "elijah_ravens" -> R.drawable.kids_story_elijah_ravens
        "esther" -> R.drawable.kids_story_esther
        "fishermen" -> R.drawable.kids_story_fishermen
        "gideon" -> R.drawable.kids_story_gideon
        "jairus" -> R.drawable.kids_story_jairus
        "jericho" -> R.drawable.kids_story_jericho
        "jesus_children" -> R.drawable.kids_story_jesus_children
        "naaman" -> R.drawable.kids_story_naaman
        "paralytic" -> R.drawable.kids_story_paralytic
        "pentecost" -> R.drawable.kids_story_pentecost
        "peter_prison" -> R.drawable.kids_story_peter_prison
        "prodigal" -> R.drawable.kids_story_prodigal
        "ruth" -> R.drawable.kids_story_ruth
        "samaritan" -> R.drawable.kids_story_samaritan
        "samuel" -> R.drawable.kids_story_samuel
        "saul_paul" -> R.drawable.kids_story_saul_paul
        "storm" -> R.drawable.kids_story_storm
        "walk_water" -> R.drawable.kids_story_walk_water
        "zacchaeus" -> R.drawable.kids_story_zacchaeus
    else -> 0
}

internal fun newStoryFrame(id: String, index: Int): Int = when (id) {
        "adam_eve" -> when (index) {
            0 -> R.drawable.kids_story_adam_eve_0
            1 -> R.drawable.kids_story_adam_eve_1
            else -> 0
        }
        "annunciation" -> when (index) {
            0 -> R.drawable.kids_story_annunciation_0
            1 -> R.drawable.kids_story_annunciation_1
            else -> 0
        }
        "babel" -> when (index) {
            0 -> R.drawable.kids_story_babel_0
            1 -> R.drawable.kids_story_babel_1
            else -> 0
        }
        "baby_moses" -> when (index) {
            0 -> R.drawable.kids_story_baby_moses_0
            1 -> R.drawable.kids_story_baby_moses_1
            else -> 0
        }
        "baptism" -> when (index) {
            0 -> R.drawable.kids_story_baptism_0
            1 -> R.drawable.kids_story_baptism_1
            else -> 0
        }
        "bartimaeus" -> when (index) {
            0 -> R.drawable.kids_story_bartimaeus_0
            1 -> R.drawable.kids_story_bartimaeus_1
            else -> 0
        }
        "boy_jesus" -> when (index) {
            0 -> R.drawable.kids_story_boy_jesus_0
            1 -> R.drawable.kids_story_boy_jesus_1
            else -> 0
        }
        "burning_bush" -> when (index) {
            0 -> R.drawable.kids_story_burning_bush_0
            1 -> R.drawable.kids_story_burning_bush_1
            else -> 0
        }
        "cana" -> when (index) {
            0 -> R.drawable.kids_story_cana_0
            1 -> R.drawable.kids_story_cana_1
            else -> 0
        }
        "commandments" -> when (index) {
            0 -> R.drawable.kids_story_commandments_0
            1 -> R.drawable.kids_story_commandments_1
            else -> 0
        }
        "easter" -> when (index) {
            0 -> R.drawable.kids_story_easter_0
            1 -> R.drawable.kids_story_easter_1
            2 -> R.drawable.kids_story_easter_2
            3 -> R.drawable.kids_story_easter_3
            4 -> R.drawable.kids_story_easter_4
            5 -> R.drawable.kids_story_easter_5
            6 -> R.drawable.kids_story_easter_6
            7 -> R.drawable.kids_story_easter_7
            8 -> R.drawable.kids_story_easter_8
            9 -> R.drawable.kids_story_easter_9
            10 -> R.drawable.kids_story_easter_10
            11 -> R.drawable.kids_story_easter_11
            else -> 0
        }
        "elijah_ravens" -> when (index) {
            0 -> R.drawable.kids_story_elijah_ravens_0
            1 -> R.drawable.kids_story_elijah_ravens_1
            else -> 0
        }
        "esther" -> when (index) {
            0 -> R.drawable.kids_story_esther_0
            1 -> R.drawable.kids_story_esther_1
            else -> 0
        }
        "fishermen" -> when (index) {
            0 -> R.drawable.kids_story_fishermen_0
            1 -> R.drawable.kids_story_fishermen_1
            else -> 0
        }
        "gideon" -> when (index) {
            0 -> R.drawable.kids_story_gideon_0
            1 -> R.drawable.kids_story_gideon_1
            else -> 0
        }
        "jairus" -> when (index) {
            0 -> R.drawable.kids_story_jairus_0
            1 -> R.drawable.kids_story_jairus_1
            else -> 0
        }
        "jericho" -> when (index) {
            0 -> R.drawable.kids_story_jericho_0
            1 -> R.drawable.kids_story_jericho_1
            else -> 0
        }
        "jesus_children" -> when (index) {
            0 -> R.drawable.kids_story_jesus_children_0
            1 -> R.drawable.kids_story_jesus_children_1
            else -> 0
        }
        "naaman" -> when (index) {
            0 -> R.drawable.kids_story_naaman_0
            1 -> R.drawable.kids_story_naaman_1
            else -> 0
        }
        "paralytic" -> when (index) {
            0 -> R.drawable.kids_story_paralytic_0
            1 -> R.drawable.kids_story_paralytic_1
            else -> 0
        }
        "pentecost" -> when (index) {
            0 -> R.drawable.kids_story_pentecost_0
            1 -> R.drawable.kids_story_pentecost_1
            else -> 0
        }
        "peter_prison" -> when (index) {
            0 -> R.drawable.kids_story_peter_prison_0
            1 -> R.drawable.kids_story_peter_prison_1
            else -> 0
        }
        "prodigal" -> when (index) {
            0 -> R.drawable.kids_story_prodigal_0
            else -> 0
        }
        "ruth" -> when (index) {
            0 -> R.drawable.kids_story_ruth_0
            1 -> R.drawable.kids_story_ruth_1
            else -> 0
        }
        "samaritan" -> when (index) {
            0 -> R.drawable.kids_story_samaritan_0
            else -> 0
        }
        "samuel" -> when (index) {
            0 -> R.drawable.kids_story_samuel_0
            1 -> R.drawable.kids_story_samuel_1
            else -> 0
        }
        "saul_paul" -> when (index) {
            0 -> R.drawable.kids_story_saul_paul_0
            1 -> R.drawable.kids_story_saul_paul_1
            else -> 0
        }
        "storm" -> when (index) {
            0 -> R.drawable.kids_story_storm_0
            1 -> R.drawable.kids_story_storm_1
            else -> 0
        }
        "walk_water" -> when (index) {
            0 -> R.drawable.kids_story_walk_water_0
            else -> 0
        }
        "zacchaeus" -> when (index) {
            0 -> R.drawable.kids_story_zacchaeus_0
            1 -> R.drawable.kids_story_zacchaeus_1
            else -> 0
        }
    else -> 0
}

internal fun kidsHeroImage(id: String): Int = when (id) {
    "adam" -> R.drawable.kids_hero_adam
    "noah" -> R.drawable.kids_hero_noah
    "abraham" -> R.drawable.kids_hero_abraham
    "joseph" -> R.drawable.kids_hero_joseph
    "moses" -> R.drawable.kids_hero_moses
    "ruth" -> R.drawable.kids_hero_ruth
    "samuel" -> R.drawable.kids_hero_samuel
    "david" -> R.drawable.kids_hero_david
    "elijah" -> R.drawable.kids_hero_elijah
    "esther" -> R.drawable.kids_hero_esther
    "daniel" -> R.drawable.kids_hero_daniel
    "jonah" -> R.drawable.kids_hero_jonah
    "mary" -> R.drawable.kids_hero_mary
    "peter" -> R.drawable.kids_hero_peter
    "zacchaeus" -> R.drawable.kids_hero_zacchaeus
    "paul" -> R.drawable.kids_hero_paul
    else -> 0
}

internal fun kidsPrayerImage(id: String): Int = when (id) {
    "morning" -> R.drawable.kids_prayer_morning
    "evening" -> R.drawable.kids_prayer_evening
    "meal" -> R.drawable.kids_prayer_meal
    "thanks" -> R.drawable.kids_prayer_thanks
    "our_father" -> R.drawable.kids_prayer_our_father
    else -> 0
}

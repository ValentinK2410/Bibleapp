package com.example.bible.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bible.R
import com.example.bible.data.KidsAgeMode
import com.example.bible.data.KidsBibleMap
import com.example.bible.data.KidsBibleStories
import com.example.bible.data.KidsFamilyStore
import com.example.bible.data.KidsMapPlace
import kotlin.math.hypot

/** Картинка kids_map_lands — 768×1024. Точки считаются внутри вписанного рисунка, без обрезки краёв. */
private const val MapImageAspect = 768f / 1024f

private fun mapPicture(width: Float, height: Float): Offset {
    if (width <= 0f || height <= 0f) return Offset.Zero
    val box = width / height
    val drawW: Float
    val drawH: Float
    if (box > MapImageAspect) {
        drawH = height
        drawW = height * MapImageAspect
    } else {
        drawW = width
        drawH = width / MapImageAspect
    }
    return Offset((width - drawW) / 2f, (height - drawH) / 2f)
}

private fun mapPoint(place: KidsMapPlace, width: Float, height: Float): Offset {
    val origin = mapPicture(width, height)
    val box = width / height
    val drawW = if (box > MapImageAspect) height * MapImageAspect else width
    val drawH = if (box > MapImageAspect) height else width / MapImageAspect
    return Offset(origin.x + place.x * drawW, origin.y + place.y * drawH)
}

@Composable
fun KidsMapScreen(onBack: () -> Unit, onOpenStory: (String) -> Unit) {
    val context = LocalContext.current
    val family by KidsFamilyStore.state(context).collectAsStateWithLifecycle()
    val young = family.resolvedMode() == KidsAgeMode.SMALL
    val read = family.activeProgress().stories
    val speak = rememberKidsSpeaker(0.9f)
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var selected by remember { mutableStateOf<KidsMapPlace?>(null) }
    var quiz by remember { mutableStateOf<String?>(null) }
    KidsSectionScaffold("Карта библейских земель", onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                "Можно двигать и увеличивать. Точки стоят рядом с городами, карта примерная.",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                fontSize = 13.sp,
            )
            if (young) {
                Text(
                    "Где родился Иисус? Нажми на город.",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    fontWeight = FontWeight.Bold,
                )
                quiz?.let { Text(it, modifier = Modifier.padding(horizontal = 16.dp), color = Color(0xFF15803D)) }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFFBAE6FD)),
            ) {
                Image(
                    painter = painterResource(R.drawable.kids_map_lands),
                    contentDescription = "Карта библейских земель",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = pan.x
                            translationY = pan.y
                        },
                )
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = pan.x
                            translationY = pan.y
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, panChange, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 3f)
                                pan += panChange
                            }
                        }
                        .pointerInput(read, young) {
                            detectTapGestures { tap ->
                                val place = KidsBibleMap.places.minByOrNull { place ->
                                    val point = mapPoint(place, size.width.toFloat(), size.height.toFloat())
                                    hypot(point.x - tap.x, point.y - tap.y)
                                }
                                val hit = place?.let { mapPoint(it, size.width.toFloat(), size.height.toFloat()) }
                                if (place != null && hit != null && hypot(hit.x - tap.x, hit.y - tap.y) < 70f) {
                                    selected = place
                                    speak("${place.name}. ${place.blurb}")
                                    if (young) {
                                        quiz = if (place.id == KidsBibleMap.whereWasJesusBorn.id) {
                                            "Да! Иисус родился в Вифлееме."
                                        } else {
                                            "Это ${place.name}. Вифлеем в другом месте."
                                        }
                                    }
                                }
                            }
                        },
                ) {
                    if (!young) {
                        KidsBibleMap.routes.forEach { route ->
                            val pts = route.placeIds.mapNotNull { id -> KidsBibleMap.place(id) }
                            for (i in 0 until pts.size - 1) {
                                drawLine(
                                    Color(route.color),
                                    mapPoint(pts[i], size.width, size.height),
                                    mapPoint(pts[i + 1], size.width, size.height),
                                    strokeWidth = 6f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f)),
                                )
                            }
                        }
                    }
                    KidsBibleMap.places.forEach { place ->
                        val visited = place.storyIds.any { it in read }
                        val center = mapPoint(place, size.width, size.height)
                        drawCircle(
                            if (visited) Color(0xFF16A34A) else Color(0xFFEA580C),
                            radius = 16f,
                            center = center,
                        )
                        drawCircle(
                            Color.White,
                            radius = 16f,
                            center = center,
                            style = Stroke(3f),
                        )
                    }
                }
            }
            selected?.let { place ->
                Column(Modifier.padding(16.dp)) {
                    Text("${place.emoji} ${place.name}${if (place.storyIds.any { it in read }) " 🚩" else ""}", fontWeight = FontWeight.Black, fontSize = 22.sp)
                    Text(place.blurb)
                    place.storyIds.mapNotNull { KidsBibleStories.byId(it) }.forEach { story ->
                        Text(
                            "История: ${story.title}",
                            color = Color(0xFF1D4ED8),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp).pointerInput(story.id) {
                                detectTapGestures { onOpenStory(story.id) }
                            },
                        )
                    }
                }
            }
        }
    }
}

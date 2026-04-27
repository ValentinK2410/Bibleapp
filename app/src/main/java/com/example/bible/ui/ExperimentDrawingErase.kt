package com.example.bible.ui

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/** Разбивает полилинию на части вне [rect]; точки внутри rect выбрасываются. */
internal fun splitPolylineOutsideRect(seg: List<Offset>, rect: Rect): List<MutableList<Offset>> {
    val out = mutableListOf<MutableList<Offset>>()
    var chunk = mutableListOf<Offset>()
    for (p in seg) {
        if (rect.contains(p)) {
            if (chunk.size >= 2) out.add(chunk)
            chunk = mutableListOf()
        } else {
            chunk.add(p)
        }
    }
    if (chunk.size >= 2) out.add(chunk)
    if (chunk.size == 1) out.add(chunk)
    return out
}

internal fun eraseStrokesInRect(
    segments: SnapshotStateList<MutableList<Offset>>,
    rect: Rect,
) {
    val rebuilt = mutableListOf<MutableList<Offset>>()
    for (seg in segments) {
        rebuilt.addAll(splitPolylineOutsideRect(seg, rect))
    }
    segments.clear()
    segments.addAll(rebuilt)
}

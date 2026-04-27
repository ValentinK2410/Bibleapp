package com.example.bible.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import androidx.compose.ui.geometry.Offset

/**
 * Переводит точку из локальных координат [anchor] (например [androidx.camera.view.PreviewView])
 * в локальные координаты [root] (обычно корневой [androidx.compose.ui.platform.AndroidComposeView]).
 */
fun offsetInViewToRootLocal(anchor: View, root: View, local: Offset): Offset {
    val a = IntArray(2)
    val r = IntArray(2)
    anchor.getLocationOnScreen(a)
    root.getLocationOnScreen(r)
    return Offset(
        local.x + a[0] - r[0],
        local.y + a[1] - r[1],
    )
}

private fun Context.findActivity(): Activity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * Окно для [dispatchSyntheticTapFromScreen] / drag: не только [Activity.getWindow], а **верхнее**
 * подходящее окно процесса (Compose Dialog, bottom sheet и т.д. — отдельные [Window]).
 * Окно оверлея курсора ([MimicCursorOverlay]) пропускается — иначе касания не доходят до модалок.
 * При сбое рефлексии — [Activity.getWindow] decorView.
 */
@SuppressLint("PrivateApi", "DiscouragedPrivateApi")
internal fun decorViewForSyntheticInput(context: Context): View? {
    val activity = context.findActivity()
    val mainDecor = activity?.window?.decorView
    val skipRoot = MimicCursorOverlay.peekOverlayRootForSyntheticDispatch()
    if (activity == null) return mainDecor
    return try {
        val wmgClass = Class.forName("android.view.WindowManagerGlobal")
        val getInstance = wmgClass.getMethod("getInstance")
        val wmg = getInstance.invoke(null) ?: return mainDecor
        val mRootsField = wmgClass.getDeclaredField("mRoots").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val roots = mRootsField.get(wmg) as? ArrayList<*> ?: return mainDecor
        if (roots.isEmpty()) return mainDecor
        val vriClass = Class.forName("android.view.ViewRootImpl")
        val mViewField = vriClass.getDeclaredField("mView").apply { isAccessible = true }
        for (i in roots.size - 1 downTo 0) {
            val root = roots[i] ?: continue
            val decor = mViewField.get(root) as? View ?: continue
            if (skipRoot != null) {
                when {
                    decor === skipRoot -> continue
                    skipRoot.windowToken != null && skipRoot.windowToken === decor.windowToken -> continue
                }
            }
            return decor
        }
        mainDecor
    } catch (_: Throwable) {
        mainDecor
    }
}

/**
 * Тап в точке превью камеры: перевод в экранные координаты и диспатч в [android.view.Window.getDecorView].
 * Надёжнее, чем слать событие только в [androidx.compose.ui.platform.AndroidComposeView]: иначе часть устройств
 * и слоёв (Surface/Compose) не отрабатывает клик.
 */
fun dispatchSyntheticTapFromPreviewLocal(preview: View, context: Context, localInPreview: Offset) {
    val pl = IntArray(2)
    preview.getLocationOnScreen(pl)
    val screenX = pl[0] + localInPreview.x
    val screenY = pl[1] + localInPreview.y
    dispatchSyntheticTapFromScreen(context, screenX, screenY)
}

/**
 * Тап в точке в **экранных** пикселях (как [android.view.View.getLocationOnScreen] для всего окна).
 * Нужен, когда видимый курсор уже в экранных координатах и не совпадает с «носом» в превью камеры.
 */
fun dispatchSyntheticTapFromScreen(context: Context, screenX: Float, screenY: Float) {
    val decor = decorViewForSyntheticInput(context) ?: return
    val dl = IntArray(2)
    decor.getLocationOnScreen(dl)
    val x = screenX - dl[0]
    val y = screenY - dl[1]
    MimicCursorOverlay.notifyClickPulse()
    dispatchSyntheticTapAt(decor, x, y)
}

/**
 * Имитация короткого касания в точке (x, y) в **локальных** координатах [target].
 * Работает только внутри окна приложения; вызывать с главного потока (или через [View.post]).
 * UP отправляется с небольшой задержкой — так чаще распознаётся как клик в Compose.
 */
fun dispatchSyntheticTapAt(target: View, xInTarget: Float, yInTarget: Float) {
    if (!target.isAttachedToWindow || target.width <= 0 || target.height <= 0) return
    val x = xInTarget.coerceIn(0f, target.width.toFloat() - 1f)
    val y = yInTarget.coerceIn(0f, target.height.toFloat() - 1f)
    target.post {
        if (!target.isAttachedToWindow) return@post
        val downMs = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(
            downMs,
            downMs,
            MotionEvent.ACTION_DOWN,
            x,
            y,
            0,
        )
        down.source = InputDevice.SOURCE_TOUCHSCREEN
        target.dispatchTouchEvent(down)
        down.recycle()
        val h = target.handler
        h.postDelayed({
            if (!target.isAttachedToWindow) return@postDelayed
            val upMs = SystemClock.uptimeMillis()
            val up = MotionEvent.obtain(
                downMs,
                upMs,
                MotionEvent.ACTION_UP,
                x,
                y,
                0,
            )
            up.source = InputDevice.SOURCE_TOUCHSCREEN
            target.dispatchTouchEvent(up)
            up.recycle()
        }, 80L)
    }
}

/** Экранные координаты центра точки, заданной в локальных координатах [preview] (как у [PreviewView]). */
fun previewLocalToScreen(preview: View, local: Offset): Pair<Float, Float> {
    val pl = IntArray(2)
    preview.getLocationOnScreen(pl)
    return Pair(pl[0] + local.x, pl[1] + local.y)
}

private fun screenToDecorLocal(decor: View, screenX: Float, screenY: Float): Pair<Float, Float> {
    val dl = IntArray(2)
    decor.getLocationOnScreen(dl)
    val x = (screenX - dl[0]).coerceIn(0f, decor.width.toFloat() - 1f)
    val y = (screenY - dl[1]).coerceIn(0f, decor.height.toFloat() - 1f)
    return x to y
}

/**
 * Начало «зажатого» касания в экранных координатах: только ACTION_DOWN.
 * Возвращает время DOWN для последующих MOVE/UP ([MotionEvent.getDownTime]); 0 при ошибке.
 */
fun dispatchSyntheticDragDownFromScreen(context: Context, screenX: Float, screenY: Float): Long {
    val decor = decorViewForSyntheticInput(context) ?: return 0L
    if (!decor.isAttachedToWindow || decor.width <= 0 || decor.height <= 0) return 0L
    val (x, y) = screenToDecorLocal(decor, screenX, screenY)
    val downMs = SystemClock.uptimeMillis()
    val down = MotionEvent.obtain(
        downMs,
        downMs,
        MotionEvent.ACTION_DOWN,
        x,
        y,
        0,
    )
    down.source = InputDevice.SOURCE_TOUCHSCREEN
    decor.dispatchTouchEvent(down)
    down.recycle()
    return downMs
}

fun dispatchSyntheticDragMoveFromScreen(
    context: Context,
    screenX: Float,
    screenY: Float,
    downTimeMs: Long,
) {
    if (downTimeMs == 0L) return
    val decor = decorViewForSyntheticInput(context) ?: return
    if (!decor.isAttachedToWindow || decor.width <= 0) return
    val (x, y) = screenToDecorLocal(decor, screenX, screenY)
    val now = SystemClock.uptimeMillis()
    val move = MotionEvent.obtain(
        downTimeMs,
        now,
        MotionEvent.ACTION_MOVE,
        x,
        y,
        0,
    )
    move.source = InputDevice.SOURCE_TOUCHSCREEN
    decor.dispatchTouchEvent(move)
    move.recycle()
}

fun dispatchSyntheticDragUpFromScreen(
    context: Context,
    screenX: Float,
    screenY: Float,
    downTimeMs: Long,
) {
    if (downTimeMs == 0L) return
    val decor = decorViewForSyntheticInput(context) ?: return
    if (!decor.isAttachedToWindow || decor.width <= 0) return
    val (x, y) = screenToDecorLocal(decor, screenX, screenY)
    val now = SystemClock.uptimeMillis()
    val up = MotionEvent.obtain(
        downTimeMs,
        now,
        MotionEvent.ACTION_UP,
        x,
        y,
        0,
    )
    up.source = InputDevice.SOURCE_TOUCHSCREEN
    decor.dispatchTouchEvent(up)
    up.recycle()
}

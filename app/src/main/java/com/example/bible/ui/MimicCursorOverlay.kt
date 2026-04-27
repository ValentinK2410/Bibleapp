package com.example.bible.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path as ComposePath
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.example.bible.ui.theme.BibleAppThemePreset
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Состояние для [MimicOverlayDrawView]; обновляется только с главного потока. */
private class MimicOverlayDrawModel {
    var hasCursor: Boolean = false
    var cursorX: Float = 0f
    var cursorY: Float = 0f
    var pointerHalo: Boolean = false
    var hasVelocity: Boolean = false
    var velX0: Float = 0f
    var velY0: Float = 0f
    var velX1: Float = 0f
    var velY1: Float = 0f
    var pulseScale: Float = 1f
    var pulseAlpha: Float = 1f

    fun reset() {
        hasCursor = false
        pointerHalo = false
        hasVelocity = false
        pulseScale = 1f
        pulseAlpha = 1f
    }
}

private class MimicOverlayDrawView(
    context: Context,
    private val model: MimicOverlayDrawModel,
) : View(context) {

    private val density = resources.displayMetrics.density
    private val strokePx = 5f * density
    private val headPx = 22f * density
    private val haloRadiusPx = 30f * density
    private val coreRadiusPx = 9f * density
    private val coreOutlinePx = 2f * density

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCC66BB6A.toInt()
        strokeWidth = strokePx
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.STROKE
    }
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xE643A047.toInt()
        style = Paint.Style.FILL
    }
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x55E53935
        style = Paint.Style.FILL
    }
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE53935.toInt()
        style = Paint.Style.FILL
    }
    private val coreOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = coreOutlinePx
    }

    private var pulseAnimator: AnimatorSet? = null
    private val arrowPath = Path()
    private val ease = PathInterpolator(0.4f, 0f, 0.2f, 1f)

    fun cancelPulseAnimation() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        model.pulseScale = 1f
        model.pulseAlpha = 1f
    }

    fun startClickPulse() {
        cancelPulseAnimation()
        val scaleUp = ValueAnimator.ofFloat(1f, 1.56f).apply {
            duration = 130L
            interpolator = ease
            addUpdateListener {
                model.pulseScale = it.animatedValue as Float
                invalidate()
            }
        }
        val scaleDown = ValueAnimator.ofFloat(1.56f, 1f).apply {
            duration = 165L
            interpolator = ease
            addUpdateListener {
                model.pulseScale = it.animatedValue as Float
                invalidate()
            }
        }
        val alphaDown = ValueAnimator.ofFloat(1f, 0.22f).apply {
            duration = 130L
            interpolator = ease
            addUpdateListener {
                model.pulseAlpha = it.animatedValue as Float
                invalidate()
            }
        }
        val alphaUp = ValueAnimator.ofFloat(0.22f, 1f).apply {
            duration = 165L
            interpolator = ease
            addUpdateListener {
                model.pulseAlpha = it.animatedValue as Float
                invalidate()
            }
        }
        pulseAnimator = AnimatorSet().apply {
            playTogether(
                AnimatorSet().apply { playSequentially(scaleUp, scaleDown) },
                AnimatorSet().apply { playSequentially(alphaDown, alphaUp) },
            )
            addListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        model.pulseScale = 1f
                        model.pulseAlpha = 1f
                        invalidate()
                    }
                },
            )
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (model.hasVelocity) {
            canvas.drawLine(model.velX0, model.velY0, model.velX1, model.velY1, linePaint)
            val ang = atan2(
                (model.velY1 - model.velY0).toDouble(),
                (model.velX1 - model.velX0).toDouble(),
            ).toFloat()
            val wing = 0.52f
            arrowPath.rewind()
            arrowPath.moveTo(model.velX1, model.velY1)
            arrowPath.lineTo(
                model.velX1 - headPx * cos(ang - wing),
                model.velY1 - headPx * sin(ang - wing),
            )
            arrowPath.lineTo(
                model.velX1 - headPx * cos(ang + wing),
                model.velY1 - headPx * sin(ang + wing),
            )
            arrowPath.close()
            canvas.drawPath(arrowPath, headPaint)
        }

        if (!model.hasCursor) return

        val cx = model.cursorX
        val cy = model.cursorY
        val pulseScale = model.pulseScale
        val pulseAlpha = model.pulseAlpha

        val save = canvas.save()
        canvas.translate(cx, cy)
        canvas.scale(pulseScale, pulseScale)
        val alphaSave = (pulseAlpha * 255f).toInt().coerceIn(0, 255)
        if (model.pointerHalo) {
            haloPaint.alpha = (0x55 * pulseAlpha).toInt().coerceIn(0, 255)
            canvas.drawCircle(0f, 0f, haloRadiusPx, haloPaint)
        }
        corePaint.alpha = alphaSave
        coreOutlinePaint.alpha = alphaSave
        canvas.drawCircle(0f, 0f, coreRadiusPx, corePaint)
        canvas.drawCircle(0f, 0f, coreRadiusPx, coreOutlinePaint)
        canvas.restoreToCount(save)
    }
}

/**
 * Плавающий слой с курсором мимики: отдельное окно [TYPE_APPLICATION_OVERLAY] — выше всего содержимого
 * активности, в том числе [androidx.compose.material3.ModalBottomSheet] и диалогов Compose.
 *
 * Рисование — через обычный [View] и [Canvas], без Compose внутри окна оверлея (Compose в отдельном
 * [Window] на части устройств даёт нестабильные падения).
 *
 * Если [Settings.canDrawOverlays] = false, оверлей не создаётся: в [BibleApp] показывается запасной курсор
 * внутри основного [android.view.Window] — он оказывается **под** модальными листами/попапами (они рисуются поверх).
 *
 * Касания не перехватывает ([FLAG_NOT_TOUCHABLE]) — работают обычные тапы. Синтетические события
 * ([dispatchSyntheticTapFromScreen] и drag) направляются в верхнее окно активности (кроме этого оверлея),
 * см. [decorViewForSyntheticInput].
 */
object MimicCursorOverlay {

    private var windowManager: WindowManager? = null
    private var container: FrameLayout? = null
    private var drawView: MimicOverlayDrawView? = null
    private val drawModel = MimicOverlayDrawModel()

    /** Инвалидирует отложенный [attach], если до выполнения поста вызвали [detach]. */
    private var attachSession = 0

    /** Увеличивается при каждом синтетическом «клике» — анимация курсора в оверлее и fallback. */
    internal val clickPulseState = mutableIntStateOf(0)

    fun notifyClickPulse() {
        clickPulseState.intValue++
        drawView?.startClickPulse()
    }

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    internal fun peekOverlayRootForSyntheticDispatch(): View? = container?.rootView

    @Suppress("UNUSED_PARAMETER")
    fun attach(activity: ComponentActivity, isDark: Boolean, preset: BibleAppThemePreset) {
        if (!Settings.canDrawOverlays(activity)) return
        if (activity.isFinishing || activity.isDestroyed) return
        if (container != null) return

        val session = attachSession
        val runAttach = Runnable {
            if (session != attachSession) return@Runnable
            if (container != null) return@Runnable
            if (!Settings.canDrawOverlays(activity)) return@Runnable
            if (activity.isFinishing || activity.isDestroyed) return@Runnable

            val wm = activity.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT,
            )
            params.gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }

            val frame = FrameLayout(activity).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
            val dv = MimicOverlayDrawView(activity, drawModel)
            drawView = dv
            frame.addView(
                dv,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
            runCatching {
                wm.addView(frame, params)
            }.onFailure {
                drawView = null
                frame.removeAllViews()
                return@Runnable
            }
            windowManager = wm
            container = frame
            dv.invalidate()
        }

        val decor = activity.window?.decorView
        if (decor != null) {
            decor.post(runAttach)
        } else {
            Handler(Looper.getMainLooper()).post(runAttach)
        }
    }

    /** Оставлено для совместимости с [BibleApp]; отрисовка оверлея не зависит от темы (фиксированные цвета). */
    @Suppress("UNUSED_PARAMETER")
    fun updateTheme(isDark: Boolean, preset: BibleAppThemePreset) {
        drawView?.invalidate()
    }

    fun updateCursor(screenPosition: Offset?) {
        if (screenPosition == null) {
            drawModel.hasCursor = false
        } else {
            drawModel.hasCursor = true
            drawModel.cursorX = screenPosition.x
            drawModel.cursorY = screenPosition.y
        }
        drawView?.invalidate()
    }

    fun updatePointerPressed(pressed: Boolean) {
        drawModel.pointerHalo = pressed
        drawView?.invalidate()
    }

    fun updateVelocityVector(from: Offset?, to: Offset?) {
        if (from != null && to != null) {
            drawModel.hasVelocity = true
            drawModel.velX0 = from.x
            drawModel.velY0 = from.y
            drawModel.velX1 = to.x
            drawModel.velY1 = to.y
        } else {
            drawModel.hasVelocity = false
        }
        drawView?.invalidate()
    }

    fun detach() {
        attachSession++
        drawView?.cancelPulseAnimation()
        drawView = null
        val wm = windowManager ?: return
        val c = container ?: return
        runCatching { wm.removeView(c) }
        windowManager = null
        container = null
        drawModel.reset()
    }
}

/** Стрелка направления мимики (экранные координаты). Запасной слой в основном окне [BibleApp]. */
@Composable
fun MimicVelocityVectorArrowLayer(
    start: Offset?,
    end: Offset?,
    modifier: Modifier = Modifier,
) {
    if (start == null || end == null) return
    Canvas(modifier) {
        val stroke = 5.dp.toPx()
        val head = 22.dp.toPx()
        drawLine(
            color = Color(0xCC66BB6A),
            start = start,
            end = end,
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        val ang = atan2(
            (end.y - start.y).toDouble(),
            (end.x - start.x).toDouble(),
        ).toFloat()
        val wing = 0.52f
        val path = ComposePath().apply {
            moveTo(end.x, end.y)
            lineTo(end.x - head * cos(ang - wing), end.y - head * sin(ang - wing))
            lineTo(end.x - head * cos(ang + wing), end.y - head * sin(ang + wing))
            close()
        }
        drawPath(path = path, color = Color(0xE643A047))
    }
}

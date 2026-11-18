package com.example.bible.ui.housecare

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintManager
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.bible.data.housecare.CleaningWeekDocument
import com.example.bible.data.housecare.HouseCareData
import com.example.bible.data.housecare.cleaningWeekDocument
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate

/**
 * Кнопка «Распечатать» для выбранной недели.
 * С экрана объекта печатается весь дом, с экрана этажа — комнаты этого этажа.
 */
@Composable
internal fun PrintCleaningWeekButton(
    data: HouseCareData,
    placeId: String,
    weekStart: LocalDate,
    floorId: String? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    FilledTonalButton(
        onClick = {
            HouseCareWeekPrinter.print(
                context,
                data.cleaningWeekDocument(placeId, weekStart, floorId),
            )
        },
        modifier = modifier.fillMaxWidth(),
    ) {
        Icon(
            Icons.Filled.Print,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text("Распечатать")
    }
}

/**
 * Печать HTML-таблицы через [PrintManager].
 *
 * Раньше задание ставилось через [View.post] у WebView, который не был прикреплён к окну.
 * У неприкреплённого View такой Runnable остаётся во внутренней очереди и на телефоне
 * не выполняется никогда: диалог не открывается и никакой ошибки пользователь не видит.
 * Поэтому WebView держим в иерархии окна (за экраном, чтобы не перехватывать нажатия),
 * а печать запускаем с главного [Handler]. Если системной печати нет, пишем PDF и открываем его.
 */
internal object HouseCareWeekPrinter {
    private const val TAG = "HouseCarePrint"
    private const val PAGE_WAIT_MS = 4_000L
    private val mainHandler = Handler(Looper.getMainLooper())
    private val hosts = mutableSetOf<PrintHost>()
    private var preparing = false

    fun print(context: Context, document: CleaningWeekDocument) {
        val activity = context.findActivity()
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            toast(context, "Печать доступна только с экрана приложения")
            return
        }
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { print(activity, document) }
            return
        }
        if (preparing) {
            toast(activity, "Печать уже готовится")
            return
        }
        preparing = true
        try {
            openPrintDialog(activity, document)
        } catch (error: Throwable) {
            preparing = false
            Log.e(TAG, "Не удалось подготовить WebView", error)
            openPdfFallback(activity, document, "Не удалось открыть печать. Открываю PDF.")
        }
    }

    private fun openPrintDialog(activity: Activity, document: CleaningWeekDocument) {
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        if (root == null) {
            preparing = false
            openPdfFallback(activity, document, "Не удалось открыть печать. Открываю PDF.")
            return
        }
        val webView = WebView(activity)
        webView.settings.apply {
            javaScriptEnabled = false
            allowFileAccess = false
            defaultTextEncodingName = "utf-8"
        }
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false
        val container = FrameLayout(activity).apply {
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            // За пределами экрана: WebView прикреплён и рисуется, но не закрывает интерфейс.
            // Прозрачность не ставим: на части прошивок невидимый WebView так и не вызывает onPageFinished.
            translationX = -20000f
            translationY = -20000f
        }
        container.addView(
            webView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        root.addView(container, FrameLayout.LayoutParams(1280, 720))
        val host = PrintHost(webView, container)
        hosts += host

        var started = false
        fun giveUp(message: String) {
            if (started) return
            started = true
            preparing = false
            release(host)
            openPdfFallback(activity, document, message)
        }
        fun beginPrint() {
            if (started) return
            started = true
            mainHandler.post {
                if (activity.isFinishing || activity.isDestroyed) {
                    preparing = false
                    release(host)
                    return@post
                }
                val printManager = activity.getSystemService(PrintManager::class.java)
                if (printManager == null) {
                    preparing = false
                    release(host)
                    openPdfFallback(activity, document, "Печать на этом устройстве недоступна. Открываю PDF.")
                    return@post
                }
                try {
                    val adapter = ReleasingPrintAdapter(webView.createPrintDocumentAdapter(document.jobName)) {
                        release(host)
                    }
                    printManager.print(document.jobName, adapter, landscapeA4())
                    preparing = false
                } catch (error: Throwable) {
                    preparing = false
                    Log.e(TAG, "PrintManager не открыл диалог", error)
                    release(host)
                    openPdfFallback(activity, document, "Не удалось открыть печать. Открываю PDF.")
                }
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                // loadData часто сообщает about:blank. Это и есть готовый лист, его нельзя пропускать.
                beginPrint()
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError,
            ) {
                if (!request.isForMainFrame) return
                Log.e(TAG, "WebView не загрузил лист: ${error.description}")
                mainHandler.post {
                    giveUp("Не удалось подготовить лист. Открываю PDF.")
                }
            }
        }
        mainHandler.postDelayed({
            giveUp("Не удалось подготовить лист. Открываю PDF.")
        }, PAGE_WAIT_MS)
        try {
            webView.loadDataWithBaseURL(
                "https://appassets.androidplatform.net/",
                document.toHtml(),
                "text/html",
                "UTF-8",
                null,
            )
        } catch (error: Throwable) {
            Log.e(TAG, "WebView не принял лист", error)
            giveUp("Не удалось подготовить лист. Открываю PDF.")
        }
    }

    private fun landscapeA4(): PrintAttributes = PrintAttributes.Builder()
        .setMediaSize(PrintAttributes.MediaSize.ISO_A4.asLandscape())
        .setResolution(PrintAttributes.Resolution("housecare-a4", "A4", 300, 300))
        .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
        .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
        .build()

    private fun openPdfFallback(activity: Activity, document: CleaningWeekDocument, reason: String) {
        if (activity.isFinishing || activity.isDestroyed) return
        try {
            val dir = File(activity.cacheDir, "housecare-print").apply { mkdirs() }
            val file = File(dir, "housecare-week.pdf")
            FileOutputStream(file).use { writeCleaningWeekPdf(document, it) }
            val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.provider", file)
            val view = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newRawUri("Таблица уборки", uri)
            }
            val chooser = Intent.createChooser(view, "Открыть таблицу уборки").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.startActivity(chooser)
            toast(activity, reason)
        } catch (_: ActivityNotFoundException) {
            toast(activity, "Печать недоступна, и нет приложения для PDF")
        } catch (error: Throwable) {
            Log.e(TAG, "Не удалось записать PDF", error)
            toast(activity, "Не удалось подготовить таблицу")
        }
    }

    private fun release(host: PrintHost) {
        if (!hosts.remove(host)) return
        mainHandler.post {
            (host.container.parent as? ViewGroup)?.removeView(host.container)
            runCatching { host.webView.destroy() }
        }
    }

    private fun toast(context: Context, text: String) {
        val show = { Toast.makeText(context, text, Toast.LENGTH_LONG).show() }
        if (Looper.myLooper() == Looper.getMainLooper()) show() else mainHandler.post { show() }
    }

    private fun Context.findActivity(): Activity? {
        var current: Context? = this
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }

    private class PrintHost(val webView: WebView, val container: FrameLayout)
}

/** Отпускает WebView после того, как система закончила или отменила задание печати. */
private class ReleasingPrintAdapter(
    private val delegate: PrintDocumentAdapter,
    private val onFinished: () -> Unit,
) : PrintDocumentAdapter() {
    override fun onStart() = delegate.onStart()

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback,
        extras: Bundle?,
    ) {
        delegate.onLayout(oldAttributes, newAttributes, cancellationSignal, callback, extras)
    }

    override fun onWrite(
        pages: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal,
        callback: WriteResultCallback,
    ) {
        delegate.onWrite(pages, destination, cancellationSignal, callback)
    }

    override fun onFinish() {
        try {
            delegate.onFinish()
        } finally {
            onFinished()
        }
    }
}

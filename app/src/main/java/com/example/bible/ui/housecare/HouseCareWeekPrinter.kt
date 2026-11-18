package com.example.bible.ui.housecare

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintManager
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
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
import com.example.bible.data.housecare.CleaningWeekDocument
import com.example.bible.data.housecare.HouseCareData
import com.example.bible.data.housecare.cleaningWeekDocument
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
 * Печать HTML-таблицы недели через [PrintManager]: альбомный A4, кириллица системным шрифтом WebView.
 * WebView держится, пока системный диалог не закончит запись листа, иначе страница выходит пустой.
 */
internal object HouseCareWeekPrinter {
    private val alive = mutableSetOf<WebView>()

    fun print(context: Context, document: CleaningWeekDocument) {
        val activity = context.findActivity()
        if (activity == null) {
            Toast.makeText(context, "Печать доступна только с экрана приложения", Toast.LENGTH_SHORT).show()
            return
        }
        val webView = WebView(activity)
        alive += webView
        webView.settings.apply {
            javaScriptEnabled = false
            allowFileAccess = false
            defaultTextEncodingName = "utf-8"
        }
        val widthPx = 1920
        val heightPx = 1080
        webView.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY),
        )
        webView.layout(0, 0, widthPx, heightPx)
        var started = false
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                if (started) return
                started = true
                view.post { startPrint(activity, view, document.jobName) }
            }
        }
        webView.loadDataWithBaseURL(null, document.toHtml(), "text/html", "UTF-8", null)
    }

    private fun startPrint(activity: Activity, view: WebView, jobName: String) {
        val printManager = activity.getSystemService(PrintManager::class.java)
        if (printManager == null) {
            Toast.makeText(activity, "Печать на этом устройстве недоступна", Toast.LENGTH_SHORT).show()
            release(view)
            return
        }
        val adapter = ReleasingPrintAdapter(view.createPrintDocumentAdapter(jobName)) {
            release(view)
        }
        val attributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4.asLandscape())
            .setResolution(PrintAttributes.Resolution("housecare-a4", "A4", 300, 300))
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
            .build()
        try {
            printManager.print(jobName, adapter, attributes)
        } catch (_: Exception) {
            Toast.makeText(activity, "Не удалось открыть печать", Toast.LENGTH_SHORT).show()
            release(view)
        }
    }

    private fun release(view: WebView) {
        if (!alive.remove(view)) return
        view.post {
            runCatching { view.destroy() }
        }
    }

    private fun Context.findActivity(): Activity? {
        var current: Context? = this
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }
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

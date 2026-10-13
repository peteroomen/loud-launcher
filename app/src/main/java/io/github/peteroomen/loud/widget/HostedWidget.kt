package io.github.peteroomen.loud.widget

import android.view.View
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/** External RemoteViews retain provider styling; put Loud's themed border and shadow outside. */
@Composable
fun HostedWidget(controller: WidgetHostController, appWidgetId: Int, modifier: Modifier = Modifier) {
    val revision = controller.revision
    val info = remember(controller, appWidgetId, revision) { controller.providerInfo(appWidgetId) }
    key(controller, appWidgetId, info?.provider, info?.profile) {
        AndroidView(modifier = modifier, factory = { context ->
            if (info == null) TextView(context).apply {
                text = "Widget unavailable. Enable its app or work profile, or remove and add the widget again."
                val pad = (16 * resources.displayMetrics.density).toInt()
                setPadding(pad, pad, pad, pad); contentDescription = text
            } else runCatching<View> {
                controller.createView(appWidgetId, info).apply {
                    addOnLayoutChangeListener { view, left, top, right, bottom, _, _, _, _ ->
                        val density = view.resources.displayMetrics.density
                        controller.updateSize(this, ((right - left) / density).toInt(), ((bottom - top) / density).toInt())
                    }
                }
            }.getOrElse { TextView(context).apply {
                text = "Android could not display this widget. Try adding it again."; contentDescription = text
            } }
        })
    }
}

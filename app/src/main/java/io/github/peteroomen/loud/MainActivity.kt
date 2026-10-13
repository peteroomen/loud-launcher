package io.github.peteroomen.loud

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import io.github.peteroomen.loud.model.HomeTile
import io.github.peteroomen.loud.model.HomeTileKind
import io.github.peteroomen.loud.ui.LoudLauncherApp
import io.github.peteroomen.loud.widget.WidgetHostController
import io.github.peteroomen.loud.widget.WidgetSelection
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var launcher: LauncherViewModel
    private lateinit var widgets: WidgetHostController
    private val savingWidgets=mutableSetOf<Int>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        launcher=ViewModelProvider(this)[LauncherViewModel::class.java]
        lifecycle.addObserver(launcher.system)
        widgets=WidgetHostController(this).apply {onMessage=launcher::report;onWidgetReady=::saveWidget}
        lifecycleScope.launch {
            launcher.core.layout.loaded.first {it}
            widgets.reconcileWidgets(launcher.core.layout.tiles.value.mapNotNull {it.config["widgetId"]?.toIntOrNull()}.toSet())
            widgets.pendingReady()?.let(::saveWidget)
        }
        setContent {LoudLauncherApp(launcher,widgets)}
    }
    private fun saveWidget(selection: WidgetSelection) {
        if(!savingWidgets.add(selection.appWidgetId)) return
        lifecycleScope.launch {
            try {
                launcher.core.layout.loaded.first {it}
                if(launcher.core.layout.tiles.value.none {it.config["widgetId"]==selection.appWidgetId.toString()}) {
                    val provider=widgets.installedProviders().providers.firstOrNull {it.providerComponent==selection.providerComponent && it.profileSerial==selection.profileSerial}
                    val rows=((provider?.minHeightDp ?: 180)+93)/94
                    launcher.core.layout.add(HomeTile(kind=HomeTileKind.Widget,columns=4,rows=rows.coerceIn(1,4),config=selection.toTileConfig()))
                }
                widgets.confirmPersisted(selection.appWidgetId)
            } catch(e: Exception) {widgets.cancelPending();launcher.report(e.message ?: "Widget could not save")}
            finally {savingWidgets.remove(selection.appWidgetId)}
        }
    }
    override fun onStart() {super.onStart();if(::widgets.isInitialized) widgets.startListening()}
    override fun onStop() {if(::widgets.isInitialized) widgets.stopListening();super.onStop()}
    @Deprecated("Uses the platform widget host consent flow")
    override fun onActivityResult(requestCode: Int,resultCode: Int,data: Intent?) {
        if(!::widgets.isInitialized || !widgets.onActivityResult(requestCode,resultCode)) super.onActivityResult(requestCode,resultCode,data)
    }
    override fun onNewIntent(intent: Intent) {super.onNewIntent(intent);setIntent(intent);launcher.home()}
    override fun onDestroy() {lifecycle.removeObserver(launcher.system);super.onDestroy()}
}

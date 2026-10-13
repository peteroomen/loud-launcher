package io.github.peteroomen.loud

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.peteroomen.loud.data.AppEntry
import io.github.peteroomen.loud.data.LoudRepositories
import io.github.peteroomen.loud.model.*
import io.github.peteroomen.loud.system.SystemRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

enum class LauncherPage { Live, Apps, Search, Settings, Permissions }
class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    val core = repositories(application)
    val system = SystemRepository(application)
    val page = MutableStateFlow(LauncherPage.Live)
    val query = MutableStateFlow("")
    val edit = MutableStateFlow(false)
    val homeGeneration = MutableStateFlow(0)
    val message = MutableStateFlow<String?>(null)
    fun navigate(destination: LauncherPage) { page.value=destination; if(destination!=LauncherPage.Search) query.value=""; edit.value=false }
    fun home() { page.value=LauncherPage.Live; query.value=""; edit.value=false; homeGeneration.value+=1 }
    fun report(text: String) { message.value=text }
    fun launch(app: AppEntry) { core.apps.launch(app,page.value==LauncherPage.Search).onFailure {report(it.message ?: "App could not open")} }
    fun save(tile: HomeTile) = viewModelScope.launch {runCatching {core.layout.update(tile)}.onFailure {report(it.message ?: "Tile could not save")} }
    fun add(tile: HomeTile) = viewModelScope.launch {runCatching {core.layout.add(tile)}.onFailure {report(it.message ?: "Tile could not add")} }
    fun move(tile: HomeTile,direction: Int) = viewModelScope.launch {
        val ids=core.layout.tiles.value.map {it.id}.toMutableList(); val old=ids.indexOf(tile.id)
        if(old<0 || ids.isEmpty()) return@launch
        val next=(old+direction).coerceIn(0,ids.lastIndex)
        if(old!=next) {ids.removeAt(old);ids.add(next,tile.id);runCatching {core.layout.reorder(ids)}.onFailure {report("Order could not save")} }
    }
    fun settings(transform: (LauncherSettings)->LauncherSettings) = viewModelScope.launch {runCatching {core.settings.update(transform)}.onFailure {report(it.message ?: "Settings could not save")} }
    override fun onCleared() {system.close()}
    companion object {
        @Volatile private var shared: LoudRepositories?=null
        private fun repositories(app: Application): LoudRepositories = shared ?: synchronized(this) {shared ?: LoudRepositories(app).also {shared=it}}
    }
}

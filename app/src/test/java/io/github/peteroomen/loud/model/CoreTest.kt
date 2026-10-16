package io.github.peteroomen.loud.model
import io.github.peteroomen.loud.data.BackupCodec
import io.github.peteroomen.loud.data.LauncherBackup
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
class CoreTest {
    private val apps=listOf(SearchApp("camera","Camera","android.camera"),SearchApp("calendar","Calendar","android.calendar"),SearchApp("chat","ChatGPT","ai.chat"),SearchApp("cafe","Café","test.cafe"),SearchApp("calc","Calculator","android.calculator"))
    private fun backup()=LauncherBackup(LauncherSettings(shape=AppearanceShape.Square,appPreferences=AppPreferences(setOf("0:pkg/.App"),mapOf("0:pkg/.App" to "My app"))),listOf(HomeTile("note",HomeTileKind.Note,2,2,0xFFFF6395,mapOf("text" to "Remember\nthings"))),mapOf("0:pkg/.App" to LaunchStats(10,3,12345)),listOf(WebVisit("https://github.com","GitHub",2,12345)))
    @Test fun hiddenAppsOnlyAppearAfterTwoCharacters() {
        val p=AppPreferences(hiddenIds=setOf("camera"))
        assertFalse(AppSearch.rank(apps,"",p).any { it.id=="camera" }); assertFalse(AppSearch.rank(apps,"c",p).any { it.id=="camera" }); assertTrue(AppSearch.rank(apps,"ca",p).any { it.id=="camera" })
    }
    @Test fun exactPrefixRenameAccentAndTypoMatches() {
        val p=AppPreferences(renames=mapOf("chat" to "Assistant"))
        assertEquals("calendar",AppSearch.rank(apps,"calendar",p).first().id); assertEquals("chat",AppSearch.rank(apps,"assist",p).first().id); assertEquals("cafe",AppSearch.rank(apps,"cafe",p).first().id)
        assertTrue(AppSearch.rank(apps,"calendr",p).any { it.id=="calendar" }); assertTrue(AppSearch.rank(apps,"cmr",p).any { it.id=="camera" })
    }
    @Test fun blankQueryIsAlphabeticalRegardlessOfUsage() { assertEquals(listOf("cafe","calc","calendar","camera","chat"),AppSearch.rank(apps,"",AppPreferences(),mapOf("chat" to LaunchStats(1000,100,1000))).map { it.id }) }
    @Test fun suggestionsUseRecencyAndNeverLeakHiddenMissingApps() {
        val now=200L*86400000; val h=mapOf("calendar" to LaunchStats(1000,0,0),"camera" to LaunchStats(2,1,now),"missing" to LaunchStats(999999,0,now))
        val r=AppSearch.suggestions(apps,AppPreferences(hiddenIds=setOf("chat")),h,listOf("chat","calc"),now,3)
        assertEquals("camera",r.first().id); assertFalse(r.any { it.id in setOf("missing","chat") }); assertEquals(3,r.size)
    }
    @Test fun noMatchAndZeroLimit() { assertTrue(AppSearch.rank(apps,"unmatchablexyz",AppPreferences()).isEmpty()); assertTrue(AppSearch.suggestions(apps,AppPreferences(),emptyMap(),listOf("camera"),limit=0).isEmpty()) }
    @Test fun portableBackupRoundTripPreservesOrderSettingsHistory() { assertEquals(backup(),BackupCodec.decode(BackupCodec.encode(backup()))) }
    @Test fun badVersionDuplicatesSpanAndNegativeHistoryRejected() {
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(JSONObject(BackupCodec.encode(backup())).put("version",999).toString()) }
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.encode(backup().copy(tiles=backup().tiles+backup().tiles)) }
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.encode(backup().copy(tiles=listOf(HomeTile(kind=HomeTileKind.App,columns=5)))) }
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.encode(backup().copy(history=mapOf("app" to LaunchStats(-1,0,0)))) }
    }
    @Test fun linksRejectExecutableSchemesCredentialsAndTokens() {
        listOf("javascript:alert(1)","intent://evil","https://user:secret@example.com","https://example.com?access_token=secret","https://example.com?access%5Ftoken=secret","https://example.com#access_token=secret").forEach { url -> assertThrows(IllegalArgumentException::class.java) { BackupCodec.encode(backup().copy(tiles=listOf(HomeTile(kind=HomeTileKind.Web,config=mapOf("url" to url))))) } }
    }
    @Test fun widgetsAndWallpaperDoNotImportButMissingAppReferencesDo() {
        val tiles=listOf(HomeTile("widget",HomeTileKind.Widget,config=mapOf("widgetId" to "42")),HomeTile("app",HomeTileKind.App,config=mapOf("appId" to "0:missing/.App")))
        val r=BackupCodec.decode(BackupCodec.encode(backup().copy(settings=LauncherSettings(wallpaperUri="content://wallpaper/123"),tiles=tiles)))
        assertEquals(listOf("app"),r.tiles.map { it.id }); assertNull(r.settings.wallpaperUri)
    }
    @Test fun unknownConfigFractionalSpanAndOversizedFileRejected() {
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.encode(backup().copy(tiles=listOf(HomeTile(kind=HomeTileKind.Note,config=mapOf("authToken" to "secret"))))) }
        val o=JSONObject(BackupCodec.encode(backup())); o.getJSONArray("tiles").getJSONObject(0).put("columns",2.5)
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(o.toString()) }
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(" ".repeat(2_000_001)) }
    }
    @Test fun deeplyNestedMalformedInputIsRejectedWithoutParsing() { assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode("[".repeat(1000) + "]".repeat(1000)) } }
    @Test fun intensityRoundTripsAndDefaultsForOlderSettings() {
        val loud=LauncherSettings(intensity=AppearanceIntensity.Loud)
        assertEquals(loud,BackupCodec.decodeSettings(BackupCodec.encodeSettings(loud)))
        val older=JSONObject(BackupCodec.encodeSettings(LauncherSettings())).apply { remove("intensity") }.toString()
        assertEquals(AppearanceIntensity.Standard,BackupCodec.decodeSettings(older).intensity)
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decodeSettings(JSONObject(older).put("intensity","Deafening").toString()) }
    }
    @Test fun roundDefaultIndependentOfPaletteAndNoteEmpty() { assertEquals(AppearanceShape.Round,LauncherSettings(palette="Night Brutal").shape); assertEquals("",defaultTiles().first { it.kind==HomeTileKind.Note }.config["text"]) }
}

# Android widget integration

Package `io.github.peteroomen.loud.widget`. Manifest needs `<uses-permission android:name="android.permission.BIND_APPWIDGET" />`; normal apps still use system consent. Do not request it as a runtime permission. No extra dependency, provider receiver or service is needed.

Create one WidgetHostController(activity) before setContent. Set callbacks onWidgetReady: (WidgetSelection)->Unit and onMessage: (String)->Unit. Public methods: installedProviders(), beginAdd(provider,widthDp=280,heightDp=180), onActivityResult(requestCode,resultCode):Boolean, startListening(), stopListening(), pendingReady():WidgetSelection?, confirmPersisted(id), cancelPending(), deleteWidget(id), reconcileWidgets(persistedIds:Set<Int>), reconfigure(id).

Route Activity.onActivityResult to the controller and call super only when unconsumed. Start/stop listening in onStart/onStop. Request codes 6101 (binding), 6102 (initial setup), 6103 (reconfigure) are reserved. Host ID 0x4c4f5544 stays stable across releases. Public AppWidgetHost configuration routing supports work-profile providers.

installedProviders returns WidgetProviderCatalog(providers,message). Provider fields: label/appLabel/providerComponent/profileSerial/profileLabel/minWidthDp/minHeightDp/info:AppWidgetProviderInfo. Respect advertised minimum dimensions. Google Calendar and Teams appear only if Android offers their widgets on the device; never substitute fake widgets. Native calendar and notification tiles are complementary.

WidgetSelection has appWidgetId/providerComponent/profileSerial/label/minWidthDp/minHeightDp. toTileConfig() returns keys widgetId/provider/profileSerial/title/minWidthDp/minHeightDp. Callback must launch a coroutine and await LayoutRepository.addWidgetIfMissing(HomeTile(kind=HomeTileKind.Widget,columns=...,rows=...,config=selection.toTileConfig()),selection.appWidgetId) before confirmPersisted(id). Choose initial grid spans fitting advertised minimum width/height. On save failure cancelPending and show error.

Use LayoutRepository.addWidgetIfMissing(tile,widgetId) for atomic dedup across activity generations and persistedTiles() for reconciliation snapshots; StateFlow may still show the previous list immediately after a Room transaction returns. Lifecycle coroutine CancellationException must be rethrown and preserve pending READY rather than delete its ID. Native hosting height should be at least minHeightDp even if rows are capped by the grid editor. Show minimum-width guidance when the current content frame is narrower than the provider's advertised width. MAX_CONTENT_DP=4096; beginAdd explains and refuses providers requiring a greater minimum.

Pending ID/stage are saved before leaving Loud; READY is retained until Room save succeeds. After layout.loaded becomes true, reconcile once using actual persisted widget IDs, then recover pendingReady and deduplicate by widgetId before adding. Never reconcile using a transient loading empty list. Cancel unfinished addition action belongs in picker for OEM-discarded results after process death; do not cancel on ordinary rotation or backgrounding.

Render HostedWidget(controller,id,Modifier.fillMaxWidth().height(tileHeight)) inside themed tile frame. Real AppWidgetHostView updates provider size options using measured dp content dimensions. RemoteViews keep provider appearance. Missing providers explain enable app/profile or re-add. Await Room tile removal before deleteWidget. After successful import/reset, reconcile against replacement persisted IDs; orphan cleanup recovers interrupted deletions. Reconfigure cancellation keeps the existing widget.

Build: ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug. On-device acceptance: consent allow/deny; setup complete/cancel; rotate during setup; process death READY recovery; resize/remove; uninstall provider; quiet/disabled work profile; reconfigure. Google Calendar and OEM/profile behavior require an Android device. Teams provider presence is discovered, not assumed.

Platform references: https://developer.android.com/develop/ui/views/appwidgets/host and https://developer.android.com/reference/android/appwidget/AppWidgetHost.

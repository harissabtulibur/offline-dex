package com.offlinecyberdeck.browser

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.offlinecyberdeck.CyberDeckApplication
import com.offlinecyberdeck.core.database.entity.AppSettingsEntity
import com.offlinecyberdeck.core.database.entity.BookmarkEntity
import com.offlinecyberdeck.core.database.entity.HistoryEntity
import com.offlinecyberdeck.core.database.entity.SiteSettingEntity
import com.offlinecyberdeck.core.database.entity.TabEntity
import com.offlinecyberdeck.core.format.Formatters
import com.offlinecyberdeck.core.model.ThemeMode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

data class BrowserTabUi(
    val id: String,
    val url: String = "about:cyberdeck",
    val title: String = "New Tab",
    val isIncognito: Boolean = false,
    val isDesktopMode: Boolean = false,
    val javaScriptEnabled: Boolean = true,
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val sslBlockedWarning: String? = null
)

data class PendingExternalScheme(
    val tabId: String,
    val uriString: String
)

data class PendingSitePermission(
    val tabId: String,
    val origin: String,
    val resources: List<String>,
    val request: PermissionRequest
)

data class PendingDownloadPrompt(
    val url: String,
    val fileName: String,
    val mimeType: String
)

data class PendingLongPressLink(
    val linkUrl: String?,
    val imageUrl: String?
)

data class PendingJsDialog(
    val isConfirm: Boolean,
    val message: String,
    val result: JsResult
)

data class PendingFileChooser(
    val callback: ValueCallback<Array<Uri>>,
    val acceptTypes: Array<String>
)

class BrowserViewModel(private val app: CyberDeckApplication) : ViewModel() {

    private val _tabs = MutableStateFlow<List<BrowserTabUi>>(emptyList())
    val tabs: StateFlow<List<BrowserTabUi>> = _tabs.asStateFlow()

    private val _activeTabId = MutableStateFlow("")
    val activeTabId: StateFlow<String> = _activeTabId.asStateFlow()

    private val _addressBarInput = MutableStateFlow("")
    val addressBarInput: StateFlow<String> = _addressBarInput.asStateFlow()

    private val _findInPageVisible = MutableStateFlow(false)
    val findInPageVisible: StateFlow<Boolean> = _findInPageVisible.asStateFlow()

    private val _findQuery = MutableStateFlow("")
    val findQuery: StateFlow<String> = _findQuery.asStateFlow()

    private val _findStats = MutableStateFlow(0 to 0)
    val findStats: StateFlow<Pair<Int, Int>> = _findStats.asStateFlow()

    private val _pendingExternalScheme = MutableStateFlow<PendingExternalScheme?>(null)
    val pendingExternalScheme: StateFlow<PendingExternalScheme?> = _pendingExternalScheme.asStateFlow()

    private val _pendingSitePermission = MutableStateFlow<PendingSitePermission?>(null)
    val pendingSitePermission: StateFlow<PendingSitePermission?> = _pendingSitePermission.asStateFlow()

    private val _pendingDownloadPrompt = MutableStateFlow<PendingDownloadPrompt?>(null)
    val pendingDownloadPrompt: StateFlow<PendingDownloadPrompt?> = _pendingDownloadPrompt.asStateFlow()

    private val _pendingLongPressLink = MutableStateFlow<PendingLongPressLink?>(null)
    val pendingLongPressLink: StateFlow<PendingLongPressLink?> = _pendingLongPressLink.asStateFlow()

    private val _pendingJsDialog = MutableStateFlow<PendingJsDialog?>(null)
    val pendingJsDialog: StateFlow<PendingJsDialog?> = _pendingJsDialog.asStateFlow()

    private val _pendingFileChooser = MutableStateFlow<PendingFileChooser?>(null)
    val pendingFileChooser: StateFlow<PendingFileChooser?> = _pendingFileChooser.asStateFlow()

    private val _fullscreenCustomView = MutableStateFlow<View?>(null)
    val fullscreenCustomView: StateFlow<View?> = _fullscreenCustomView.asStateFlow()
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null

    private val _tabFavicons = MutableStateFlow<Map<String, Bitmap>>(emptyMap())
    val tabFavicons: StateFlow<Map<String, Bitmap>> = _tabFavicons.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarEvents = _snackbarEvents.asSharedFlow()

    private val _isArchiving = MutableStateFlow(false)
    val isArchiving: StateFlow<Boolean> = _isArchiving.asStateFlow()

    private val _inspectedHtmlSource = MutableStateFlow<String?>(null)
    val inspectedHtmlSource: StateFlow<String?> = _inspectedHtmlSource.asStateFlow()

    val settings = app.settingsRepository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettingsEntity())

    val bookmarks = app.database.bookmarkDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val history = app.database.historyDao().observeRecent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isOnline = app.networkMonitor.isOnline
    val transportLabel = app.networkMonitor.transportLabel
    val driveStatus = app.driveManager.status

    val activeTab: StateFlow<BrowserTabUi?> = combine(_tabs, _activeTabId) { list, id ->
        list.firstOrNull { it.id == id } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val webViewPool: WebViewPoolManager = WebViewPoolManager(
        context = app.applicationContext,
        callbacks = WebViewCallbacks(
            onPageStarted = { tabId, url ->
                updateTab(tabId) {
                    it.copy(
                        url = url,
                        isLoading = true,
                        progress = 10,
                        sslBlockedWarning = null
                    )
                }
                if (_activeTabId.value == tabId) {
                    _addressBarInput.value = url
                }
            },
            onPageFinished = { tabId, url, title, canBack, canForward ->
                updateTab(tabId) {
                    it.copy(
                        url = url,
                        title = title,
                        isLoading = false,
                        progress = 100,
                        canGoBack = canBack,
                        canGoForward = canForward
                    )
                }
                if (_activeTabId.value == tabId) {
                    _addressBarInput.value = url
                }
                recordVisitIfEligible(tabId, url, title)
                persistTabsSnapshot()
            },
            onProgressChanged = { tabId, progress ->
                updateTab(tabId) {
                    it.copy(progress = progress, isLoading = progress < 100)
                }
            },
            onTitleChanged = { tabId, title ->
                updateTab(tabId) { it.copy(title = title) }
            },
            onFaviconChanged = { tabId, favicon ->
                if (favicon != null) {
                    _tabFavicons.update { it + (tabId to favicon) }
                }
            },
            onSslErrorBlocked = { tabId, url, reason ->
                updateTab(tabId) {
                    it.copy(
                        isLoading = false,
                        sslBlockedWarning = "Blocked insecure HTTPS connection ($reason) for $url"
                    )
                }
            },
            onExternalSchemeRequested = { tabId, uriString ->
                _pendingExternalScheme.value = PendingExternalScheme(tabId, uriString)
            },
            onDownloadRequested = { url, fileName, mimeType ->
                handleWebDownloadRequest(url, fileName, mimeType)
            },
            onSitePermissionPrompt = { tabId, origin, resources, request ->
                _pendingSitePermission.value = PendingSitePermission(tabId, origin, resources, request)
            },
            onFindMatchesUpdated = { tabId, activeOrdinal, count ->
                if (_activeTabId.value == tabId) {
                    _findStats.value = activeOrdinal to count
                }
            },
            onLinkLongPressed = { _, linkUrl, imageUrl ->
                _pendingLongPressLink.value = PendingLongPressLink(linkUrl, imageUrl)
            },
            onFileChooserRequested = { callback, acceptTypes ->
                _pendingFileChooser.value?.callback?.onReceiveValue(null)
                _pendingFileChooser.value = PendingFileChooser(callback, acceptTypes)
            },
            onShowCustomView = { view, callback ->
                fullscreenCallback = callback
                _fullscreenCustomView.value = view
            },
            onHideCustomView = {
                _fullscreenCustomView.value = null
                fullscreenCallback = null
            },
            onJsDialog = { _, isConfirm, message, result ->
                _pendingJsDialog.value = PendingJsDialog(isConfirm, message, result)
            }
        )
    )

    init {
        viewModelScope.launch {
            settings.collect { s ->
                webViewPool.setMaxLiveCount(s.maxLiveWebViews)
                webViewPool.setDarkWebAllTabs(s.webViewDarkMode)
                app.driveManager.refreshStatus(s.deckDriveTreeUri)
            }
        }
        viewModelScope.launch {
            restoreTabsOnLaunch()
        }
    }

    private suspend fun restoreTabsOnLaunch() {
        val saved = app.database.tabDao().getPersistentTabs()
        val s = app.settingsRepository.getSettings()
        if (saved.isEmpty()) {
            val initial = BrowserTabUi(
                id = UUID.randomUUID().toString(),
                url = "about:cyberdeck",
                title = "New Tab",
                isDesktopMode = s.defaultDesktopMode,
                javaScriptEnabled = s.globalJavaScriptEnabled
            )
            _tabs.value = listOf(initial)
            _activeTabId.value = initial.id
            _addressBarInput.value = ""
        } else {
            val restored = saved.map {
                BrowserTabUi(
                    id = it.id,
                    url = it.url,
                    title = it.title,
                    isIncognito = false,
                    isDesktopMode = it.isDesktopMode,
                    javaScriptEnabled = s.globalJavaScriptEnabled
                )
            }
            val active = saved.firstOrNull { it.isActive }?.id ?: restored.first().id
            _tabs.value = restored
            _activeTabId.value = active
            val activeUrl = restored.firstOrNull { it.id == active }?.url.orEmpty()
            _addressBarInput.value = if (activeUrl == "about:cyberdeck") "" else activeUrl
        }
    }

    fun onAddressInputChange(value: String) {
        _addressBarInput.value = value
    }

    fun navigateFromInput(rawInput: String, targetTabId: String = _activeTabId.value) {
        viewModelScope.launch {
            val s = app.settingsRepository.getSettings()
            val resolved = Formatters.resolveNavigationUrl(rawInput, s.customSearchTemplate)
            val host = Formatters.extractHost(resolved)
            val siteSetting = if (host.isNotBlank()) app.database.siteSettingDao().getForHost(host) else null
            val jsEnabled = siteSetting?.javaScriptEnabled ?: s.globalJavaScriptEnabled
            val desktop = siteSetting?.desktopMode ?: s.defaultDesktopMode

            updateTab(targetTabId) {
                it.copy(
                    url = resolved,
                    title = if (resolved == "about:cyberdeck") "New Tab" else resolved,
                    javaScriptEnabled = jsEnabled,
                    isDesktopMode = desktop,
                    sslBlockedWarning = null
                )
            }
            if (targetTabId == _activeTabId.value) {
                _addressBarInput.value = if (resolved == "about:cyberdeck") "" else resolved
            }
            if (resolved != "about:cyberdeck") {
                webViewPool.getLiveWebView(targetTabId)?.loadUrl(resolved)
            }
            persistTabsSnapshot()
        }
    }

    fun createNewTab(url: String = "about:cyberdeck", isIncognito: Boolean = false) {
        viewModelScope.launch {
            val s = app.settingsRepository.getSettings()
            val resolved = if (url == "about:cyberdeck") url else Formatters.resolveNavigationUrl(url, s.customSearchTemplate)
            val tab = BrowserTabUi(
                id = UUID.randomUUID().toString(),
                url = resolved,
                title = if (resolved == "about:cyberdeck") {
                    if (isIncognito) "Private Tab" else "New Tab"
                } else resolved,
                isIncognito = isIncognito,
                isDesktopMode = s.defaultDesktopMode,
                javaScriptEnabled = s.globalJavaScriptEnabled
            )
            _tabs.update { it + tab }
            selectTab(tab.id)
            persistTabsSnapshot()
        }
    }

    fun selectTab(tabId: String) {
        _activeTabId.value = tabId
        webViewPool.pauseInactiveTabs(tabId)
        val tab = _tabs.value.firstOrNull { it.id == tabId }
        _addressBarInput.value = if (tab == null || tab.url == "about:cyberdeck") "" else tab.url
        _findInPageVisible.value = false
        persistTabsSnapshot()
    }

    fun closeTab(tabId: String) {
        val current = _tabs.value
        val closing = current.firstOrNull { it.id == tabId } ?: return
        webViewPool.destroyTab(tabId, closing.isIncognito)
        _tabFavicons.update { it - tabId }

        val remaining = current.filterNot { it.id == tabId }
        if (closing.isIncognito && remaining.none { it.isIncognito }) {
            webViewPool.clearAllIncognitoSessionData()
            _snackbarEvents.tryEmit("All private tabs closed — session cookies and storage cleared.")
        }

        if (remaining.isEmpty()) {
            createNewTab()
        } else {
            val nextActive = if (_activeTabId.value == tabId) remaining.last().id else _activeTabId.value
            _tabs.value = remaining
            selectTab(nextActive)
        }
        persistTabsSnapshot()
    }

    fun goBack() {
        val id = _activeTabId.value
        val webView = webViewPool.getLiveWebView(id)
        if (webView != null && webView.canGoBack()) {
            webView.goBack()
        } else {
            updateTab(id) { it.copy(url = "about:cyberdeck", title = "New Tab", canGoBack = false) }
            _addressBarInput.value = ""
        }
    }

    fun goForward() {
        webViewPool.getLiveWebView(_activeTabId.value)?.let {
            if (it.canGoForward()) it.goForward()
        }
    }

    fun reloadOrStop() {
        val tab = activeTab.value ?: return
        val webView = webViewPool.getLiveWebView(tab.id) ?: return
        if (tab.isLoading) {
            webView.stopLoading()
            updateTab(tab.id) { it.copy(isLoading = false) }
        } else if (tab.url != "about:cyberdeck") {
            webView.reload()
        }
    }

    fun goHome() {
        val id = _activeTabId.value
        updateTab(id) {
            it.copy(
                url = "about:cyberdeck",
                title = if (it.isIncognito) "Private Tab" else "New Tab",
                isLoading = false,
                sslBlockedWarning = null
            )
        }
        _addressBarInput.value = ""
        persistTabsSnapshot()
    }

    fun toggleDesktopMode() {
        val tab = activeTab.value ?: return
        val next = !tab.isDesktopMode
        updateTab(tab.id) { it.copy(isDesktopMode = next) }
        viewModelScope.launch {
            val host = Formatters.extractHost(tab.url)
            if (host.isNotBlank()) {
                val existing = app.database.siteSettingDao().getForHost(host) ?: SiteSettingEntity(host)
                app.database.siteSettingDao().upsert(existing.copy(desktopMode = next))
            }
        }
        val webView = webViewPool.getLiveWebView(tab.id)
        if (webView != null && tab.url != "about:cyberdeck") {
            webViewPool.getOrCreateWebView(
                tabId = tab.id,
                url = tab.url,
                isIncognito = tab.isIncognito,
                desktopMode = next,
                javaScriptEnabled = tab.javaScriptEnabled,
                forceDarkWeb = settings.value.webViewDarkMode,
                textZoom = settings.value.webViewTextZoom
            )
            webView.reload()
        }
        _snackbarEvents.tryEmit(if (next) "Desktop site enabled for tab" else "Mobile site enabled for tab")
    }

    fun toggleWebViewDarkMode() {
        viewModelScope.launch {
            val next = !app.settingsRepository.getSettings().webViewDarkMode
            app.settingsRepository.updateSettings { it.copy(webViewDarkMode = next) }
            webViewPool.setDarkWebAllTabs(next)
            _snackbarEvents.tryEmit(
                if (next) "Mode Gelap WebView diaktifkan" else "Mode Gelap WebView dimatikan"
            )
        }
    }

    fun toggleJavaScriptForCurrentSite() {
        val tab = activeTab.value ?: return
        val next = !tab.javaScriptEnabled
        updateTab(tab.id) { it.copy(javaScriptEnabled = next) }
        viewModelScope.launch {
            val host = Formatters.extractHost(tab.url)
            if (host.isNotBlank()) {
                val existing = app.database.siteSettingDao().getForHost(host) ?: SiteSettingEntity(host)
                app.database.siteSettingDao().upsert(existing.copy(javaScriptEnabled = next))
            }
        }
        webViewPool.getLiveWebView(tab.id)?.let {
            it.settings.javaScriptEnabled = next
            it.reload()
        }
        _snackbarEvents.tryEmit(
            if (next) "JavaScript enabled for this site"
            else "JavaScript disabled for this site"
        )
    }

    fun toggleBookmarkCurrentPage() {
        val tab = activeTab.value ?: return
        if (tab.url == "about:cyberdeck") return
        viewModelScope.launch {
            val existing = app.database.bookmarkDao().findByUrl(tab.url)
            if (existing != null) {
                app.database.bookmarkDao().delete(existing)
                _snackbarEvents.tryEmit("Bookmark removed")
            } else {
                app.database.bookmarkDao().upsert(
                    BookmarkEntity(
                        id = UUID.randomUUID().toString(),
                        title = tab.title.ifBlank { tab.url },
                        url = tab.url
                    )
                )
                _snackbarEvents.tryEmit("Bookmarked ${tab.title.take(36)}")
            }
        }
    }

    fun openFindInPage() {
        _findInPageVisible.value = true
    }

    fun closeFindInPage() {
        _findInPageVisible.value = false
        _findQuery.value = ""
        _findStats.value = 0 to 0
        webViewPool.getLiveWebView(_activeTabId.value)?.clearMatches()
    }

    fun updateFindQuery(query: String) {
        _findQuery.value = query
        val webView = webViewPool.getLiveWebView(_activeTabId.value) ?: return
        if (query.isBlank()) {
            webView.clearMatches()
            _findStats.value = 0 to 0
        } else {
            webView.findAllAsync(query)
        }
    }

    fun findNext(forward: Boolean) {
        webViewPool.getLiveWebView(_activeTabId.value)?.findNext(forward)
    }

    fun archiveCurrentPage() {
        val tab = activeTab.value ?: return
        if (tab.url == "about:cyberdeck") {
            _snackbarEvents.tryEmit("Open a webpage first to archive it.")
            return
        }
        val webView = webViewPool.getLiveWebView(tab.id)
        if (webView == null) {
            _snackbarEvents.tryEmit("WebView is not loaded yet.")
            return
        }
        _isArchiving.value = true
        webView.evaluateJavascript("(function(){return document.documentElement.outerHTML;})();") { raw ->
            viewModelScope.launch {
                try {
                    val unescaped = decodeJsString(raw)
                    val result = app.archiveEngine.archiveHtmlSnapshot(
                        sourceUrl = tab.url,
                        pageTitle = tab.title,
                        rawHtml = unescaped
                    )
                    result.onSuccess { archived ->
                        _snackbarEvents.tryEmit("Archived (${archived.status.label}): ${archived.title.take(32)}")
                    }.onFailure { err ->
                        _snackbarEvents.tryEmit("Archive failed: ${err.message}")
                    }
                } finally {
                    _isArchiving.value = false
                }
            }
        }
    }

    fun saveVisibleVideoMetadataFromCurrentPage() {
        val tab = activeTab.value ?: return
        if (tab.url == "about:cyberdeck") return
        val webView = webViewPool.getLiveWebView(tab.id) ?: return
        val js = """
            (function(){
              var metaTitle = document.querySelector('meta[property="og:title"], meta[name="title"]');
              var metaDesc = document.querySelector('meta[property="og:description"], meta[name="description"]');
              var author = document.querySelector('link[itemprop="name"], [itemprop="author"] [itemprop="name"], meta[name="author"]');
              return JSON.stringify({
                title: (metaTitle && metaTitle.content) ? metaTitle.content : document.title,
                description: (metaDesc && metaDesc.content) ? metaDesc.content : '',
                channel: (author && (author.content || author.textContent)) ? (author.content || author.textContent) : ''
              });
            })();
        """.trimIndent()
        webView.evaluateJavascript(js) { raw ->
            viewModelScope.launch {
                val decoded = decodeJsString(raw)
                val obj = runCatching { JSONObject(decoded) }.getOrNull()
                val title = obj?.optString("title").orEmpty().ifBlank { tab.title }
                val desc = obj?.optString("description").orEmpty()
                val channel = obj?.optString("channel").orEmpty()
                val videoId = app.mediaRepository.extractVideoIdFromString(tab.url)
                val saved = app.mediaRepository.savePublicWebMetadata(
                    title = title,
                    channel = channel,
                    videoId = videoId,
                    sourceUrl = tab.url,
                    description = desc
                )
                val note = if (saved.localFileUri != null) "matched to local file!" else "marked Metadata Only (No local file)."
                _snackbarEvents.tryEmit("Saved catalog entry '${saved.title.take(28)}' — $note")
            }
        }
    }

    fun dismissExternalScheme() {
        _pendingExternalScheme.value = null
    }

    fun resolveSitePermission(allow: Boolean) {
        val pending = _pendingSitePermission.value ?: return
        runCatching {
            if (allow) pending.request.grant(pending.resources.toTypedArray())
            else pending.request.deny()
        }
        _pendingSitePermission.value = null
    }

    fun confirmPendingDownload(confirm: Boolean) {
        val pending = _pendingDownloadPrompt.value ?: return
        _pendingDownloadPrompt.value = null
        if (confirm) {
            startDirectDownload(pending.url, pending.fileName, pending.mimeType)
        }
    }

    fun startDirectDownload(url: String, fileName: String? = null, mimeType: String? = null) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            _snackbarEvents.tryEmit("Only http/https links can be downloaded.")
            return
        }
        viewModelScope.launch {
            val dl = app.downloadRepository.enqueueDownload(url, fileName, mimeType)
            _snackbarEvents.tryEmit("Download started: ${dl.fileName}")
        }
    }

    fun dismissLongPressLink() {
        _pendingLongPressLink.value = null
    }

    fun resolveJsDialog(confirm: Boolean) {
        val pending = _pendingJsDialog.value ?: return
        runCatching {
            if (confirm) pending.result.confirm() else pending.result.cancel()
        }
        _pendingJsDialog.value = null
    }

    fun resolveFileChooser(uris: Array<Uri>?) {
        val pending = _pendingFileChooser.value ?: return
        runCatching {
            pending.callback.onReceiveValue(uris)
        }
        _pendingFileChooser.value = null
    }

    fun exitFullscreenCustomView() {
        runCatching { fullscreenCallback?.onCustomViewHidden() }
        fullscreenCallback = null
        _fullscreenCustomView.value = null
    }

    fun toggleBrowserFullscreen() {
        viewModelScope.launch {
            val current = app.settingsRepository.getSettings()
            val next = !(current.immersiveFullscreen || current.hideNavChromeInBrowser)
            app.settingsRepository.updateSettings {
                it.copy(
                    immersiveFullscreen = next,
                    hideNavChromeInBrowser = next
                )
            }
        }
    }

    fun inspectCurrentPageHtml() {
        val tab = activeTab.value ?: return
        if (tab.url == "about:cyberdeck") {
            _snackbarEvents.tryEmit("Open a webpage first to inspect HTML source.")
            return
        }
        val webView = webViewPool.getLiveWebView(tab.id) ?: return
        webView.evaluateJavascript(
            "(function(){ return document.documentElement ? document.documentElement.outerHTML : ''; })();"
        ) { raw ->
            val html = decodeJsString(raw)
            _inspectedHtmlSource.value = html.ifBlank { "<!-- Empty HTML -->" }.take(120_000)
        }
    }

    fun dismissInspectedHtml() {
        _inspectedHtmlSource.value = null
    }

    private fun handleWebDownloadRequest(url: String, fileName: String, mimeType: String) {
        viewModelScope.launch {
            val s = app.settingsRepository.getSettings()
            if (s.askBeforeDownload) {
                _pendingDownloadPrompt.value = PendingDownloadPrompt(url, fileName, mimeType)
            } else {
                app.downloadRepository.enqueueDownload(url, fileName, mimeType)
                _snackbarEvents.tryEmit("Download queued: $fileName")
            }
        }
    }

    private fun recordVisitIfEligible(tabId: String, url: String, title: String) {
        val tab = _tabs.value.firstOrNull { it.id == tabId } ?: return
        if (tab.isIncognito || url == "about:cyberdeck") return
        viewModelScope.launch {
            val s = app.settingsRepository.getSettings()
            if (!s.recordHistory) return@launch
            app.database.historyDao().insert(
                HistoryEntity(
                    id = UUID.randomUUID().toString(),
                    title = title.ifBlank { url },
                    url = url
                )
            )
        }
    }

    private fun updateTab(tabId: String, transform: (BrowserTabUi) -> BrowserTabUi) {
        _tabs.update { list ->
            list.map { if (it.id == tabId) transform(it) else it }
        }
    }

    private fun persistTabsSnapshot() {
        viewModelScope.launch {
            val active = _activeTabId.value
            val persistent = _tabs.value.filter { !it.isIncognito }.mapIndexed { idx, tab ->
                TabEntity(
                    id = tab.id,
                    url = tab.url,
                    title = tab.title,
                    isIncognito = false,
                    isDesktopMode = tab.isDesktopMode,
                    position = idx,
                    isActive = tab.id == active
                )
            }
            app.database.tabDao().clearPersistentTabs()
            if (persistent.isNotEmpty()) {
                app.database.tabDao().upsertAll(persistent)
            }
        }
    }

    private fun decodeJsString(raw: String?): String {
        if (raw.isNullOrBlank() || raw == "null") return ""
        return runCatching {
            JSONObject("{\"v\":$raw}").getString("v")
        }.getOrDefault(raw.trim('"'))
    }

    override fun onCleared() {
        super.onCleared()
        webViewPool.destroyAll()
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return BrowserViewModel(context.applicationContext as CyberDeckApplication) as T
        }
    }
}

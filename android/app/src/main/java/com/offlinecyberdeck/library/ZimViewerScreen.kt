package com.offlinecyberdeck.library

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.offlinecyberdeck.CyberDeckApplication
import com.offlinecyberdeck.core.database.entity.LibraryItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream

private const val ZIM_HOST = "zim.offline"
private const val ZIM_ORIGIN = "https://$ZIM_HOST"

private fun buildZimUrl(key: String): String =
    Uri.Builder()
        .scheme("https")
        .authority(ZIM_HOST)
        .path("/${key.trimStart('/')}")
        .build()
        .toString()

private val WikiLightBg = Color(0xFFFFFFFF)
private val WikiLightHeader = Color(0xFFF8F9FA)
private val WikiLightBorder = Color(0xFFEAECF0)
private val WikiLightInk = Color(0xFF202122)
private val WikiLightMuted = Color(0xFF54595D)
private val WikiBlue = Color(0xFF3366CC)

private val WikiDarkBg = Color(0xFF101418)
private val WikiDarkHeader = Color(0xFF181D24)
private val WikiDarkBorder = Color(0xFF2C3440)
private val WikiDarkInk = Color(0xFFE6EDF3)
private val WikiDarkMuted = Color(0xFF9AA4B2)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ZimViewerScreen(
    app: CyberDeckApplication,
    itemId: String,
    webTextZoom: Int = 100,
    onBack: () -> Unit
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    var item by remember { mutableStateOf<LibraryItemEntity?>(null) }
    var reader by remember { mutableStateOf<ZimArchiveReader?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoadingArchive by remember { mutableStateOf(true) }
    var isPageLoading by remember { mutableStateOf(false) }
    var pageTitle by remember { mutableStateOf("Wikipedia Offline") }
    var canGoBackWeb by remember { mutableStateOf(false) }
    var canGoForwardWeb by remember { mutableStateOf(false) }
    var mainPageUrl by remember { mutableStateOf("$ZIM_ORIGIN/__index__") }

    var searchQuery by remember { mutableStateOf("") }
    var searchHits by remember { mutableStateOf<List<ZimSearchHit>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var searchDropdownOpen by remember { mutableStateOf(false) }
    var nightFilterEnabled by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var lastStartedInitialUrl by remember { mutableStateOf<String?>(null) }
    var searchJob by remember { mutableStateOf<Job?>(null) }

    val headerBg = if (nightFilterEnabled) WikiDarkHeader else WikiLightHeader
    val surfaceBg = if (nightFilterEnabled) WikiDarkBg else WikiLightBg
    val borderCol = if (nightFilterEnabled) WikiDarkBorder else WikiLightBorder
    val inkCol = if (nightFilterEnabled) WikiDarkInk else WikiLightInk
    val mutedCol = if (nightFilterEnabled) WikiDarkMuted else WikiLightMuted

    DisposableEffect(view) {
        val prev = view.keepScreenOn
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = prev }
    }

    LaunchedEffect(itemId) {
        isLoadingArchive = true
        errorMessage = null
        lastStartedInitialUrl = null
        reader?.close()
        reader = null
        val loaded = app.database.libraryDao().getById(itemId)
        item = loaded
        if (loaded == null) {
            errorMessage = "Arsip ZIM tidak ditemukan di Knowledge Vault."
            isLoadingArchive = false
            return@LaunchedEffect
        }
        pageTitle = loaded.title

        withContext(Dispatchers.IO) {
            runCatching {
                val pfd = app.driveManager.openReadFileDescriptor(loaded.fileUri)
                    ?: error("Tidak bisa mengakses file ZIM (${loaded.fileName}). Pastikan izin penyimpanan / Deck Drive aktif.")
                val opened = ZimArchiveReader(pfd)
                val startKey = opened.getMainPageKey()
                val startUrl = if (!startKey.isNullOrBlank()) {
                    buildZimUrl(startKey)
                } else {
                    "$ZIM_ORIGIN/__index__"
                }
                opened to startUrl
            }.onSuccess { (opened, startUrl) ->
                reader = opened
                mainPageUrl = startUrl
                isLoadingArchive = false
            }.onFailure { err ->
                errorMessage = "Gagal membuka ZIM: ${err.message ?: err.javaClass.simpleName}"
                isLoadingArchive = false
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.apply {
                stopLoading()
                loadUrl("about:blank")
                clearHistory()
                removeAllViews()
                destroy()
            }
            webViewRef = null
            reader?.close()
            reader = null
        }
    }

    BackHandler {
        val wv = webViewRef
        if (searchDropdownOpen) {
            searchDropdownOpen = false
        } else if (wv != null && wv.canGoBack()) {
            wv.goBack()
        } else {
            onBack()
        }
    }

    LaunchedEffect(nightFilterEnabled, webViewRef) {
        val wv = webViewRef ?: return@LaunchedEffect
        wv.setBackgroundColor(if (nightFilterEnabled) AndroidColor.parseColor("#101418") else AndroidColor.WHITE)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            runCatching { WebSettingsCompat.setAlgorithmicDarkeningAllowed(wv.settings, nightFilterEnabled) }
        }
        if (wv.url?.startsWith(ZIM_ORIGIN) == true) {
            wv.evaluateJavascript(buildWikipediaCssInjectionJs(nightFilterEnabled), null)
        }
    }

    LaunchedEffect(reader, mainPageUrl, webViewRef) {
        val active = reader ?: return@LaunchedEffect
        val wv = webViewRef ?: return@LaunchedEffect
        if (lastStartedInitialUrl != mainPageUrl && active != null) {
            lastStartedInitialUrl = mainPageUrl
            wv.loadUrl(mainPageUrl)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(surfaceBg)) {
        // Real Wikipedia-style top navigation & search bar
        Surface(color = headerBg) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = {
                        val wv = webViewRef
                        if (wv != null && wv.canGoBack()) wv.goBack() else onBack()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = inkCol
                    )
                }
                IconButton(
                    onClick = { webViewRef?.goForward() },
                    enabled = canGoForwardWeb,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Forward",
                        tint = if (canGoForwardWeb) inkCol else mutedCol.copy(alpha = 0.35f)
                    )
                }

                // Classic Wikipedia 'W' Globe Emblem
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (nightFilterEnabled) Color(0xFF262D38) else Color(0xFFEFF2F6))
                        .clickable {
                            searchDropdownOpen = false
                            webViewRef?.loadUrl(mainPageUrl)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "W",
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = inkCol
                    )
                }

                Column(modifier = Modifier.weight(0.95f)) {
                    Text(
                        text = pageTitle,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = inkCol,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val activeReader = reader
                    if (activeReader != null) {
                        Text(
                            text = "Ensiklopedia Offline • ${activeReader.header.entryCount} entri (ZIM v${activeReader.header.majorVersion})",
                            fontSize = 10.sp,
                            color = mutedCol,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Inline Wikipedia Search Field
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = surfaceBg,
                    border = BorderStroke(1.dp, borderCol),
                    modifier = Modifier
                        .weight(1.25f)
                        .height(36.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = null,
                            tint = mutedCol,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    "Cari di Wikipedia offline...",
                                    fontSize = 12.sp,
                                    color = mutedCol,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { next ->
                                    searchQuery = next
                                    searchDropdownOpen = next.isNotBlank()
                                    searchJob?.cancel()
                                    val activeReader = reader ?: return@BasicTextField
                                    searchJob = scope.launch {
                                        delay(200L)
                                        if (next.isBlank()) {
                                            searchHits = emptyList()
                                            isSearching = false
                                            return@launch
                                        }
                                        isSearching = true
                                        val hits = withContext(Dispatchers.IO) {
                                            activeReader.searchTitles(next, limit = 35)
                                        }
                                        searchHits = hits
                                        isSearching = false
                                    }
                                },
                                singleLine = true,
                                textStyle = TextStyle(color = inkCol, fontSize = 13.sp),
                                cursorBrush = SolidColor(WikiBlue),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(
                                    onSearch = {
                                        val first = searchHits.firstOrNull()
                                        if (first != null) {
                                            searchDropdownOpen = false
                                            webViewRef?.loadUrl(buildZimUrl(first.fullKey))
                                        }
                                    }
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (searchQuery.isNotEmpty()) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Hapus",
                                tint = mutedCol,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable {
                                        searchQuery = ""
                                        searchHits = emptyList()
                                        searchDropdownOpen = false
                                    }
                            )
                        }
                    }
                }

                IconButton(
                    onClick = {
                        searchDropdownOpen = false
                        webViewRef?.loadUrl(mainPageUrl)
                    },
                    enabled = reader != null,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(Icons.Filled.Home, contentDescription = "Halaman Utama", tint = inkCol)
                }
                IconButton(
                    onClick = {
                        searchDropdownOpen = false
                        webViewRef?.loadUrl("$ZIM_ORIGIN/__index__")
                    },
                    enabled = reader != null,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(Icons.Filled.MenuBook, contentDescription = "Indeks Artikel", tint = inkCol)
                }
                IconButton(
                    onClick = { nightFilterEnabled = !nightFilterEnabled },
                    enabled = reader != null,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        Icons.Filled.Contrast,
                        contentDescription = "Mode Gelap Wikipedia",
                        tint = if (nightFilterEnabled) WikiBlue else mutedCol
                    )
                }
                IconButton(onClick = onBack, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Tutup", tint = inkCol)
                }
            }
        }
        HorizontalDivider(color = borderCol)

        if (isLoadingArchive || isPageLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = WikiBlue,
                trackColor = borderCol
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val activeReader = reader
            when {
                errorMessage != null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "W",
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 42.sp,
                            color = mutedCol
                        )
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.error
                        )
                        Button(onClick = onBack) {
                            Text("Tutup")
                        }
                    }
                }
                activeReader == null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "W",
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 42.sp,
                            color = inkCol
                        )
                        Text(
                            text = "Membuka indeks Wikipedia / ZIM (streaming RAM hemat)...",
                            color = mutedCol
                        )
                    }
                }
                else -> {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                setBackgroundColor(
                                    if (nightFilterEnabled) AndroidColor.parseColor("#101418") else AndroidColor.WHITE
                                )
                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    builtInZoomControls = true
                                    displayZoomControls = false
                                    loadWithOverviewMode = true
                                    useWideViewPort = true
                                    cacheMode = WebSettings.LOAD_NO_CACHE
                                    textZoom = webTextZoom.coerceIn(75, 150)
                                    defaultTextEncodingName = "UTF-8"
                                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    allowFileAccess = false
                                    allowContentAccess = false
                                }
                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        isPageLoading = true
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        isPageLoading = false
                                        canGoBackWeb = view?.canGoBack() == true
                                        canGoForwardWeb = view?.canGoForward() == true
                                        val title = view?.title?.takeIf {
                                            it.isNotBlank() && it != "about:blank" && !it.startsWith(ZIM_HOST)
                                        }
                                        if (title != null) pageTitle = title
                                        view?.evaluateJavascript(
                                            buildWikipediaCssInjectionJs(nightFilterEnabled),
                                            null
                                        )
                                    }

                                    override fun onReceivedError(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                        error: android.webkit.WebResourceError?
                                    ) {
                                        if (request?.isForMainFrame == true) {
                                            isPageLoading = false
                                        }
                                    }

                                    override fun onRenderProcessGone(
                                        view: WebView?,
                                        detail: RenderProcessGoneDetail?
                                    ): Boolean {
                                        isPageLoading = false
                                        errorMessage = "Renderer WebView berhenti sementara. Tutup & buka ulang arsip ZIM."
                                        return true
                                    }

                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {
                                        val url = request?.url ?: return false
                                        if (url.host != ZIM_HOST) return true
                                        return false
                                    }

                                    override fun shouldInterceptRequest(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): WebResourceResponse {
                                        return try {
                                            val uri = request?.url ?: return emptyNotFound()
                                            if (uri.host != ZIM_HOST) {
                                                return emptyNotFound()
                                            }
                                            val path = uri.encodedPath.orEmpty().trimStart('/')
                                            if (path == "__index__" || (path.isBlank() && mainPageUrl.endsWith("__index__"))) {
                                                val samples = runCatching { activeReader.getSampleArticles(50) }.getOrDefault(emptyList())
                                                val html = buildWikipediaPortalHtml(
                                                    title = item?.title ?: "Wikipedia Offline",
                                                    entryCount = activeReader.header.entryCount,
                                                    samples = samples,
                                                    dark = nightFilterEnabled
                                                )
                                                return okWebResponse("text/html", "UTF-8", html.toByteArray(Charsets.UTF_8))
                                            }

                                            val result = activeReader.loadWebPath(path)
                                            val loaded = result.getOrNull()
                                            if (loaded != null) {
                                                val cleanMime = when {
                                                    loaded.mimeType.equals("application/xhtml+xml", ignoreCase = true) -> "text/html"
                                                    else -> loaded.mimeType
                                                }
                                                val encoding = if (cleanMime.startsWith("text/") ||
                                                    cleanMime.contains("javascript") ||
                                                    cleanMime.contains("svg") ||
                                                    cleanMime.contains("json") ||
                                                    cleanMime.contains("xml")
                                                ) "UTF-8" else null

                                                if (cleanMime == "text/html" && loaded.bytes.isEmpty()) {
                                                    val samples = runCatching { activeReader.getSampleArticles(25) }.getOrDefault(emptyList())
                                                    val fallback = buildWikipediaMissingHtml(
                                                        title = item?.title ?: "Wikipedia Offline",
                                                        requestedPath = path,
                                                        reason = "Halaman ini kosong (0 byte) di dalam arsip ZIM.",
                                                        samples = samples,
                                                        dark = nightFilterEnabled
                                                    )
                                                    return okWebResponse("text/html", "UTF-8", fallback.toByteArray(Charsets.UTF_8))
                                                }
                                                return okWebResponse(cleanMime, encoding, loaded.bytes)
                                            }

                                            val isMainFrame = request.isForMainFrame || isLikelyHtmlPath(path)
                                            if (isMainFrame) {
                                                val reason = result.exceptionOrNull()?.message ?: "Entri tidak ditemukan."
                                                val samples = runCatching { activeReader.getSampleArticles(25) }.getOrDefault(emptyList())
                                                val fallback = buildWikipediaMissingHtml(
                                                    title = item?.title ?: "Wikipedia Offline",
                                                    requestedPath = path,
                                                    reason = reason,
                                                    samples = samples,
                                                    dark = nightFilterEnabled
                                                )
                                                return okWebResponse("text/html", "UTF-8", fallback.toByteArray(Charsets.UTF_8))
                                            }
                                            emptyNotFound()
                                        } catch (t: Throwable) {
                                            if (request?.isForMainFrame == true) {
                                                val fallback = buildWikipediaMissingHtml(
                                                    title = item?.title ?: "Wikipedia Offline",
                                                    requestedPath = request.url?.path ?: "",
                                                    reason = "Kesalahan membaca konten ZIM: ${t.message ?: t.javaClass.simpleName}",
                                                    samples = emptyList(),
                                                    dark = nightFilterEnabled
                                                )
                                                okWebResponse("text/html", "UTF-8", fallback.toByteArray(Charsets.UTF_8))
                                            } else {
                                                emptyNotFound()
                                            }
                                        }
                                    }
                                }
                                webViewRef = this
                            }
                        },
                        update = { wv ->
                            wv.settings.textZoom = webTextZoom.coerceIn(75, 150)
                        },
                        onRelease = { wv ->
                            wv.stopLoading()
                            wv.loadUrl("about:blank")
                            wv.removeAllViews()
                            wv.destroy()
                            if (webViewRef === wv) webViewRef = null
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Floating Wikipedia Search Results Dropdown under header
            if (searchDropdownOpen && reader != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = surfaceBg),
                    border = BorderStroke(1.dp, borderCol),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 6.dp, end = 12.dp)
                        .width(420.dp)
                        .heightIn(max = 360.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (isSearching) {
                            Text(
                                "Mencari artikel di ZIM...",
                                fontSize = 12.sp,
                                color = mutedCol,
                                modifier = Modifier.padding(12.dp)
                            )
                        } else if (searchHits.isEmpty()) {
                            Text(
                                "Tidak ada artikel cocok untuk \"$searchQuery\".",
                                fontSize = 12.sp,
                                color = mutedCol,
                                modifier = Modifier.padding(12.dp)
                            )
                        } else {
                            LazyColumn {
                                items(searchHits, key = { "${it.entryIndex}_${it.fullKey}" }) { hit ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                searchDropdownOpen = false
                                                webViewRef?.loadUrl(buildZimUrl(hit.fullKey))
                                            }
                                            .padding(horizontal = 14.dp, vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = hit.title,
                                            fontFamily = FontFamily.Serif,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 14.sp,
                                            color = inkCol,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = hit.fullKey,
                                            fontSize = 11.sp,
                                            color = mutedCol,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    HorizontalDivider(color = borderCol)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun okWebResponse(
    mimeType: String,
    encoding: String?,
    bytes: ByteArray
): WebResourceResponse = WebResourceResponse(
    mimeType,
    encoding,
    200,
    "OK",
    mapOf(
        "Access-Control-Allow-Origin" to "*",
        "Cache-Control" to "no-store"
    ),
    ByteArrayInputStream(bytes)
)

private fun emptyNotFound(): WebResourceResponse = WebResourceResponse(
    "text/plain",
    "UTF-8",
    404,
    "Not Found",
    mapOf("Access-Control-Allow-Origin" to "*"),
    ByteArrayInputStream(ByteArray(0))
)

private fun isLikelyHtmlPath(path: String): Boolean {
    val clean = path.substringBefore('?').substringBefore('#').lowercase()
    if (clean.endsWith(".html") || clean.endsWith(".htm")) return true
    val last = clean.substringAfterLast('/')
    return !last.contains('.')
}

private fun buildWikipediaCssInjectionJs(dark: Boolean): String {
    val css = if (dark) {
        """
        html, body {
          background: #101418 !important;
          color: #E6EDF3 !important;
          font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
          line-height: 1.65;
          max-width: 960px;
          margin: 0 auto !important;
          padding: 12px 18px 36px 18px !important;
          box-sizing: border-box;
        }
        h1, h2 { font-family: Georgia, "Times New Roman", serif !important; border-bottom: 1px solid #2C3440 !important; padding-bottom: 4px; color: #F0F6FC !important; }
        h3, h4, h5 { color: #E6EDF3 !important; }
        a { color: #6B9EFF !important; }
        table, .infobox, .wikitable, .navbox, .toc, .thumbinner, blockquote, pre, code {
          background-color: #181D24 !important;
          color: #E6EDF3 !important;
          border-color: #2C3440 !important;
        }
        th { background-color: #222933 !important; color: #F0F6FC !important; }
        img { max-width: 100%; height: auto; }
        table { display: block; overflow-x: auto; max-width: 100%; }
        """.trimIndent()
    } else {
        """
        html, body {
          background: #FFFFFF;
          color: #202122;
          font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
          line-height: 1.65;
          max-width: 960px;
          margin: 0 auto;
          padding: 12px 18px 36px 18px;
          box-sizing: border-box;
        }
        h1, h2 { font-family: Georgia, "Times New Roman", serif; border-bottom: 1px solid #A2A9B1; padding-bottom: 4px; font-weight: normal; color: #000000; }
        a { color: #3366CC; text-decoration: none; }
        a:hover { text-decoration: underline; }
        .infobox, .toc, .thumbinner { background-color: #F8F9FA; border: 1px solid #A2A9B1; }
        img { max-width: 100%; height: auto; }
        table { max-width: 100%; overflow-x: auto; }
        """.trimIndent()
    }
    val escaped = css.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")
    return """
        (function(){
          var id='cd-wiki-style';
          var el=document.getElementById(id);
          if(!el){
            el=document.createElement('style');
            el.id=id;
            (document.head||document.documentElement).appendChild(el);
          }
          el.textContent='$escaped';
        })();
    """.trimIndent()
}

private fun escapeHtml(raw: String): String =
    raw.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

private fun buildWikipediaPortalHtml(
    title: String,
    entryCount: Int,
    samples: List<ZimSearchHit>,
    dark: Boolean
): String {
    val bg = if (dark) "#101418" else "#FFFFFF"
    val card = if (dark) "#181D24" else "#F8F9FA"
    val border = if (dark) "#2C3440" else "#EAECF0"
    val ink = if (dark) "#E6EDF3" else "#202122"
    val muted = if (dark) "#9AA4B2" else "#54595D"
    val link = if (dark) "#6B9EFF" else "#3366CC"

    val links = if (samples.isEmpty()) {
        "<li style='color:$muted'>Gunakan kolom pencarian di bilah atas untuk menemukan artikel.</li>"
    } else {
        samples.joinToString("\n") { hit ->
            "<li><a href=\"/${hit.fullKey.trimStart('/')}\">${escapeHtml(hit.title)}</a></li>"
        }
    }

    return """
        <!doctype html>
        <html>
        <head>
          <meta charset="utf-8" />
          <meta name="viewport" content="width=device-width,initial-scale=1" />
          <title>${escapeHtml(title)}</title>
          <style>
            body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: $bg; color: $ink; margin: 0 auto; max-width: 880px; padding: 24px 18px; line-height: 1.6; }
            .wiki-hero { text-align: center; padding: 18px 12px; border-bottom: 1px solid $border; margin-bottom: 20px; }
            .wiki-mark { font-family: Georgia, "Times New Roman", serif; font-size: 38px; font-weight: bold; letter-spacing: 2px; margin: 0; }
            .wiki-sub { color: $muted; font-size: 14px; margin-top: 4px; }
            .wiki-box { background: $card; border: 1px solid $border; border-radius: 6px; padding: 16px 20px; }
            h2 { font-family: Georgia, serif; font-size: 18px; border-bottom: 1px solid $border; padding-bottom: 6px; margin-top: 0; }
            ul { columns: 2; column-gap: 24px; padding-left: 18px; margin: 12px 0 0 0; }
            @media (max-width: 600px) { ul { columns: 1; } }
            li { margin: 6px 0; break-inside: avoid; }
            a { color: $link; text-decoration: none; font-weight: 500; }
            a:hover { text-decoration: underline; }
          </style>
        </head>
        <body>
          <div class="wiki-hero">
            <div class="wiki-mark">WIKIPEDIA</div>
            <div class="wiki-sub">${escapeHtml(title)} • $entryCount total entri offline terindeks</div>
          </div>
          <div class="wiki-box">
            <h2>Artikel Pilihan & Indeks</h2>
            <p style="color:$muted;font-size:13px;margin:4px 0 10px 0;">Ketik judul di kotak pencarian atas untuk membuka artikel apa pun secara instan tanpa internet.</p>
            <ul>$links</ul>
          </div>
        </body>
        </html>
    """.trimIndent()
}

private fun buildWikipediaMissingHtml(
    title: String,
    requestedPath: String,
    reason: String,
    samples: List<ZimSearchHit>,
    dark: Boolean
): String {
    val bg = if (dark) "#101418" else "#FFFFFF"
    val card = if (dark) "#181D24" else "#F8F9FA"
    val border = if (dark) "#2C3440" else "#EAECF0"
    val ink = if (dark) "#E6EDF3" else "#202122"
    val muted = if (dark) "#9AA4B2" else "#54595D"
    val link = if (dark) "#6B9EFF" else "#3366CC"

    val links = samples.joinToString("\n") { hit ->
        "<li><a href=\"/${hit.fullKey.trimStart('/')}\">${escapeHtml(hit.title)}</a></li>"
    }

    return """
        <!doctype html>
        <html>
        <head>
          <meta charset="utf-8" />
          <meta name="viewport" content="width=device-width,initial-scale=1" />
          <title>Halaman Tidak Ditemukan - ${escapeHtml(title)}</title>
          <style>
            body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: $bg; color: $ink; margin: 0 auto; max-width: 820px; padding: 22px 18px; line-height: 1.6; }
            .card { background: $card; border: 1px solid $border; border-radius: 6px; padding: 16px 20px; margin-bottom: 18px; }
            h1 { font-family: Georgia, serif; font-size: 22px; margin: 0 0 8px 0; border-bottom: 1px solid $border; padding-bottom: 6px; }
            a { color: $link; text-decoration: none; }
            ul { padding-left: 18px; }
            li { margin: 5px 0; }
          </style>
        </head>
        <body>
          <div class="card">
            <h1>Artikel Belum Tersedia di Arsip ZIM</h1>
            <p>Path: <code>${escapeHtml(requestedPath)}</code></p>
            <p style="color:$muted;font-size:13px;">${escapeHtml(reason)}</p>
            <p><a href="/__index__">← Kembali ke Indeks Artikel ZIM</a></p>
          </div>
          ${if (samples.isNotEmpty()) "<div class='card'><strong>Artikel Tersedia:</strong><ul>$links</ul></div>" else ""}
        </body>
        </html>
    """.trimIndent()
}

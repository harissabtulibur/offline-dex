package com.offlinecyberdeck.library

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.ServiceWorkerClient
import android.webkit.ServiceWorkerController
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offlinecyberdeck.CyberDeckApplication
import com.offlinecyberdeck.ui.theme.DeckAmber
import com.offlinecyberdeck.ui.theme.DeckGreen
import java.io.File

private const val KIWIX_PWA_URL = "https://pwa.kiwix.org/www/index.html"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun OfflineWikiScreen(
    app: CyberDeckApplication,
    onBack: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val isOnline by app.networkMonitor.isOnline.collectAsStateWithLifecycle()

    val offlineDir = remember { File(context.filesDir, "offline_wiki").apply { mkdirs() } }
    val archiveFile = remember { File(offlineDir, "kiwix_pwa.mht") }
    var hasLocalSnapshot by remember { mutableStateOf(archiveFile.exists() && archiveFile.length() > 1024L) }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf(KIWIX_PWA_URL) }
    var loadProgress by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var usingSnapshotMode by remember { mutableStateOf(false) }
    var pendingFileChooserCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        pendingFileChooserCallback?.onReceiveValue(
            if (uris.isNotEmpty()) uris.toTypedArray() else null
        )
        pendingFileChooserCallback = null
    }

    fun saveOfflineSnapshot(wv: WebView, silent: Boolean) {
        runCatching {
            wv.saveWebArchive(archiveFile.absolutePath, false) { savedPath ->
                val ok = !savedPath.isNullOrBlank() && File(savedPath).length() > 1024L
                if (ok) {
                    hasLocalSnapshot = true
                    if (!silent) {
                        onShowMessage("Offline Wiki (Kiwix PWA) disimpan ke memori lokal untuk akses offline.")
                    }
                } else if (!silent) {
                    onShowMessage("Gagal menyimpan snapshot Offline Wiki. Pastikan halaman sudah termuat penuh.")
                }
            }
        }.onFailure {
            if (!silent) onShowMessage("Snapshot error: ${it.message}")
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            pendingFileChooserCallback?.onReceiveValue(null)
            pendingFileChooserCallback = null
            runCatching { webViewRef?.destroy() }
            webViewRef = null
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(tonalElevation = 2.dp, shadowElevation = 1.dp) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Tutup", modifier = Modifier.size(18.dp))
                    }
                    Icon(
                        Icons.Filled.MenuBook,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Offline Wiki (Kiwix PWA)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            if (usingSnapshotMode) "Mode Snapshot Lokal (Offline)" else currentUrl,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Surface(
                        color = if (isOnline) DeckGreen.copy(alpha = 0.16f) else DeckAmber.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                if (isOnline) Icons.Filled.CloudDone else Icons.Filled.CloudOff,
                                contentDescription = null,
                                tint = if (isOnline) DeckGreen else DeckAmber,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                when {
                                    isOnline -> "ONLINE"
                                    hasLocalSnapshot -> "OFFLINE READY"
                                    else -> "OFFLINE"
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isOnline) DeckGreen else DeckAmber
                            )
                        }
                    }

                    IconButton(
                        onClick = { webViewRef?.let { if (it.canGoBack()) it.goBack() } },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Mundur", modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                        onClick = { webViewRef?.let { if (it.canGoForward()) it.goForward() } },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Filled.ArrowForward, contentDescription = "Maju", modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                        onClick = {
                            usingSnapshotMode = false
                            webViewRef?.settings?.cacheMode = if (isOnline) {
                                WebSettings.LOAD_DEFAULT
                            } else {
                                WebSettings.LOAD_CACHE_ELSE_NETWORK
                            }
                            webViewRef?.reload()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Muat Ulang", modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                        onClick = {
                            usingSnapshotMode = false
                            webViewRef?.loadUrl(KIWIX_PWA_URL)
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Filled.Home, contentDescription = "Kiwix Home", modifier = Modifier.size(16.dp))
                    }

                    Button(
                        onClick = { webViewRef?.let { saveOfflineSnapshot(it, silent = false) } },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Simpan Offline", fontSize = 11.sp)
                    }

                    if (hasLocalSnapshot && !usingSnapshotMode) {
                        OutlinedButton(
                            onClick = {
                                usingSnapshotMode = true
                                webViewRef?.loadUrl("file://${archiveFile.absolutePath}")
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Buka Snapshot", fontSize = 11.sp)
                        }
                    }
                }
                if (isLoading && loadProgress in 1..99) {
                    LinearProgressIndicator(
                        progress = { loadProgress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth().background(Color.White)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    runCatching {
                        ServiceWorkerController.getInstance().setServiceWorkerClient(object : ServiceWorkerClient() {
                            override fun shouldInterceptRequest(request: WebResourceRequest?): WebResourceResponse? = null
                        })
                    }
                    WebView(ctx).apply {
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            allowFileAccess = true
                            allowContentAccess = true
                            @Suppress("DEPRECATION")
                            allowFileAccessFromFileURLs = true
                            @Suppress("DEPRECATION")
                            allowUniversalAccessFromFileURLs = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            builtInZoomControls = true
                            displayZoomControls = false
                            cacheMode = if (isOnline) WebSettings.LOAD_DEFAULT else WebSettings.LOAD_CACHE_ELSE_NETWORK
                        }
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                isLoading = true
                                if (!url.isNullOrBlank()) currentUrl = url
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                                if (!url.isNullOrBlank()) currentUrl = url
                                // Automatically cache a local MHT snapshot whenever the online Kiwix PWA loads
                                if (view != null && url != null && url.startsWith("https://pwa.kiwix.org")) {
                                    usingSnapshotMode = false
                                    saveOfflineSnapshot(view, silent = true)
                                }
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                if (request?.isForMainFrame == true && view != null && !usingSnapshotMode) {
                                    if (archiveFile.exists() && archiveFile.length() > 1024L) {
                                        usingSnapshotMode = true
                                        view.loadUrl("file://${archiveFile.absolutePath}")
                                        onShowMessage("Offline terdeteksi — memuat Offline Wiki dari snapshot lokal.")
                                    }
                                }
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                loadProgress = newProgress
                            }

                            override fun onShowFileChooser(
                                webView: WebView?,
                                filePathCallback: ValueCallback<Array<Uri>>?,
                                fileChooserParams: FileChooserParams?
                            ): Boolean {
                                pendingFileChooserCallback?.onReceiveValue(null)
                                pendingFileChooserCallback = filePathCallback
                                filePickerLauncher.launch(arrayOf("*/*"))
                                return true
                            }
                        }

                        webViewRef = this
                        if (!isOnline && archiveFile.exists() && archiveFile.length() > 1024L) {
                            // Try Chromium Service Worker cache first; fallback to .mht automatically via onReceivedError
                            settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                        }
                        loadUrl(KIWIX_PWA_URL)
                    }
                },
                update = { wv ->
                    val desiredCache = if (isOnline) WebSettings.LOAD_DEFAULT else WebSettings.LOAD_CACHE_ELSE_NETWORK
                    if (wv.settings.cacheMode != desiredCache) {
                        wv.settings.cacheMode = desiredCache
                    }
                }
            )
        }
    }
}

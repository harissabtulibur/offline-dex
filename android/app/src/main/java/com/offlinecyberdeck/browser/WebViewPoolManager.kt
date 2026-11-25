package com.offlinecyberdeck.browser

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

data class WebViewCallbacks(
    val onPageStarted: (tabId: String, url: String) -> Unit,
    val onPageFinished: (tabId: String, url: String, title: String, canBack: Boolean, canForward: Boolean) -> Unit,
    val onProgressChanged: (tabId: String, progress: Int) -> Unit,
    val onTitleChanged: (tabId: String, title: String) -> Unit,
    val onFaviconChanged: (tabId: String, favicon: Bitmap?) -> Unit,
    val onSslErrorBlocked: (tabId: String, url: String, reason: String) -> Unit,
    val onExternalSchemeRequested: (tabId: String, uriString: String) -> Unit,
    val onDownloadRequested: (url: String, fileName: String, mimeType: String) -> Unit,
    val onSitePermissionPrompt: (tabId: String, origin: String, resources: List<String>, request: PermissionRequest) -> Unit,
    val onFindMatchesUpdated: (tabId: String, activeOrdinal: Int, count: Int) -> Unit,
    val onLinkLongPressed: (tabId: String, linkUrl: String?, imageUrl: String?) -> Unit,
    val onFileChooserRequested: (callback: ValueCallback<Array<Uri>>, acceptTypes: Array<String>) -> Unit,
    val onShowCustomView: (view: View, callback: WebChromeClient.CustomViewCallback) -> Unit,
    val onHideCustomView: () -> Unit,
    val onJsDialog: (tabId: String, isConfirm: Boolean, message: String, result: JsResult) -> Unit
)

class WebViewPoolManager(
    private val context: Context,
    private val callbacks: WebViewCallbacks
) {
    private val liveViews = LinkedHashMap<String, WebView>(4, 0.75f, true)
    private val savedBundles = HashMap<String, Bundle>()
    private val darkWebMap = HashMap<String, Boolean>()
    private var maxLiveCount = 3

    fun setMaxLiveCount(limit: Int) {
        maxLiveCount = limit.coerceIn(1, 5)
        evictExcess(activeTabId = null)
    }

    fun setDarkWebAllTabs(enabled: Boolean) {
        liveViews.forEach { (tabId, webView) ->
            darkWebMap[tabId] = enabled
            applyDarkModeToWebView(webView, enabled)
        }
    }

    fun getOrCreateWebView(
        tabId: String,
        url: String,
        isIncognito: Boolean,
        desktopMode: Boolean,
        javaScriptEnabled: Boolean,
        forceDarkWeb: Boolean,
        textZoom: Int = 100
    ): WebView {
        darkWebMap[tabId] = forceDarkWeb
        val existing = liveViews[tabId]
        if (existing != null) {
            applyRuntimeSettings(existing, desktopMode, javaScriptEnabled, forceDarkWeb, textZoom)
            existing.onResume()
            return existing
        }

        evictExcess(activeTabId = tabId)

        val webView = createConfiguredWebView(
            tabId = tabId,
            isIncognito = isIncognito,
            desktopMode = desktopMode,
            javaScriptEnabled = javaScriptEnabled,
            forceDarkWeb = forceDarkWeb,
            textZoom = textZoom
        )
        liveViews[tabId] = webView

        val saved = savedBundles.remove(tabId)
        if (saved != null) {
            webView.restoreState(saved)
        } else if (url.startsWith("http://") || url.startsWith("https://")) {
            webView.loadUrl(url)
        }
        return webView
    }

    fun getLiveWebView(tabId: String): WebView? = liveViews[tabId]

    fun getLiveCount(): Int = liveViews.size

    fun clearAllWebViewCaches() {
        liveViews.values.forEach { view ->
            runCatching { view.clearCache(true) }
        }
    }

    fun pauseInactiveTabs(activeTabId: String) {
        liveViews.forEach { (id, view) ->
            if (id != activeTabId) {
                view.onPause()
            } else {
                view.onResume()
            }
        }
    }

    fun destroyTab(tabId: String, isIncognito: Boolean) {
        savedBundles.remove(tabId)
        darkWebMap.remove(tabId)
        val view = liveViews.remove(tabId) ?: return
        destroyWebViewSafely(view, clearPrivateData = isIncognito)
    }

    fun clearAllIncognitoSessionData() {
        runCatching {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            WebStorage.getInstance().deleteAllData()
        }
    }

    fun destroyAll() {
        liveViews.values.forEach { destroyWebViewSafely(it, clearPrivateData = false) }
        liveViews.clear()
        savedBundles.clear()
    }

    private fun evictExcess(activeTabId: String?) {
        while (liveViews.size >= maxLiveCount) {
            val victimKey = liveViews.keys.firstOrNull { it != activeTabId } ?: break
            val victim = liveViews.remove(victimKey) ?: break
            val bundle = Bundle()
            runCatching { victim.saveState(bundle) }
            if (!bundle.isEmpty) {
                savedBundles[victimKey] = bundle
            }
            destroyWebViewSafely(victim, clearPrivateData = false)
        }
    }

    private fun destroyWebViewSafely(webView: WebView, clearPrivateData: Boolean) {
        runCatching {
            webView.stopLoading()
            webView.onPause()
            if (clearPrivateData) {
                webView.clearHistory()
                webView.clearCache(true)
                webView.clearFormData()
            }
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createConfiguredWebView(
        tabId: String,
        isIncognito: Boolean,
        desktopMode: Boolean,
        javaScriptEnabled: Boolean,
        forceDarkWeb: Boolean,
        textZoom: Int
    ): WebView {
        return WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            settings.apply {
                this.javaScriptEnabled = javaScriptEnabled
                domStorageEnabled = !isIncognito
                databaseEnabled = !isIncognito
                saveFormData = !isIncognito
                allowFileAccess = false
                allowContentAccess = false
                @Suppress("DEPRECATION")
                allowFileAccessFromFileURLs = false
                @Suppress("DEPRECATION")
                allowUniversalAccessFromFileURLs = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                builtInZoomControls = true
                displayZoomControls = false
                loadWithOverviewMode = true
                useWideViewPort = true
                setSupportMultipleWindows(false)
                safeBrowsingEnabled = true
                cacheMode = if (isIncognito) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
            }

            applyRuntimeSettings(this, desktopMode, javaScriptEnabled, forceDarkWeb, textZoom)

            if (isIncognito) {
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
            }

            setFindListener { activeMatchOrdinal, numberOfMatches, isDoneCounting ->
                if (isDoneCounting) {
                    callbacks.onFindMatchesUpdated(tabId, activeMatchOrdinal, numberOfMatches)
                }
            }

            setDownloadListener(DownloadListener { url, _, contentDisposition, mimeType, _ ->
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
                    callbacks.onDownloadRequested(url, fileName, mimeType ?: "application/octet-stream")
                }
            })

            setOnLongClickListener { view ->
                val wv = view as? WebView ?: return@setOnLongClickListener false
                val hit = wv.hitTestResult
                when (hit.type) {
                    WebView.HitTestResult.SRC_ANCHOR_TYPE -> {
                        val url = hit.extra
                        if (!url.isNullOrBlank()) {
                            callbacks.onLinkLongPressed(tabId, url, null)
                            true
                        } else false
                    }
                    WebView.HitTestResult.IMAGE_TYPE -> {
                        val imgUrl = hit.extra
                        if (!imgUrl.isNullOrBlank()) {
                            callbacks.onLinkLongPressed(tabId, null, imgUrl)
                            true
                        } else false
                    }
                    WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                        val handler = Handler(Looper.getMainLooper()) { msg ->
                            val href = msg.data.getString("url")
                            val src = msg.data.getString("src") ?: hit.extra
                            if (!href.isNullOrBlank() || !src.isNullOrBlank()) {
                                callbacks.onLinkLongPressed(tabId, href, src)
                            }
                            true
                        }
                        wv.requestFocusNodeHref(Message.obtain(handler))
                        true
                    }
                    else -> false
                }
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val uri = request?.url ?: return false
                    val scheme = uri.scheme?.lowercase() ?: return false
                    if (scheme == "http" || scheme == "https" || scheme == "about") {
                        return false
                    }
                    callbacks.onExternalSchemeRequested(tabId, uri.toString())
                    return true
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    if (!url.isNullOrBlank()) {
                        callbacks.onPageStarted(tabId, url)
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (view != null && !url.isNullOrBlank()) {
                        val isDark = darkWebMap[tabId] == true
                        applyDarkModeScript(view, isDark)
                        callbacks.onPageFinished(
                            tabId,
                            url,
                            view.title.orEmpty().ifBlank { url },
                            view.canGoBack(),
                            view.canGoForward()
                        )
                    }
                }

                override fun onReceivedSslError(
                    view: WebView?,
                    handler: SslErrorHandler?,
                    error: SslError?
                ) {
                    handler?.cancel()
                    val reason = when (error?.primaryError) {
                        SslError.SSL_EXPIRED -> "Certificate expired"
                        SslError.SSL_IDMISMATCH -> "Hostname mismatch"
                        SslError.SSL_UNTRUSTED -> "Untrusted certificate authority"
                        SslError.SSL_NOTYETVALID -> "Certificate not yet valid"
                        else -> "Invalid SSL/TLS certificate"
                    }
                    callbacks.onSslErrorBlocked(tabId, error?.url.orEmpty(), reason)
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    callbacks.onProgressChanged(tabId, newProgress)
                }

                override fun onReceivedTitle(view: WebView?, title: String?) {
                    if (!title.isNullOrBlank()) {
                        callbacks.onTitleChanged(tabId, title)
                    }
                }

                override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
                    val scaled = icon?.let {
                        runCatching { Bitmap.createScaledBitmap(it, 36, 36, true) }.getOrNull()
                    }
                    callbacks.onFaviconChanged(tabId, scaled)
                }

                override fun onPermissionRequest(request: PermissionRequest?) {
                    if (request == null) return
                    val origin = request.origin?.toString().orEmpty()
                    val res = request.resources?.toList().orEmpty()
                    callbacks.onSitePermissionPrompt(tabId, origin, res, request)
                }

                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    if (filePathCallback == null) return false
                    val accept = fileChooserParams?.acceptTypes
                        ?.filter { it.isNotBlank() }
                        ?.toTypedArray()
                        ?.takeIf { it.isNotEmpty() }
                        ?: arrayOf("*/*")
                    callbacks.onFileChooserRequested(filePathCallback, accept)
                    return true
                }

                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    if (view != null && callback != null) {
                        callbacks.onShowCustomView(view, callback)
                    }
                }

                override fun onHideCustomView() {
                    callbacks.onHideCustomView()
                }

                override fun onJsAlert(
                    view: WebView?,
                    url: String?,
                    message: String?,
                    result: JsResult?
                ): Boolean {
                    if (result == null) return false
                    callbacks.onJsDialog(tabId, false, message.orEmpty(), result)
                    return true
                }

                override fun onJsConfirm(
                    view: WebView?,
                    url: String?,
                    message: String?,
                    result: JsResult?
                ): Boolean {
                    if (result == null) return false
                    callbacks.onJsDialog(tabId, true, message.orEmpty(), result)
                    return true
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun applyRuntimeSettings(
        webView: WebView,
        desktopMode: Boolean,
        javaScriptEnabled: Boolean,
        forceDarkWeb: Boolean,
        textZoom: Int
    ) {
        webView.settings.javaScriptEnabled = javaScriptEnabled
        webView.settings.textZoom = textZoom.coerceIn(50, 200)
        val defaultUa = WebSettings.getDefaultUserAgent(context)
        webView.settings.userAgentString = if (desktopMode) {
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
        } else {
            defaultUa
        }
        applyDarkModeToWebView(webView, forceDarkWeb)
    }

    private fun applyDarkModeToWebView(webView: WebView, enabled: Boolean) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            runCatching { WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, enabled) }
        }
        applyDarkModeScript(webView, enabled)
    }

    private fun applyDarkModeScript(webView: WebView, enabled: Boolean) {
        val js = if (enabled) {
            """
            (function(){
              try {
                document.documentElement.style.colorScheme = 'dark';
                var id = 'cd-webview-dark';
                var body = document.body || document.documentElement;
                var bg = window.getComputedStyle(body).backgroundColor || '';
                var m = bg.match(/rgba?\((\d+),\s*(\d+),\s*(\d+)(?:,\s*([\d.]+))?\)/);
                var isLightOrTransparent = true;
                if (m) {
                  var a = m[4] !== undefined ? parseFloat(m[4]) : 1;
                  var lum = (parseInt(m[1]) + parseInt(m[2]) + parseInt(m[3])) / 3;
                  if (a > 0.2 && lum < 120) isLightOrTransparent = false;
                }
                if (isLightOrTransparent && !document.getElementById(id)) {
                  var s = document.createElement('style');
                  s.id = id;
                  s.textContent = 'html { filter: invert(0.9) hue-rotate(180deg) !important; background: #121212 !important; } img, video, picture, canvas, svg, iframe { filter: invert(1) hue-rotate(180deg) !important; }';
                  (document.head || document.documentElement).appendChild(s);
                }
              } catch(e){}
            })();
            """.trimIndent()
        } else {
            """
            (function(){
              try {
                document.documentElement.style.colorScheme = 'light';
                var el = document.getElementById('cd-webview-dark');
                if (el) el.remove();
              } catch(e){}
            })();
            """.trimIndent()
        }
        runCatching { webView.evaluateJavascript(js, null) }
    }
}

package com.offlinecyberdeck.browser

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offlinecyberdeck.core.database.entity.BookmarkEntity
import com.offlinecyberdeck.core.database.entity.HistoryEntity
import com.offlinecyberdeck.core.format.Formatters
import com.offlinecyberdeck.core.model.ThemeMode
import com.offlinecyberdeck.ui.theme.DeckAmber
import com.offlinecyberdeck.ui.theme.DeckGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    onOpenDownloads: () -> Unit,
    onOpenUnifiedSearch: () -> Unit,
    onOpenArchives: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onReturnToLauncher: (() -> Unit)? = null,
    onShowMessage: (String) -> Unit
) {
    val tabs by viewModel.tabs.collectAsStateWithLifecycle()
    val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
    val tabFavicons by viewModel.tabFavicons.collectAsStateWithLifecycle()
    val addressInput by viewModel.addressBarInput.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val transportLabel by viewModel.transportLabel.collectAsStateWithLifecycle()
    val driveStatus by viewModel.driveStatus.collectAsStateWithLifecycle()
    val findVisible by viewModel.findInPageVisible.collectAsStateWithLifecycle()
    val findQuery by viewModel.findQuery.collectAsStateWithLifecycle()
    val findStats by viewModel.findStats.collectAsStateWithLifecycle()
    val isArchiving by viewModel.isArchiving.collectAsStateWithLifecycle()
    val pendingExternal by viewModel.pendingExternalScheme.collectAsStateWithLifecycle()
    val pendingPerm by viewModel.pendingSitePermission.collectAsStateWithLifecycle()
    val pendingDownload by viewModel.pendingDownloadPrompt.collectAsStateWithLifecycle()
    val pendingLongPress by viewModel.pendingLongPressLink.collectAsStateWithLifecycle()
    val pendingJsDialog by viewModel.pendingJsDialog.collectAsStateWithLifecycle()
    val pendingFileChooser by viewModel.pendingFileChooser.collectAsStateWithLifecycle()
    val fullscreenCustomView by viewModel.fullscreenCustomView.collectAsStateWithLifecycle()
    val inspectedHtml by viewModel.inspectedHtmlSource.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var landscapeToolbarRevealed by remember(isLandscape, settings.hideToolbarInLandscape) {
        mutableStateOf(false)
    }
    val shouldHideToolbarInLandscape = settings.hideToolbarInLandscape && isLandscape && !landscapeToolbarRevealed
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showTabSwitcher by remember { mutableStateOf(false) }

    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        viewModel.resolveFileChooser(if (uris.isEmpty()) null else uris.toTypedArray())
    }

    LaunchedEffect(pendingFileChooser) {
        val pending = pendingFileChooser
        if (pending != null) {
            fileChooserLauncher.launch(pending.acceptTypes)
        }
    }

    val currentTab = activeTab
    BackHandler(enabled = fullscreenCustomView != null || onReturnToLauncher != null) {
        if (fullscreenCustomView != null) {
            viewModel.exitFullscreenCustomView()
        } else if (currentTab != null && (currentTab.canGoBack || currentTab.url != "about:cyberdeck")) {
            viewModel.goBack()
        } else {
            onReturnToLauncher?.invoke()
        }
    }
    val isBookmarked = currentTab != null &&
        currentTab.url != "about:cyberdeck" &&
        bookmarks.any { it.url == currentTab.url }

    if (fullscreenCustomView != null) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { fullscreenCustomView!! },
                modifier = Modifier.fillMaxSize()
            )
            IconButton(
                onClick = { viewModel.exitFullscreenCustomView() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Exit Fullscreen", tint = Color.White)
            }
        }
        return
    }

    val btnSize = settings.toolbarButtonSizeDp.coerceIn(26, 44).dp
    val barHeight = settings.addressBarHeightDp.coerceIn(30, 64).dp
    val barCorner = settings.addressBarCornerDp.coerceIn(0, 24).dp
    val barFontSp = settings.addressBarFontSp.coerceIn(11, 20).sp

    val renderTabStrip: @Composable () -> Unit = {
        if (settings.showTabStrip) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEach { tab ->
                        val selected = tab.id == currentTab?.id
                        val favicon = tabFavicons[tab.id]
                        Surface(
                            color = if (selected) MaterialTheme.colorScheme.surfaceVariant
                            else MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(6.dp),
                            tonalElevation = if (selected) 2.dp else 0.dp,
                            modifier = Modifier
                                .widthIn(min = 110.dp, max = 190.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { viewModel.selectTab(tab.id) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (tab.isIncognito) {
                                    Icon(
                                        imageVector = Icons.Filled.VisibilityOff,
                                        contentDescription = null,
                                        tint = DeckAmber,
                                        modifier = Modifier.size(14.dp)
                                    )
                                } else if (favicon != null) {
                                    Image(
                                        bitmap = favicon.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp).clip(RoundedCornerShape(2.dp))
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Filled.Public,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = tab.title.ifBlank { "New Tab" },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Close tab",
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { viewModel.closeTab(tab.id) }
                                )
                            }
                        }
                    }
                }
                IconButton(
                    onClick = { viewModel.createNewTab(isIncognito = false) },
                    modifier = Modifier.size(btnSize)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "New Tab")
                }
                IconButton(
                    onClick = { viewModel.createNewTab(isIncognito = true) },
                    modifier = Modifier.size(btnSize)
                ) {
                    Icon(
                        Icons.Filled.VisibilityOff,
                        contentDescription = "New Private Tab",
                        tint = DeckAmber
                    )
                }
            }
        }
    }

    val renderToolbarAndBars: @Composable () -> Unit = {
        // Main Browser Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { viewModel.goBack() },
                enabled = currentTab?.canGoBack == true || currentTab?.url != "about:cyberdeck",
                modifier = Modifier.size(btnSize)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            IconButton(
                onClick = { viewModel.goForward() },
                enabled = currentTab?.canGoForward == true,
                modifier = Modifier.size(btnSize)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward")
            }
            IconButton(
                onClick = { viewModel.reloadOrStop() },
                modifier = Modifier.size(btnSize)
            ) {
                Icon(
                    imageVector = if (currentTab?.isLoading == true) Icons.Filled.Close else Icons.Filled.Refresh,
                    contentDescription = if (currentTab?.isLoading == true) "Stop" else "Reload"
                )
            }
            if (onReturnToLauncher != null) {
                IconButton(
                    onClick = onReturnToLauncher,
                    modifier = Modifier.size(btnSize)
                ) {
                    Icon(
                        Icons.Filled.GridView,
                        contentDescription = "Kembali ke Launcher",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            IconButton(
                onClick = { viewModel.goHome() },
                modifier = Modifier.size(btnSize)
            ) {
                Icon(Icons.Filled.Home, contentDescription = "Home")
            }

            // Customizable Address / Search Bar
            Surface(
                shape = RoundedCornerShape(barCorner),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
                    .heightIn(min = barHeight)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentTab?.url?.startsWith("https://") == true) {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = "HTTPS",
                            tint = DeckGreen,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (addressInput.isEmpty()) {
                            Text(
                                text = "Search Google or enter URL",
                                fontSize = barFontSp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        BasicTextField(
                            value = addressInput,
                            onValueChange = { viewModel.onAddressInputChange(it) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = barFontSp,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(
                                onGo = {
                                    focusManager.clearFocus()
                                    viewModel.navigateFromInput(addressInput)
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (addressInput.isNotEmpty()) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Clear URL",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { viewModel.onAddressInputChange("") }
                        )
                    }
                }
            }

            IconButton(
                onClick = { viewModel.toggleWebViewDarkMode() },
                modifier = Modifier.size(btnSize)
            ) {
                Icon(
                    Icons.Filled.Contrast,
                    contentDescription = "Mode Gelap WebView",
                    tint = if (settings.webViewDarkMode) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = { viewModel.toggleDesktopMode() },
                modifier = Modifier.size(btnSize)
            ) {
                Icon(
                    Icons.Filled.DesktopWindows,
                    contentDescription = "Toggle Desktop Site",
                    tint = if (currentTab?.isDesktopMode == true) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = { viewModel.toggleBookmarkCurrentPage() },
                enabled = currentTab?.url != "about:cyberdeck",
                modifier = Modifier.size(btnSize)
            ) {
                Icon(
                    imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                    contentDescription = "Bookmark",
                    tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }

            IconButton(
                onClick = { viewModel.archiveCurrentPage() },
                enabled = !isArchiving && currentTab?.url != "about:cyberdeck",
                modifier = Modifier.size(btnSize)
            ) {
                Icon(
                    Icons.Filled.Archive,
                    contentDescription = "Archive Page Offline",
                    tint = if (isArchiving) DeckAmber else MaterialTheme.colorScheme.primary
                )
            }

            IconButton(
                onClick = onOpenDownloads,
                modifier = Modifier.size(btnSize)
            ) {
                Icon(Icons.Filled.Download, contentDescription = "Downloads")
            }

            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .clickable { showTabSwitcher = true }
                    .padding(horizontal = 3.dp)
            ) {
                Text(
                    text = tabs.size.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                )
            }

            Box {
                IconButton(
                    onClick = { showOverflowMenu = true },
                    modifier = Modifier.size(btnSize)
                ) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Browser Menu")
                }
                DropdownMenu(
                    expanded = showOverflowMenu,
                    onDismissRequest = { showOverflowMenu = false },
                    modifier = Modifier.widthIn(min = 220.dp, max = 280.dp)
                ) {
                    if (onReturnToLauncher != null) {
                        DropdownMenuItem(
                            text = { Text("Kembali ke DeX Launcher") },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.GridView,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            onClick = {
                                showOverflowMenu = false
                                onReturnToLauncher()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (settings.immersiveFullscreen || settings.hideNavChromeInBrowser)
                                    "Exit Fullscreen"
                                else "Fullscreen Mode"
                            )
                        },
                        leadingIcon = {
                            Icon(
                                if (settings.immersiveFullscreen || settings.hideNavChromeInBrowser)
                                    Icons.Filled.FullscreenExit
                                else Icons.Filled.Fullscreen,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            showOverflowMenu = false
                            viewModel.toggleBrowserFullscreen()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Archive Page Offline") },
                        onClick = {
                            showOverflowMenu = false
                            viewModel.archiveCurrentPage()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Saved Archives") },
                        onClick = {
                            showOverflowMenu = false
                            onOpenArchives()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Bookmarks Manager") },
                        onClick = {
                            showOverflowMenu = false
                            onOpenBookmarks()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Browsing History") },
                        onClick = {
                            showOverflowMenu = false
                            onOpenHistory()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Find in Page") },
                        onClick = {
                            showOverflowMenu = false
                            viewModel.openFindInPage()
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (settings.webViewDarkMode) "WebView Dark Mode: ON"
                                else "WebView Dark Mode: OFF"
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Contrast,
                                contentDescription = null,
                                tint = if (settings.webViewDarkMode) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        onClick = {
                            showOverflowMenu = false
                            viewModel.toggleWebViewDarkMode()
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (currentTab?.isDesktopMode == true) "Desktop Site: ON"
                                else "Desktop Site: OFF"
                            )
                        },
                        onClick = {
                            showOverflowMenu = false
                            viewModel.toggleDesktopMode()
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (currentTab?.javaScriptEnabled == true) "Site JavaScript: ON"
                                else "Site JavaScript: OFF"
                            )
                        },
                        onClick = {
                            showOverflowMenu = false
                            viewModel.toggleJavaScriptForCurrentSite()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Save Public Video Metadata") },
                        onClick = {
                            showOverflowMenu = false
                            viewModel.saveVisibleVideoMetadataFromCurrentPage()
                        }
                    )
                    if (settings.developerModeEnabled) {
                        DropdownMenuItem(
                            text = { Text("Inspect Page HTML Source") },
                            leadingIcon = { Icon(Icons.Filled.Code, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                viewModel.inspectCurrentPageHtml()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Copy URL") },
                        onClick = {
                            showOverflowMenu = false
                            val url = currentTab?.url.orEmpty()
                            if (url.isNotBlank() && url != "about:cyberdeck") {
                                clipboard.setText(AnnotatedString(url))
                                onShowMessage("URL copied")
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Share URL") },
                        onClick = {
                            showOverflowMenu = false
                            val url = currentTab?.url.orEmpty()
                            if (url.isNotBlank() && url != "about:cyberdeck") {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, url)
                                    putExtra(Intent.EXTRA_SUBJECT, currentTab?.title.orEmpty())
                                }
                                context.startActivity(Intent.createChooser(intent, "Share link"))
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("New Private Tab") },
                        onClick = {
                            showOverflowMenu = false
                            viewModel.createNewTab(isIncognito = true)
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Developer Settings",
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                        onClick = {
                            showOverflowMenu = false
                            onOpenSettings()
                        }
                    )
                }
            }
        }

        if (currentTab?.isLoading == true) {
            LinearProgressIndicator(
                progress = { (currentTab.progress.coerceIn(5, 100)) / 100f },
                modifier = Modifier.fillMaxWidth().height(2.dp)
            )
        }

        if (settings.showStatusStrip) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isOnline) transportLabel else "OFFLINE MODE",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isOnline) DeckGreen else DeckAmber
                )
                val driveText = if (driveStatus.isConnected) {
                    val avail = Formatters.formatBytes(driveStatus.availableBytes)
                    "SSD: ${driveStatus.displayName} ($avail free)"
                } else {
                    "SSD: ${driveStatus.displayName}"
                }
                Text(
                    text = driveText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (findVisible) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = findQuery,
                    onValueChange = { viewModel.updateFindQuery(it) },
                    singleLine = true,
                    placeholder = { Text("Find in page") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (findStats.second > 0) "${findStats.first + 1}/${findStats.second}" else "0/0",
                    style = MaterialTheme.typography.labelMedium
                )
                IconButton(onClick = { viewModel.findNext(false) }) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Previous match")
                }
                IconButton(onClick = { viewModel.findNext(true) }) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Next match")
                }
                IconButton(onClick = { viewModel.closeFindInPage() }) {
                    Icon(Icons.Filled.Close, contentDescription = "Close Find")
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.isCtrlPressed) {
                    when (event.key) {
                        Key.T -> {
                            viewModel.createNewTab(isIncognito = false)
                            true
                        }
                        Key.W -> {
                            currentTab?.let { viewModel.closeTab(it.id) }
                            true
                        }
                        Key.F -> {
                            viewModel.openFindInPage()
                            true
                        }
                        Key.R -> {
                            viewModel.reloadOrStop()
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        if (!settings.bottomAddressBar && !shouldHideToolbarInLandscape) {
            renderTabStrip()
            renderToolbarAndBars()
        }

        if (currentTab?.sslBlockedWarning != null) {
            Surface(
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.18f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = currentTab.sslBlockedWarning,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Browser Viewport: New Tab Page OR Live WebView
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (currentTab == null || currentTab.url == "about:cyberdeck") {
                NewTabPage(
                    isIncognito = currentTab?.isIncognito == true,
                    isOnline = isOnline,
                    driveName = driveStatus.displayName,
                    driveConnected = driveStatus.isConnected,
                    bookmarks = bookmarks.take(6),
                    recentHistory = if (settings.showRecentOnNewTab) history.take(6) else emptyList(),
                    onNavigate = { viewModel.navigateFromInput(it) },
                    onOpenUnifiedSearch = onOpenUnifiedSearch,
                    onOpenArchives = onOpenArchives,
                    onOpenBookmarks = onOpenBookmarks,
                    onOpenHistory = onOpenHistory
                )
            } else {
                key(currentTab.id) {
                    val webView = remember(
                        currentTab.id,
                        currentTab.isDesktopMode,
                        currentTab.javaScriptEnabled,
                        settings.webViewDarkMode,
                        settings.webViewTextZoom
                    ) {
                        viewModel.webViewPool.getOrCreateWebView(
                            tabId = currentTab.id,
                            url = currentTab.url,
                            isIncognito = currentTab.isIncognito,
                            desktopMode = currentTab.isDesktopMode,
                            javaScriptEnabled = currentTab.javaScriptEnabled,
                            forceDarkWeb = settings.webViewDarkMode,
                            textZoom = settings.webViewTextZoom
                        )
                    }
                    AndroidView(
                        factory = {
                            (webView.parent as? ViewGroup)?.removeView(webView)
                            webView
                        },
                        update = { view ->
                            if (view.url.isNullOrBlank() &&
                                (currentTab.url.startsWith("http://") || currentTab.url.startsWith("https://"))
                            ) {
                                view.loadUrl(currentTab.url)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            if (settings.hideToolbarInLandscape && isLandscape) {
                val arrowAlign = if (settings.bottomAddressBar) Alignment.BottomEnd else Alignment.TopEnd
                val arrowIcon = when {
                    shouldHideToolbarInLandscape && settings.bottomAddressBar -> Icons.Filled.KeyboardArrowUp
                    shouldHideToolbarInLandscape -> Icons.Filled.KeyboardArrowDown
                    settings.bottomAddressBar -> Icons.Filled.KeyboardArrowDown
                    else -> Icons.Filled.KeyboardArrowUp
                }
                val arrowLabel = if (shouldHideToolbarInLandscape) "Show Toolbar" else "Hide Toolbar"
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)),
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .align(arrowAlign)
                        .padding(10.dp)
                        .clickable { landscapeToolbarRevealed = !landscapeToolbarRevealed }
                ) {
                    Icon(
                        imageVector = arrowIcon,
                        contentDescription = arrowLabel,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(8.dp).size(22.dp)
                    )
                }
            }

            if (settings.immersiveFullscreen || settings.hideNavChromeInBrowser) {
                Surface(
                    color = Color.Black.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(
                            end = if (settings.hideToolbarInLandscape && isLandscape && settings.bottomAddressBar) 62.dp else 12.dp,
                            bottom = 12.dp
                        )
                        .clickable { viewModel.toggleBrowserFullscreen() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Filled.FullscreenExit,
                            contentDescription = "Exit Fullscreen",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text("Exit Full", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        if (settings.bottomAddressBar && !shouldHideToolbarInLandscape) {
            renderToolbarAndBars()
            renderTabStrip()
        }
    }

    if (showTabSwitcher) {
        ModalBottomSheet(onDismissRequest = { showTabSwitcher = false }) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Open Tabs (${tabs.size})", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            showTabSwitcher = false
                            viewModel.createNewTab(isIncognito = true)
                        }) {
                            Text("Private Tab")
                        }
                        Button(onClick = {
                            showTabSwitcher = false
                            viewModel.createNewTab(isIncognito = false)
                        }) {
                            Text("New Tab")
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(tabs, key = { it.id }) { tab ->
                        val fav = tabFavicons[tab.id]
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (tab.id == currentTab?.id)
                                    MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.selectTab(tab.id)
                                    showTabSwitcher = false
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (tab.isIncognito) {
                                    Icon(Icons.Filled.VisibilityOff, contentDescription = null, tint = DeckAmber)
                                } else if (fav != null) {
                                    Image(
                                        bitmap = fav.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp).clip(RoundedCornerShape(4.dp))
                                    )
                                } else {
                                    Icon(Icons.Filled.Public, contentDescription = null)
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(tab.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        tab.url,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = { viewModel.closeTab(tab.id) }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Close tab")
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    pendingLongPress?.let { target ->
        val primaryUrl = target.linkUrl ?: target.imageUrl.orEmpty()
        AlertDialog(
            onDismissRequest = { viewModel.dismissLongPressLink() },
            title = { Text(if (target.linkUrl != null) "Link Actions" else "Image Actions") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        primaryUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (target.linkUrl != null) {
                        OutlinedButton(
                            onClick = {
                                viewModel.createNewTab(url = target.linkUrl, isIncognito = false)
                                viewModel.dismissLongPressLink()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Open in New Tab") }

                        OutlinedButton(
                            onClick = {
                                viewModel.createNewTab(url = target.linkUrl, isIncognito = true)
                                viewModel.dismissLongPressLink()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Open in Private Tab") }
                    }
                    OutlinedButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(primaryUrl))
                            viewModel.dismissLongPressLink()
                            onShowMessage("Link copied")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Copy URL") }

                    val downloadTarget = target.imageUrl ?: target.linkUrl
                    if (!downloadTarget.isNullOrBlank()) {
                        Button(
                            onClick = {
                                viewModel.startDirectDownload(downloadTarget)
                                viewModel.dismissLongPressLink()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (target.imageUrl != null) "Download Image" else "Download Link")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissLongPressLink() }) { Text("Close") }
            }
        )
    }

    pendingJsDialog?.let { dlg ->
        AlertDialog(
            onDismissRequest = { viewModel.resolveJsDialog(false) },
            title = { Text("Page Message") },
            text = { Text(dlg.message) },
            confirmButton = {
                Button(onClick = { viewModel.resolveJsDialog(true) }) { Text("OK") }
            },
            dismissButton = if (dlg.isConfirm) {
                {
                    TextButton(onClick = { viewModel.resolveJsDialog(false) }) { Text("Cancel") }
                }
            } else null
        )
    }

    pendingExternal?.let { req ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissExternalScheme() },
            title = { Text("Open External Link?") },
            text = {
                Text("This page requested opening an external URI:\n\n${req.uriString}\n\nOnly open links you trust.")
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.dismissExternalScheme()
                    runCatching {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(req.uriString)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }.onFailure {
                        onShowMessage("No app available to handle this link.")
                    }
                }) { Text("Open App") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissExternalScheme() }) { Text("Cancel") }
            }
        )
    }

    pendingPerm?.let { perm ->
        AlertDialog(
            onDismissRequest = { viewModel.resolveSitePermission(false) },
            title = { Text("Site Permission Request") },
            text = {
                Text("Origin: ${perm.origin}\nRequests: ${perm.resources.joinToString(", ")}")
            },
            confirmButton = {
                Button(onClick = { viewModel.resolveSitePermission(true) }) { Text("Allow") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.resolveSitePermission(false) }) { Text("Deny") }
            }
        )
    }

    pendingDownload?.let { dl ->
        AlertDialog(
            onDismissRequest = { viewModel.confirmPendingDownload(false) },
            title = { Text("Download File?") },
            text = { Text("File: ${dl.fileName}\nSource: ${dl.url}") },
            confirmButton = {
                Button(onClick = { viewModel.confirmPendingDownload(true) }) { Text("Download") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.confirmPendingDownload(false) }) { Text("Cancel") }
            }
        )
    }

    inspectedHtml?.let { source ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissInspectedHtml() },
            title = { Text("Page HTML Source (Dev Inspector)") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = source,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    clipboard.setText(AnnotatedString(source))
                    onShowMessage("HTML source copied (${source.length} chars)")
                }) { Text("Copy HTML") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissInspectedHtml() }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun NewTabPage(
    isIncognito: Boolean,
    isOnline: Boolean,
    driveName: String,
    driveConnected: Boolean,
    bookmarks: List<BookmarkEntity>,
    recentHistory: List<HistoryEntity>,
    onNavigate: (String) -> Unit,
    onOpenUnifiedSearch: () -> Unit,
    onOpenArchives: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenHistory: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = if (isIncognito) "PRIVATE SESSION" else "OFFLINE CYBER DECK",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isIncognito) DeckAmber else MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (isIncognito) {
                        "Local privacy mode: history and bookmarks are not saved automatically, and cookies/cache clear when all private tabs close. This does not anonymize network traffic."
                    } else {
                        if (isOnline) "Connected • Browse with Google, archive pages for offline replay, or search your local vault."
                        else "Offline • Web browsing requires connectivity, while your archives, Knowledge Vault, and Offline Tube remain ready."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Search Google or enter URL...") },
                trailingIcon = {
                    IconButton(onClick = { if (query.isNotBlank()) onNavigate(query) }) {
                        Icon(Icons.Filled.Search, contentDescription = "Go")
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { if (query.isNotBlank()) onNavigate(query) }),
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onOpenUnifiedSearch) {
                    Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Offline Search")
                }
                OutlinedButton(onClick = onOpenArchives) {
                    Icon(Icons.Filled.Archive, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Archives")
                }
                OutlinedButton(onClick = onOpenBookmarks) {
                    Icon(Icons.Filled.Bookmark, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Bookmarks")
                }
                OutlinedButton(onClick = onOpenHistory) {
                    Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("History")
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Filled.Storage,
                        contentDescription = null,
                        tint = if (driveConnected) DeckGreen else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column {
                        Text("Deck Drive: $driveName", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = if (driveConnected) "USB-C external storage connected & ready"
                            else "Configure an external USB-C SSD in Settings → Deck Drive",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (bookmarks.isNotEmpty()) {
            item {
                Text("Bookmarks", style = MaterialTheme.typography.titleMedium)
            }
            items(bookmarks, key = { it.id }) { bm ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onNavigate(bm.url) }
                        .padding(vertical = 8.dp, horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Bookmark,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(bm.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            bm.url,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            }
        }

        if (recentHistory.isNotEmpty()) {
            item {
                Text("Recent Pages", style = MaterialTheme.typography.titleMedium)
            }
            items(recentHistory, key = { it.id }) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onNavigate(item.url) }
                        .padding(vertical = 8.dp, horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Public, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            item.url,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

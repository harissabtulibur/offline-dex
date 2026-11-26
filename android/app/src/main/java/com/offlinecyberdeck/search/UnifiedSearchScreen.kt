package com.offlinecyberdeck.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offlinecyberdeck.CyberDeckApplication
import com.offlinecyberdeck.core.model.SearchResultType
import com.offlinecyberdeck.core.model.UnifiedSearchResult
import com.offlinecyberdeck.desktop.DesktopLauncherManager
import com.offlinecyberdeck.ui.theme.DeckAmber
import com.offlinecyberdeck.ui.theme.DeckGreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun UnifiedSearchScreen(
    app: CyberDeckApplication,
    onOpenArchive: (String) -> Unit,
    onOpenLibraryItem: (String) -> Unit,
    onOpenMediaItem: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenFileExplorer: (() -> Unit)? = null,
    isWindowFocused: Boolean = true,
    onTextInputFocusChanged: (Boolean) -> Unit = {},
    onShowMessage: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val searchFocusRequester = remember { FocusRequester() }
    val recentSearches by app.searchRepository.recentSearchesFlow
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf<SearchResultType?>(null) } // null = All (Semua)
    var results by remember { mutableStateOf<List<UnifiedSearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    val hasAllFiles = DesktopLauncherManager.hasAllFilesAccess(context)

    DisposableEffect(Unit) {
        onDispose { onTextInputFocusChanged(false) }
    }

    LaunchedEffect(query, selectedFilter) {
        if (query.isBlank()) {
            results = emptyList()
            isSearching = false
            return@LaunchedEffect
        }
        isSearching = true
        delay(160L)
        val types = selectedFilter?.let { setOf(it) } ?: SearchResultType.entries.toSet()
        results = app.searchRepository.searchOffline(query, types)
        isSearching = false
    }

    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val googleWordmark = remember(onSurfaceColor) {
        buildAnnotatedString {
            withStyle(SpanStyle(color = Color(0xFF4285F4), fontWeight = FontWeight.Bold)) { append("C") }
            withStyle(SpanStyle(color = Color(0xFFEA4335), fontWeight = FontWeight.Bold)) { append("y") }
            withStyle(SpanStyle(color = Color(0xFFFBBC05), fontWeight = FontWeight.Bold)) { append("b") }
            withStyle(SpanStyle(color = Color(0xFF4285F4), fontWeight = FontWeight.Bold)) { append("e") }
            withStyle(SpanStyle(color = Color(0xFF34A853), fontWeight = FontWeight.Bold)) { append("r") }
            withStyle(SpanStyle(color = onSurfaceColor, fontWeight = FontWeight.Normal)) { append(" Search") }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Slot 0: Header / Hero area (changes smoothly without unmounting the Search Box below)
        Surface(color = if (query.isBlank()) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surface) {
            if (query.isBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!hasAllFiles) {
                            OutlinedButton(onClick = {
                                onShowMessage(DesktopLauncherManager.openAllFilesAccessSettings(context))
                            }) {
                                Icon(Icons.Filled.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Izin Semua File", fontSize = 12.sp)
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        if (onOpenFileExplorer != null) {
                            TextButton(onClick = onOpenFileExplorer) {
                                Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("File Explorer")
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = googleWordmark,
                        fontSize = 42.sp,
                        letterSpacing = (-0.5).sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Telusuri Arsip, Buku, Video, Bookmark, serta File di Internal, SD Card & USB secara Offline",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = googleWordmark,
                        fontSize = 20.sp,
                        modifier = Modifier.clickable {
                            query = ""
                            if (isWindowFocused) runCatching { searchFocusRequester.requestFocus() }
                        }
                    )
                    if (onOpenFileExplorer != null) {
                        TextButton(onClick = onOpenFileExplorer) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("File Explorer", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Slot 1: Persistent Single BasicTextField (NEVER unmounted when typing 1st character!)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (query.isBlank()) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = if (query.isBlank()) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
                shadowElevation = if (query.isBlank()) 4.dp else 1.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                modifier = Modifier
                    .widthIn(max = if (query.isBlank()) 660.dp else 1400.dp)
                    .fillMaxWidth()
                    .height(if (query.isBlank()) 52.dp else 46.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        tint = Color(0xFF4285F4),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                "Ketik nama file, PDF, MP3, MP4, arsip web, atau kata kunci...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 15.sp
                            ),
                            cursorBrush = SolidColor(Color(0xFF4285F4)),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                focusManager.clearFocus()
                                scope.launch { app.searchRepository.recordSearch(query) }
                            }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(searchFocusRequester)
                                .onFocusChanged { onTextInputFocusChanged(it.isFocused) }
                        )
                    }
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                query = ""
                                if (isWindowFocused) runCatching { searchFocusRequester.requestFocus() }
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Filled.Clear, contentDescription = "Hapus", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        // Slot 2: Scope Filter Chips
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (query.isBlank()) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = if (query.isBlank()) Alignment.Center else Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SearchScopeTab(
                    label = "Semua",
                    selected = selectedFilter == null,
                    onClick = { selectedFilter = null }
                )
                SearchResultType.entries.forEach { type ->
                    SearchScopeTab(
                        label = type.label,
                        selected = selectedFilter == type,
                        onClick = { selectedFilter = type }
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = if (query.isBlank()) 0.15f else 0.35f))

        // Slot 3: Recent Searches (when blank) or Search Results (when active)
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                query.isBlank() -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .widthIn(max = 660.dp)
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (recentSearches.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Pencarian Terakhir",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TextButton(onClick = { scope.launch { app.searchRepository.clearRecentSearches() } }) {
                                    Text("Hapus", fontSize = 12.sp)
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                recentSearches.take(8).forEach { rec ->
                                    Surface(
                                        shape = RoundedCornerShape(18.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.clickable {
                                            query = rec.query
                                            if (isWindowFocused) runCatching { searchFocusRequester.requestFocus() }
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.History,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(rec.query, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        } else {
                            Text(
                                "Mulai ketik untuk mencari seluruh berkas offline di perangkat Anda.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                isSearching -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                            Text("Mencari di seluruh penyimpanan internal, SD Card & USB...")
                        }
                    }
                }

                results.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "Penelusuran '$query' tidak cocok dengan dokumen, media, arsip, atau file apa pun.",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(Modifier.height(8.dp))
                        if (!hasAllFiles) {
                            Button(
                                onClick = {
                                    onShowMessage(DesktopLauncherManager.openAllFilesAccessSettings(context))
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4285F4))
                            ) {
                                Text("Aktifkan Akses Semua File untuk Pencarian Penuh")
                            }
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Text(
                                "Sekitar ${results.size} hasil ditemukan (Internal, SD Card & Eksternal)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        items(results, key = { "${it.type.name}_${it.id}" }) { res ->
                            val icon = when (res.type) {
                                SearchResultType.ARCHIVE -> Icons.Filled.Archive
                                SearchResultType.LIBRARY -> Icons.Filled.MenuBook
                                SearchResultType.MEDIA -> Icons.Filled.VideoLibrary
                                SearchResultType.BOOKMARK -> Icons.Filled.Bookmark
                                SearchResultType.HISTORY -> Icons.Filled.History
                                SearchResultType.FILE -> Icons.Filled.InsertDriveFile
                            }
                            val accent = when (res.type) {
                                SearchResultType.FILE -> Color(0xFF4285F4)
                                SearchResultType.ARCHIVE -> DeckGreen
                                SearchResultType.LIBRARY -> Color(0xFF8AB4F8)
                                SearchResultType.MEDIA -> Color(0xFFEA4335)
                                SearchResultType.BOOKMARK -> DeckAmber
                                SearchResultType.HISTORY -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        scope.launch { app.searchRepository.recordSearch(query) }
                                        when (res.type) {
                                            SearchResultType.ARCHIVE -> onOpenArchive(res.targetRef)
                                            SearchResultType.LIBRARY -> onOpenLibraryItem(res.targetRef)
                                            SearchResultType.MEDIA -> onOpenMediaItem(res.targetRef)
                                            SearchResultType.BOOKMARK,
                                            SearchResultType.HISTORY -> onOpenUrl(res.targetRef)
                                            SearchResultType.FILE -> {
                                                app.driveManager.openPathOrUriWithSystem(res.targetRef, res.mimeType)
                                                    .onFailure {
                                                        onShowMessage("Gagal membuka file: ${it.message}")
                                                    }
                                            }
                                        }
                                    }
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .background(accent.copy(alpha = 0.16f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(13.dp))
                                        }
                                        Text(
                                            text = "${res.type.label} • ${res.subtitle}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (res.isAvailableOffline) {
                                            Text(
                                                "Offline",
                                                fontSize = 10.sp,
                                                color = DeckGreen,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                    Text(
                                        text = res.title,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            color = Color(0xFF4285F4),
                                            textDecoration = TextDecoration.Underline
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (res.tags.isNotBlank()) {
                                        Text(
                                            text = res.tags,
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
            }
        }
    }
}

@Composable
private fun SearchScopeTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) Color(0xFF4285F4).copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) Color(0xFF4285F4) else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        ),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) Color(0xFF4285F4) else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

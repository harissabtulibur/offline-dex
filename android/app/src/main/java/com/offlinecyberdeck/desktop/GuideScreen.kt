package com.offlinecyberdeck.desktop

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offlinecyberdeck.ui.theme.DeckAmber
import com.offlinecyberdeck.ui.theme.DeckGreen

private enum class GuideCategory(val label: String) {
    ALL("Semua"),
    OFFLINE("Modul Offline"),
    SYSTEM("Sistem & File"),
    SETTINGS("Setting & Izin")
}

private data class GuideTopic(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: GuideCategory,
    val icon: ImageVector,
    val accent: Color,
    val whatIsIt: String,
    val howItWorks: List<String>,
    val importantNote: String? = null,
    val targetWindow: DesktopWindowKind? = null,
    val isAiOffline: Boolean = false
)

@Composable
fun GuideScreen(
    onOpenWindow: (DesktopWindowKind) -> Unit,
    onLaunchAiOffline: () -> Unit,
    onOpenDexPermissions: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf(GuideCategory.ALL) }
    var searchQuery by remember { mutableStateOf("") }

    val topics = remember {
        listOf(
            GuideTopic(
                id = "offline_wiki",
                title = "Offline Wiki (Kiwix PWA)",
                subtitle = "Baca Wikipedia & ensiklopedia ZIM tanpa internet",
                category = GuideCategory.OFFLINE,
                icon = Icons.Filled.CloudOff,
                accent = Color(0xFF38BDF8),
                whatIsIt = "Offline Wiki adalah web-viewer berbasis Kiwix PWA untuk membuka arsip ensiklopedia offline berformat .zim (seperti Wikipedia Indonesia/Inggris, Wiktionary, Wikivoyage, atau dokumentasi teknis).",
                howItWorks = listOf(
                    "1. Setup Pertama Kali (WAJIB): Koneksi internet dibutuhkan sebentar saat pertama kali membuka Offline Wiki untuk memuat aplikasi web Kiwix PWA.",
                    "2. Saat pertama kali terbuka akan muncul Welcome Popup -> klik tombol 'X' untuk menutup popup sambutan tersebut.",
                    "3. Setelah masuk ke tampilan utama Kiwix, pilih opsi 'Use classic file picker' agar Anda bisa memilih file .zim langsung dari SSD OTG, SD Card, atau penyimpanan internal.",
                    "4. Pilih file .zim yang sudah Anda unduh/simpan di SSD atau SD Card.",
                    "5. Klik tombol 'Simpan Offline' di bar atas agar antarmuka Kiwix PWA tersimpan menjadi snapshot lokal (status berubah menjadi OFFLINE READY) sehingga tetap bisa dibuka saat tidak ada internet."
                ),
                importantNote = "PENTING: Pertama kali dibuka klik tanda X di welcome popup, lalu pilih 'Use classic file picker' agar pemilih file Android (SAF) terbuka dan file .zim di SSD/SD Card bisa terbaca.",
                targetWindow = DesktopWindowKind.OFFLINE_WIKI
            ),
            GuideTopic(
                id = "cyber_browser",
                title = "Cyber Deck Browser",
                subtitle = "Browser multi-tab gaya desktop + arsip web offline",
                category = GuideCategory.SYSTEM,
                icon = Icons.Filled.Public,
                accent = Color(0xFF22D3EE),
                whatIsIt = "Browser web berfitur penuh dengan tab bergaya desktop, mode Incognito, Dark Mode paksa untuk situs web, pemblokir skrip/pengaturan per-situs, serta fitur penyimpan halaman web menjadi arsip offline.",
                howItWorks = listOf(
                    "Ketik URL atau kata kunci di address bar. Mesin pencari bawaan dapat diubah di Settings (Google, DuckDuckGo, atau custom URL).",
                    "Gunakan tombol Archive (ikon arsip/simpan halaman) di menu browser untuk menyimpan halaman web (HTML + gambar/CSS) ke internal atau Deck Drive agar bisa dibaca ulang tanpa kuota.",
                    "Tab Incognito tidak mencatat riwayat maupun cookies permanen.",
                    "Jumlah WebView aktif dibatasi otomatis sesuai setting 'Max Live WebViews' agar tablet RAM 4GB tetap lancar."
                ),
                importantNote = "Setting penting: 'WebView Dark Mode', 'Default Desktop User-Agent', 'Bottom Address Bar', dan 'Max Live WebViews in Memory' (disarankan 2-3 untuk RAM 4GB).",
                targetWindow = DesktopWindowKind.BROWSER
            ),
            GuideTopic(
                id = "file_explorer",
                title = "File Explorer (File Manager)",
                subtitle = "Manajer berkas ala Windows Explorer untuk Internal, SD & SSD OTG",
                category = GuideCategory.SYSTEM,
                icon = Icons.Filled.Folder,
                accent = Color(0xFFFACC15),
                whatIsIt = "Penjelajah berkas bergaya Windows Explorer ('This PC') untuk mengelola file di Penyimpanan Internal, App Vault, SD Card, dan SSD Eksternal USB-C OTG.",
                howItWorks = listOf(
                    "Panel kiri menampilkan akses cepat ke This PC, Internal Storage, SD Card, dan Deck Drive (SSD OTG yang terhubung lewat SAF).",
                    "Mendukung Salin (Copy), Potong (Cut), Tempel (Paste) lengkap dengan pop-up progress bar saat menyalin file berukuran besar.",
                    "Jika memakai keyboard fisik/Bluetooth, menekan tombol Backspace langsung kembali ke folder sebelumnya (selama kotak pencarian tidak sedang aktif).",
                    "Klik file PDF, TXT, ZIM, MP3, atau MP4 untuk langsung membukanya lewat pembaca/pemutar bawaan."
                ),
                importantNote = "Aktifkan 'Izin Akses Semua File' di Settings agar seluruh folder di Internal dan SD Card bisa dilihat dan dicari tanpa batasan.",
                targetWindow = DesktopWindowKind.FILES
            ),
            GuideTopic(
                id = "task_manager",
                title = "Task Manager",
                subtitle = "Pantau RAM, CPU, suhu, jendela aktif & kill Recent Apps",
                category = GuideCategory.SYSTEM,
                icon = Icons.Filled.Speed,
                accent = Color(0xFF4ADE80),
                whatIsIt = "Monitor sistem real-time ala Windows Task Manager untuk memantau beban tablet RAM 4GB, suhu CPU, grafik jaringan, serta menghentikan aplikasi Android yang masih terbuka.",
                howItWorks = listOf(
                    "Tab 'Proses & Kill': Menampilkan daftar jendela Cyber Deck yang aktif dan aplikasi Android (Recent Apps) beserta pemakaian RAM (MB). Anda bisa langsung menekan tombol 'Kill' atau 'Kill Semua' tanpa membuka layar Recent Apps Android.",
                    "Tombol 'Boost RAM' & 'Trim WebView' membantu membersihkan cache WebView dan proses latar belakang untuk melegakan memori.",
                    "Tab 'Kinerja & Network': Menampilkan grafik real-time penggunaan CPU (%), suhu prosesor, RAM terpakai, dan kecepatan Download/Upload (RX/TX).",
                    "Tab 'Tentang & Sistem': Menampilkan detail spesifikasi hardware, versi Android, baterai, dan uptime."
                ),
                importantNote = "Aktifkan izin 'Akses Data Penggunaan' (Usage Access) lewat banner kuning di Task Manager atau di Settings agar daftar Recent Apps & pemakaian RAM terbaca akurat.",
                targetWindow = DesktopWindowKind.TASK_MANAGER
            ),
            GuideTopic(
                id = "offline_tube",
                title = "OfflineTube",
                subtitle = "Pemutar & katalog video lokal dari Internal, SD Card & SSD",
                category = GuideCategory.OFFLINE,
                icon = Icons.Filled.VideoLibrary,
                accent = Color(0xFFF87171),
                whatIsIt = "Aplikasi video offline bergaya YouTube untuk memutar dan mengelompokkan video lokal (.mp4, .mkv, .webm) dari Internal Storage, SD Card, dan SSD OTG.",
                howItWorks = listOf(
                    "Jika 'Auto-Import Video' aktif, OfflineTube otomatis memindai video baru di Internal, SD Card, dan folder Deck Drive / SAF tanpa membuat duplikat.",
                    "Tersedia filter tab: All, Videos, Audio, Offline Ready, Watch Later, Continue Watching, Channels, dan Playlists.",
                    "Pemutar video memakai ExoPlayer dengan dukungan kecepatan putar (speed), mode layar penuh, dan melanjutkan posisi tontonan terakhir."
                ),
                importantNote = "Jumlah kolom tampilan video dan opsi Auto-Import dapat diatur di Settings -> Cyber Music, OfflineTube & File Access.",
                targetWindow = DesktopWindowKind.MEDIA
            ),
            GuideTopic(
                id = "offline_search",
                title = "Offline Search",
                subtitle = "Mesin pencari terpadu seluruh data offline di tablet",
                category = GuideCategory.OFFLINE,
                icon = Icons.Filled.Search,
                accent = Color(0xFF60A5FA),
                whatIsIt = "Mesin pencari lokal bergaya halaman pencarian yang menelusuri seluruh konten offline sekaligus: file di penyimpanan, Arsip Web, dokumen Knowledge Vault (PDF/ZIM/EPUB), video OfflineTube, Bookmark, dan History.",
                howItWorks = listOf(
                    "Cukup ketik kata kunci (misal nama file, judul video, atau topik artikel) — hasil akan langsung muncul real-time tanpa perlu mengetuk ulang kolom teks.",
                    "Gunakan filter di bawah kotak pencarian (Semua, File, Arsip Web, Knowledge Vault, Media, Bookmark, History) untuk menyaring hasil.",
                    "Ketuk salah satu hasil untuk langsung membuka reader, pemutar media, atau file terkait."
                ),
                importantNote = "Agar pencarian nama berkas di Internal, SD Card, dan USB bekerja menyeluruh, pastikan 'Izin Akses Semua File' sudah diaktifkan.",
                targetWindow = DesktopWindowKind.SEARCH
            ),
            GuideTopic(
                id = "cyber_music",
                title = "Cyber Music",
                subtitle = "Pemutar musik offline untuk MP3, FLAC, M4A, WAV & OGG",
                category = GuideCategory.OFFLINE,
                icon = Icons.Filled.LibraryMusic,
                accent = Color(0xFFC084FC),
                whatIsIt = "Pemutar musik lokal yang mendukung pemindaian otomatis lagu dari Internal Storage, SD Card, maupun SSD OTG.",
                howItWorks = listOf(
                    "Auto-Import memindai lagu MP3/FLAC/M4A/WAV/OGG/OPUS dari penyimpanan perangkat dan folder SAF yang dipilih di Settings.",
                    "Menampilkan pop-up judul lagu, navigator musik (Prev / Play-Pause / Next), seekbar progres lagu, pengatur kecepatan putar, tab Favorit, Artis, Album, dan Antrian.",
                    "Untuk file audio di SD Card/OTG dengan izin terbatas, aplikasi otomatis memakai jalur langsung atau staging cache aman agar pemutaran tidak gagal."
                ),
                importantNote = "Jika lagu di SD Card atau SSD belum muncul, pastikan folder SSD/SD Card sudah ditautkan lewat Settings -> Deck Drive atau 'Folder Media Tambahan', lalu klik Refresh/Scan di Cyber Music.",
                targetWindow = DesktopWindowKind.MUSIC
            ),
            GuideTopic(
                id = "ai_offline",
                title = "AI OFFLINE (Qwen2.5 GGUF)",
                subtitle = "Asisten AI Chat lokal 100% tanpa internet & tanpa API Key",
                category = GuideCategory.OFFLINE,
                icon = Icons.Filled.SmartToy,
                accent = Color(0xFF00FF41),
                whatIsIt = "Chat AI lokal berbasis llama.cpp (streaming token per token) yang dioptimalkan untuk Samsung Galaxy Tab A9 (RAM 4GB, Helio G99) memakai parameter n_ctx = 2048, n_threads = 4, dan mmap = true.",
                howItWorks = listOf(
                    "Siapkan SSD Eksternal USB-C OTG (format exFAT) dan buat folder bernama tepat 'AI_Models' di root SSD: SSD/AI_Models/.",
                    "Taruh file model (contoh: Qwen2.5-1.5B-Instruct-Q4_K_M.gguf) di dalam folder AI_Models tersebut — jangan di root SSD atau folder Download.",
                    "Saat pertama kali membuka AI OFFLINE, pilih folder 'AI_Models' melalui dialog pemilih folder Android (SAF). Izin akan disimpan permanen sehingga pembukaan berikutnya langsung memuat model otomatis.",
                    "Model dimuat menggunakan memory-mapping (mmap) langsung dari SSD tanpa menyalin file 1 GB ke memori internal tablet."
                ),
                importantNote = "Gunakan tombol 'Stop' untuk menghentikan jawaban AI di tengah jalan, 'Clear Chat' untuk membersihkan riwayat percakapan, atau 'Pilih Folder AI_Models' untuk mengganti lokasi folder.",
                isAiOffline = true
            ),
            GuideTopic(
                id = "knowledge_vault",
                title = "Knowledge Vault & Web Archives",
                subtitle = "Perpustakaan dokumen PDF, EPUB, TXT, ZIM & halaman web tersimpan",
                category = GuideCategory.OFFLINE,
                icon = Icons.Filled.MenuBook,
                accent = Color(0xFFFB923C),
                whatIsIt = "Pustaka pribadi untuk menyimpan dan membaca buku digital, catatan teks, file ensiklopedia .zim (ZIM Viewer bawaan), serta halaman web yang telah diarsipkan.",
                howItWorks = listOf(
                    "Buka tab Knowledge Vault untuk mengimpor file satuan atau menjalankan 'Auto Import' dari folder tertentu.",
                    "Pembaca PDF mendukung zoom/geser dan mode kontras malam. ZIM Viewer bawaan mampu membuka artikel terkompresi XZ/ZSTD langsung secara offline.",
                    "Tab Web Archives menyimpan seluruh halaman web yang sebelumnya disimpan dari Cyber Deck Browser."
                ),
                targetWindow = DesktopWindowKind.LIBRARY
            ),
            GuideTopic(
                id = "terminal_desktop",
                title = "PowerShell Terminal & Kustomisasi Desktop",
                subtitle = "Shortcut TAB, perintah cepat terminal & drag-and-drop ikon desktop",
                category = GuideCategory.SYSTEM,
                icon = Icons.Filled.Terminal,
                accent = Color(0xFF38BDF8),
                whatIsIt = "Terminal bergaya Windows PowerShell untuk membuka modul, mengecek hardware, menjalankan shortcut sistem Android, serta fitur kustomisasi tata letak ikon di desktop.",
                howItWorks = listOf(
                    "Tekan tombol TAB pada keyboard Bluetooth/fisik kapan saja untuk membuka atau menutup PowerShell Terminal dengan cepat.",
                    "Ketik 'help' untuk melihat semua perintah: 'sysinfo', 'appinfo', 'ram', 'open browser', 'open wiki', 'open tube', 'open search', 'open ai', 'open files', 'open taskmgr', 'open settings', 'open guide', 'apps', 'launch <nama>', dan 'sys wifi|bluetooth|storage|dev|...'.",
                    "Custom Command: Anda dapat membuat perintah buatan sendiri (misal 'wa' -> buka WhatsApp) lewat Settings -> Terminal Custom Commands.",
                    "Drag & Drop Desktop: Klik tombol 'Atur Ikon' di kanan atas desktop (atau tekan-tahan salah satu ikon) untuk menggeser posisi ikon bebas atau dengan 'Snap: ON'. Klik 'Rapikan' untuk mengembalikan ke grid awal."
                ),
                targetWindow = DesktopWindowKind.TERMINAL
            ),
            GuideTopic(
                id = "settings_permissions",
                title = "Pengaturan Penting & Izin DeX",
                subtitle = "Checklist izin & setting yang wajib diketahui agar semua fitur berjalan maksimal",
                category = GuideCategory.SETTINGS,
                icon = Icons.Filled.Settings,
                accent = Color(0xFFEAB308),
                whatIsIt = "Ringkasan pengaturan penting di Cyber Deck Settings serta izin Android yang memengaruhi fitur DeX, penyimpanan SSD, jendela floating, dan performa RAM 4GB.",
                howItWorks = listOf(
                    "1. Deck Drive (USB-C SSD via SAF): Pilih folder utama SSD Anda sekali saja agar Download, Web Archive, OfflineTube, dan Cyber Music bisa membaca/menulis ke SSD.",
                    "2. Izin DeX Launcher & Floating Apps: (a) Jadikan Cyber Deck sebagai Home Launcher Utama jika ingin tampil saat tekan Home; (b) Aktifkan Izin Overlay; (c) Di Developer Options tablet aktifkan 'Enable freeform windows' + 'Force activities to be resizable' lalu restart agar aplikasi luar (WA/YouTube) terbuka sebagai jendela mengambang; (d) Aktifkan Akses Notifikasi agar notif muncul di panel kanan taskbar.",
                    "3. Izin Akses Semua File (All Files Access): Wajib aktif untuk File Explorer penuh, Offline Search ke SD/Internal, dan auto-import musik/video.",
                    "4. Akses Data Penggunaan (Usage Access): Wajib aktif agar Task Manager bisa menampilkan Recent Apps dan penggunaan RAM per aplikasi.",
                    "5. Ukuran Ikon, Wallpaper & Taskbar: Atur ukuran ikon desktop (36dp-68dp), pilih wallpaper preset atau maksimal 4 foto custom, serta aktifkan 'Auto-Hide Taskbar DeX' jika ingin tampilan layar penuh.",
                    "6. Export / Restore Catalog: Gunakan untuk membackup daftar bookmark, indeks arsip, pustaka, dan pengaturan ke file JSON."
                ),
                importantNote = "Semua pengaturan yang berhubungan dengan tampilan, custom command terminal, ukuran ikon, dan manajemen penyimpanan ada di menu Settings (GUI).",
                targetWindow = DesktopWindowKind.SETTINGS
            )
        )
    }

    val filteredTopics = remember(topics, selectedCategory, searchQuery) {
        topics.filter { topic ->
            val catOk = selectedCategory == GuideCategory.ALL || topic.category == selectedCategory
            val q = searchQuery.trim()
            val queryOk = q.isBlank() ||
                topic.title.contains(q, ignoreCase = true) ||
                topic.subtitle.contains(q, ignoreCase = true) ||
                topic.whatIsIt.contains(q, ignoreCase = true) ||
                topic.howItWorks.any { it.contains(q, ignoreCase = true) } ||
                (topic.importantNote?.contains(q, ignoreCase = true) == true)
            catOk && queryOk
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Surface(tonalElevation = 2.dp, color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.HelpOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Column {
                            Text(
                                "Panduan Penggunaan Offline Cyber Deck",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Penjelasan fungsi, cara kerja, tips setting & setup pertama kali",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedButton(onClick = onOpenDexPermissions) {
                        Icon(Icons.Filled.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Cek Izin DeX", fontSize = 12.sp)
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    placeholder = { Text("Cari panduan (misal: offline wiki, file picker, browser, ssd, task manager)...") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GuideCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat.label) }
                        )
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(filteredTopics, key = { it.id }) { topic ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, topic.accent.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(topic.accent.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(topic.icon, contentDescription = null, tint = topic.accent, modifier = Modifier.size(20.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    topic.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    topic.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (topic.targetWindow != null || topic.isAiOffline) {
                                Surface(
                                    color = topic.accent.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, topic.accent.copy(alpha = 0.55f)),
                                    modifier = Modifier.clickable {
                                        if (topic.isAiOffline) onLaunchAiOffline()
                                        else topic.targetWindow?.let(onOpenWindow)
                                    }
                                ) {
                                    Text(
                                        "Buka",
                                        color = topic.accent,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                        Text(
                            "Apa ini?",
                            style = MaterialTheme.typography.labelLarge,
                            color = topic.accent,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            topic.whatIsIt,
                            style = MaterialTheme.typography.bodySmall
                        )

                        Text(
                            "Cara Kerja & Penggunaan:",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            topic.howItWorks.forEach { step ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = DeckGreen,
                                        modifier = Modifier
                                            .padding(top = 2.dp)
                                            .size(14.dp)
                                    )
                                    Text(step, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }

                        if (!topic.importantNote.isNullOrBlank()) {
                            Surface(
                                color = DeckAmber.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, DeckAmber.copy(alpha = 0.45f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        Icons.Filled.Warning,
                                        contentDescription = null,
                                        tint = DeckAmber,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        topic.importantNote,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
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

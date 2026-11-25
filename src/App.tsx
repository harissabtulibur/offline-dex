/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import {FolderGit2, HardDrive, ShieldCheck, Smartphone, Terminal} from 'lucide-react';

const MODULES = [
  {
    path: 'android/app/src/main/java/com/offlinecyberdeck/browser/',
    title: 'Browser Core & WebView Pool',
    detail:
      'Multi-tab WebViewPoolManager tuned for 4 GB RAM (LRU eviction + Bundle state save/restore), SSL error blocking, external scheme confirmation, incognito cookie/storage purge, find-in-page, desktop toggle.'
  },
  {
    path: 'android/app/src/main/java/com/offlinecyberdeck/core/storage/DeckDriveManager.kt',
    title: 'Deck Drive (USB-C SSD via SAF)',
    detail:
      'Persistable DocumentFile tree access, Os.fstatvfs capacity probing, disconnected drive resilience, unique atomic-safe naming, and write/read self-test.'
  },
  {
    path: 'android/app/src/main/java/com/offlinecyberdeck/archive/',
    title: 'Offline Page Archive & Reader',
    detail:
      'DOM HTML snapshot + static image/CSS capture, cross-archive link detection, honest Reader Snapshot / Partial / Full status badges, offline asset replay & simplified text reader.'
  },
  {
    path: 'android/app/src/main/java/com/offlinecyberdeck/downloads/',
    title: 'Download Manager',
    detail:
      'Real HTTP/HTTPS streaming downloader with Range resume support, pause/resume/retry/cancel, SAF folder destination, and FileProvider open/share.'
  },
  {
    path: 'android/app/src/main/java/com/offlinecyberdeck/library/',
    title: 'Knowledge Vault & Readers',
    detail:
      '13-category offline catalog, SAF document import, native PdfRenderer PDF viewer, EPUB/HTML/TXT reader, curated open package directory.'
  },
  {
    path: 'android/app/src/main/java/com/offlinecyberdeck/media/',
    title: 'Media Vault & ExoPlayer',
    detail:
      'SAF media scanning, MediaMetadataRetriever thumbnails, exact ID & fuzzy title matching, Available Offline vs Metadata Only badges, playlists, Watch Later, Media3 ExoPlayer.'
  },
  {
    path: 'android/app/src/main/java/com/offlinecyberdeck/search/',
    title: 'Unified Offline Search & Room DB',
    detail:
      'Ranked offline search across archives, Vault documents, media catalog, bookmarks, and history backed by Room SQLite + JSON catalog backup/restore.'
  }
];

export default function App() {
  return (
    <div className="min-h-screen bg-[#0B0F14] text-slate-100 px-6 py-10">
      <div className="max-w-4xl mx-auto space-y-6">
        <header className="border border-cyan-500/30 bg-[#121820] rounded-xl p-6 space-y-3">
          <div className="flex items-center gap-3 text-cyan-400">
            <Smartphone className="w-6 h-6" />
            <span className="font-mono text-xs uppercase tracking-wider">
              Native Android Source Project (Kotlin + Jetpack Compose)
            </span>
          </div>
          <h1 className="text-2xl font-bold tracking-tight">Offline Cyber Deck</h1>
          <p className="text-sm text-slate-300 leading-relaxed">
            The native Android Kotlin &amp; Jetpack Compose project has been generated under{' '}
            <code className="px-1.5 py-0.5 rounded bg-slate-800 text-cyan-300">/android</code>.
            Because this AI Studio container runs a Node.js/Vite web runtime without JDK, Android
            SDK, or Gradle installed, no APK was compiled here—open{' '}
            <code className="px-1.5 py-0.5 rounded bg-slate-800 text-cyan-300">/android</code> in
            Android Studio to build and deploy to your Samsung Galaxy Tab A9.
          </p>
        </header>

        <section className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div className="border border-slate-800 bg-[#121820] rounded-xl p-5 space-y-2">
            <div className="flex items-center gap-2 text-teal-400 font-semibold text-sm">
              <Terminal className="w-4 h-4" />
              Build &amp; Install on Galaxy Tab A9
            </div>
            <pre className="text-xs font-mono bg-[#0B0F14] border border-slate-800 rounded-lg p-3 overflow-x-auto text-slate-300">
{`# Open /android in Android Studio Ladybug+
# Or with Gradle + Android SDK CLI:
cd android
gradle assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk`}
            </pre>
          </div>

          <div className="border border-slate-800 bg-[#121820] rounded-xl p-5 space-y-2">
            <div className="flex items-center gap-2 text-cyan-400 font-semibold text-sm">
              <HardDrive className="w-4 h-4" />
              USB-C SSD (Deck Drive) Guidance
            </div>
            <p className="text-xs text-slate-300 leading-relaxed">
              Format external USB-C SSDs as <strong>exFAT</strong> for large files (&gt;4 GB).
              In Settings → Deck Drive, grant SAF access to a dedicated folder on the SSD (such as{' '}
              <code>CyberDeckVault</code>) and run the built-in Write/Read Self-Test.
            </p>
          </div>
        </section>

        <section className="border border-slate-800 bg-[#121820] rounded-xl p-6 space-y-4">
          <div className="flex items-center gap-2 text-slate-200 font-semibold">
            <FolderGit2 className="w-5 h-5 text-cyan-400" />
            Implemented Android Modules
          </div>
          <div className="space-y-3">
            {MODULES.map((mod) => (
              <div
                key={mod.path}
                className="border border-slate-800 bg-[#0B0F14] rounded-lg p-4 space-y-1"
              >
                <div className="font-medium text-sm text-cyan-300">{mod.title}</div>
                <div className="font-mono text-xs text-slate-400">{mod.path}</div>
                <p className="text-xs text-slate-300 pt-1">{mod.detail}</p>
              </div>
            ))}
          </div>
        </section>

        <footer className="flex items-center gap-2 text-xs text-slate-400">
          <ShieldCheck className="w-4 h-4 text-teal-400" />
          <span>
            Offline-first, zero accounts, zero telemetry, strict SSL &amp; SAF scoped storage.
            See <code>android/README.md</code> for full build and testing details.
          </span>
        </footer>
      </div>
    </div>
  );
}

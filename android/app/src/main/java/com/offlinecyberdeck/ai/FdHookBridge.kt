package com.offlinecyberdeck.ai

/**
 * JNI bridge for redirecting native /proc/self/fd/<fd> open/fopen/stat calls
 * to dup(fd) + fdopen()/fstat() directly inside loaded llama.cpp shared libraries.
 *
 * This bypasses Android 14 SELinux EACCES denials on ExternalStorageProvider
 * procfs symlinks without copying .gguf files or requesting MANAGE_EXTERNAL_STORAGE.
 */
object FdHookBridge {

    @Volatile
    private var libraryLoaded = false

    private fun ensureLoaded(): Boolean {
        if (libraryLoaded) return true
        return synchronized(this) {
            if (!libraryLoaded) {
                libraryLoaded = runCatching {
                    System.loadLibrary("cyberdeck_fdhook")
                    true
                }.getOrDefault(false)
            }
            libraryLoaded
        }
    }

    fun installHooks(): String {
        if (!ensureLoaded()) return "unavailable"
        return runCatching { nativeInstallHooks() }.getOrDefault("error")
    }

    fun setTargetFd(fd: Int) {
        if (!ensureLoaded()) return
        runCatching { nativeSetTargetFd(fd) }
    }

    @JvmStatic
    private external fun nativeInstallHooks(): String

    @JvmStatic
    private external fun nativeSetTargetFd(fd: Int)
}

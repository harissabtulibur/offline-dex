-keep class com.offlinecyberdeck.core.model.** { *; }
-keep class com.offlinecyberdeck.core.settings.BackupPayload { *; }
-keep class com.offlinecyberdeck.core.database.entity.** { *; }
-keep class com.github.luben.zstd.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
-dontwarn org.jsoup.**
-dontwarn org.tukaani.xz.**
-dontwarn com.github.luben.zstd.**

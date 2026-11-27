-keep class com.offlinecyberdeck.core.model.** { *; }
-keep class com.offlinecyberdeck.core.settings.BackupPayload { *; }
-keep class com.offlinecyberdeck.core.database.entity.** { *; }
-keep class com.github.luben.zstd.** { *; }
-keep class com.llamatik.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
-dontwarn org.jsoup.**
-dontwarn org.tukaani.xz.**
-dontwarn com.github.luben.zstd.**
-dontwarn com.llamatik.**

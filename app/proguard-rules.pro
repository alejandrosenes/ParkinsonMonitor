# Keep Room entities
-keep class com.parkinson.app.data.local.database.entities.** { *; }
-keep class com.parkinson.app.domain.models.** { *; }
-dontwarn com.itextpdf.**

# Suppress missing slf4j binding (iText transitive dependency)
-dontwarn org.slf4j.impl.StaticLoggerBinder

# Keep iText class names (required for internal event checks)
-keep class com.itextpdf.** { *; }
-keepnames class com.itextpdf.**

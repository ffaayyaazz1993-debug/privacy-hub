# FocusMap R8 rules
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# Room
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-dontwarn androidx.room.paging.**

# Gson: reflectively bound models
-keep class com.privacyhub.app.data.db.** { *; }
-keep class com.privacyhub.app.data.export.BackupManager$Backup { *; }
-keep class com.google.gson.** { *; }
-dontwarn sun.misc.**

# Compose / coroutines
-dontwarn androidx.compose.**
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Manifest-referenced components
-keep class com.privacyhub.app.service.** { *; }
-keep class com.privacyhub.app.MainActivity { *; }

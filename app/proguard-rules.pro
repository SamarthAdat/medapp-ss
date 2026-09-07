# R8 rules for the release build.
#
# Everything here exists because something in this app is reached by reflection
# or by name at runtime, which R8 cannot see. Each block says what breaks
# without it, because a rule with no reason is one nobody dares delete.

# --- Crash reports stay readable ------------------------------------------
# Without this a release stack trace is a list of a(), b(), c(). The mapping
# file in build/outputs/mapping/release/ is what turns it back; keep it with
# every build that is shipped or it cannot be deobfuscated later.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- kotlinx.serialization -------------------------------------------------
# Navigation-Compose type-safe routes are @Serializable classes resolved by
# their generated serializers. Obfuscating them away makes every navigate()
# call fail at runtime with a serializer-not-found error - and only in release.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.ss.medrecord.**$$serializer { *; }
-keepclassmembers class com.ss.medrecord.** {
    *** Companion;
}
-keepclasseswithmembers class com.ss.medrecord.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Room ------------------------------------------------------------------
# Entities and DAO implementations are generated and referenced by name.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# --- SQLCipher -------------------------------------------------------------
# Loaded with System.loadLibrary and bound through JNI, so the JVM side has to
# keep its exact names. Stripping these bricks the database entirely: the app
# cannot open its own store and every screen is empty.
-keep class net.zetetic.database.** { *; }
-keep class net.sqlcipher.** { *; }
-dontwarn net.zetetic.**

# --- Firebase / Firestore --------------------------------------------------
# Firestore maps documents by field name. This app writes explicit maps rather
# than POJOs, so the exposure is small, but the SDK's own model classes are
# still reflected over.
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**
-keepclassmembers class com.ss.medrecord.domain.model.** {
    <init>(...);
    *;
}

# --- Maps and Places -------------------------------------------------------
-keep class com.google.android.libraries.places.** { *; }
-dontwarn com.google.android.libraries.places.**

# --- Hilt / Dagger ---------------------------------------------------------
# Generated components and @HiltWorker classes are instantiated by name.
-keep class dagger.hilt.** { *; }
-keep class * extends androidx.work.ListenableWorker { <init>(...); }
-keepclasseswithmembernames class * {
    @dagger.hilt.* <fields>;
}

# --- WorkManager -----------------------------------------------------------
# Workers are constructed reflectively from the class name stored in its
# database, so a worker renamed by R8 is one that silently never runs again -
# including the reminder sweep and the retention purge.
-keep class com.ss.medrecord.data.**.*Worker { *; }

# --- Broadcast receivers and services --------------------------------------
# Named in the manifest and instantiated by the system.
-keep class com.ss.medrecord.data.reminder.ReminderReceiver { *; }
-keep class com.ss.medrecord.data.reminder.BootReceiver { *; }
-keep class com.ss.medrecord.data.notification.MedRecordMessagingService { *; }

# --- Logging ---------------------------------------------------------------
# Strips debug and verbose logging from release builds. Warnings and errors are
# kept: they carry no patient data by design and are what makes a field report
# actionable. This is defence in depth rather than the primary control - the
# rule the app follows is that nothing clinical is ever passed to Log.
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}

# kotlinx.serialization usa os serializers gerados; o R8 apagaria os campos.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class com.ptvin.tmdbrows.** {
    *** Companion;
}
-keepclasseswithmembers class com.ptvin.tmdbrows.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.ptvin.tmdbrows.**$$serializer { *; }

# Room/WorkManager criam o WorkDatabase_Impl por nome (reflexão).
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class androidx.work.impl.WorkDatabase_Impl { *; }

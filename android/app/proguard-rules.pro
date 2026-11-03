# kotlinx.serialization: generierte Serializer behalten
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class de.helmffo.teamplaner.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class de.helmffo.teamplaner.**$$serializer { *; }

# Retrofit-Schnittstelle (Reflection auf Methoden und generische Rückgabetypen)
-keepattributes Signature, Exceptions
-keep,allowobfuscation interface de.helmffo.teamplaner.daten.TeamplanerApi
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

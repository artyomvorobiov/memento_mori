# Модели читаются через kotlinx.serialization — сериализаторы должны остаться.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class app.mementomori.core.** {
    *** Companion;
}
-keepclasseswithmembers class app.mementomori.core.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.mementomori.core.**$$serializer { *; }

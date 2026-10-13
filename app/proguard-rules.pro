# kotlinx.serialization: keep generated serializers for the Jamendo DTOs.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.vikash.mp3player.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.vikash.mp3player.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}

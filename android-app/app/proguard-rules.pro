-keep class com.momijineko.fanqie.data.api.** { *; }
-keep class com.momijineko.fanqie.data.db.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.JsonClass *;
}
-keepattributes RuntimeVisibleAnnotations,AnnotationDefaults

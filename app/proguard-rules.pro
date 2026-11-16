# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class sg.edumate.** { *** Companion; }
-keepclasseswithmembers class sg.edumate.** { kotlinx.serialization.KSerializer serializer(...); }
# SQLCipher
-keep class net.zetetic.database.** { *; }
# Strip verbose logging in release (no personal data in logs regardless)
-assumenosideeffects class android.util.Log { public static int v(...); public static int d(...); public static int i(...); }

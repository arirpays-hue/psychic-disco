# שמירה מלאה על כל הקוד של האפליקציה שלנו
-keep class com.example.englishtutor.** { *; }
-keepclassmembers enum com.example.englishtutor.** { *; }

# הגנה על Jetpack Compose Runtime ו-Coroutines מפני מחיקת יתר בדחיסה
-keep class androidx.compose.runtime.** { *; }
-dontwarn androidx.compose.**
-dontwarn kotlinx.coroutines.**
-keepattributes *Annotation*,InnerClasses,Signature,EnclosingMethod

# Keep Gson and related classes
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# Keep your data models to prevent R8 from obfuscating/removing them
# This is crucial for Gson to map JSON fields to these classes
-keep class com.example.kazanim_app1.Entry { *; }
-keep class com.example.kazanim_app1.Section { *; }
-keep class com.example.kazanim_app1.SubMenu { *; }

# Alternatively, keep all classes in the package if you add more models later
-keep class com.example.kazanim_app1.** { *; }

# Keep Compose related classes that might be stripped in release
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**
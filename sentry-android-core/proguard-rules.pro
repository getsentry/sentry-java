# Gson uses generic type information stored in a class file when working with fields. Proguard
# removes such information by default, so configure it to keep all of it.
-keepattributes Signature

# For using GSON @Expose annotation
-keepattributes *Annotation*

# Application classes that will be serialized/deserialized
-keep class io.sentry.** { *; }
-keepclassmembers enum io.sentry.** { *; }
-keep class io.sentry.android.core.** { *; }

# don't warn jetbrains annotations
-dontwarn org.jetbrains.annotations.**

# R8: Attribute Signature requires InnerClasses attribute. Check -keepattributes directive.
-keepattributes InnerClasses

# To ensure that stack traces is unambiguous
# https://developer.android.com/studio/build/shrink-code#decode-stack-trace
-keepattributes LineNumberTable,SourceFile

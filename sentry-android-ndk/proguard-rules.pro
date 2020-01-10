##---------------Begin: proguard configuration for NDK  ----------

-keep class io.sentry.android.ndk.** { *; }

# For native methods, see http://proguard.sourceforge.net/manual/examples.html#native
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

##---------------End: proguard configuration for NDK  ----------

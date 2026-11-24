##---------------Begin: proguard configuration for Compose  ----------

# To ensure that stack traces is unambiguous
# https://developer.android.com/studio/build/shrink-code#decode-stack-trace
-keepattributes LineNumberTable,SourceFile

##---------------End: proguard configuration for Compose  ----------

##---------------Begin: proguard configuration for kotlinx.serialization  ----------

# kotlinx.serialization is a compileOnly dependency, and an app that uses this module without Nav3
# may not include it. Without this rule, R8 can fail such an app's build on the missing classes
# referenced by the built-in BackStackEntryMappers. If the app uses one of those mappers anyway,
# creating it fails with a NoClassDefFoundError, and every entry is recorded as /unknown.
-dontwarn kotlinx.serialization.**

##---------------End: proguard configuration for kotlinx.serialization  ----------

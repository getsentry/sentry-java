##---------------Begin: proguard configuration for Compose  ----------

# To ensure that stack traces is unambiguous
# https://developer.android.com/studio/build/shrink-code#decode-stack-trace
-keepattributes LineNumberTable,SourceFile

##---------------End: proguard configuration for Compose  ----------

##---------------Begin: proguard configuration for kotlinx.serialization  ----------

# kotlinx.serialization is an optional (compileOnly) dependency used by
# BackStackEntryMapper.serialName(). Don't fail the build when it is absent.
-dontwarn kotlinx.serialization.**

# BackStackEntryMapper.serialName() checks for kotlinx.serialization by class name.
# Keep that name so the check still succeeds in minified apps that include the library.
-keepnames class kotlinx.serialization.KSerializer

##---------------End: proguard configuration for kotlinx.serialization  ----------

##---------------Begin: proguard configuration for OkHttp  ----------

# To ensure that stack traces is unambiguous
# https://developer.android.com/studio/build/shrink-code#decode-stack-trace
-keepattributes LineNumberTable,SourceFile
-renamesourcefileattribute SourceFile

# https://square.github.io/okhttp/features/r8_proguard/
# If you use OkHttp as a dependency in an Android project which uses R8 as a default compiler you
# don’t have to do anything. The specific rules are already bundled into the JAR which can
# be interpreted by R8 automatically.
# https://raw.githubusercontent.com/square/okhttp/master/okhttp/src/jvmMain/resources/META-INF/proguard/okhttp3.pro

##---------------End: proguard configuration for OkHttp  ----------

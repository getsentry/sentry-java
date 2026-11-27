##---------------Begin: proguard configuration for Compose  ----------

# To ensure that stack traces is unambiguous
# https://developer.android.com/studio/build/shrink-code#decode-stack-trace
-keepattributes LineNumberTable,SourceFile

##---------------End: proguard configuration for Compose  ----------

##---------------Begin: proguard configuration for kotlinx.serialization  ----------

# The kotlinx.serialization compatibility probe looks up this SDK-owned fixture's serializer via reflection, as the built-in mappers look
# up those of apps' back stack entries. kotlinx.serialization's R8 rules before 1.8.0 keep an object's serializer() only while its INSTANCE
# field is kept, and R8 full mode removes INSTANCE here, as the SDK only refers to the object's class. The probe would then be inconclusive
# for object lookups.
-keepclassmembers class io.sentry.compose.navigation3.internal.backstack.kotlinx.KotlinxCompatibilityProbe$ProbeObject {
    public static ** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# Serial names survive R8 minification because Kotlin's serialization compiler plugin fixes them at compile time. Our built-in mapper
# fallback for private `@Serializable` objects relies on members preserved by kotlinx.serialization's own consumer rules.

# We don't ship rules for `@Serializable` classes with named companion objects. kotlinx.serialization's rules cover them but only from
# 1.10.0 on. A generic rule set that fixes older versions exists, but we don't apply it because it would keep `InnerClasses` and
# named-companion class names in every host app.

# We don't include a -dontwarn rule for kotlinx.serialization even though it's a compileOnly dependency. The only apps potentially affected
# are those that use sentry-android-navigation3 but not Nav3 (which brings in kotlinx-serialization-core via navigation3-runtime). Adding a
# broad `-dontwarn kotlinx.serialization.**` rule could suppress legitimate – and unrelated – R8 missing-class errors for apps that use
# Nav3, causing them to crash at runtime rather than fail at buildtime. Apps that need the -dontwarn rule (likely the null set) can add it
# themselves.

##---------------End: proguard configuration for kotlinx.serialization  ----------

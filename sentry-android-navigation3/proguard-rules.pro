##---------------Begin: proguard configuration for Compose  ----------

# To ensure that stack traces is unambiguous
# https://developer.android.com/studio/build/shrink-code#decode-stack-trace
-keepattributes LineNumberTable,SourceFile

##---------------End: proguard configuration for Compose  ----------

##---------------Begin: proguard configuration for the kotlinx.serialization compatibility probe  ----------

# The probe looks up this SDK-owned fixture's serializer by reflection, as the built-in mappers look
# up those of apps' back stack entries. kotlinx.serialization's rules before 1.10.0 keep an object's
# serializer() only while its INSTANCE field is kept, and R8 full mode removes INSTANCE here, as the
# SDK only refers to the object's class. The probe would then be inconclusive for object lookups.
-keepclassmembers class io.sentry.compose.navigation3.internal.backstack.kotlinx.KotlinxCompatibilityProbe$Companion$ProbeObject {
    public static ** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

##---------------End: proguard configuration for the kotlinx.serialization compatibility probe  ----------

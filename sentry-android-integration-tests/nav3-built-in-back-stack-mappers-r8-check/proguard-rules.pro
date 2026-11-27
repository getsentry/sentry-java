# kotlinx.serialization's named-companion rules for R8 full mode, which also work in compat mode.
# Needed for the serialization floor (1.7.3); kotlinx.serialization 1.10.0+ supplies its own.
# See https://docs.sentry.io/platforms/android/integrations/navigation/#troubleshooting-kotlinxserialization-mappers
# https://github.com/Kotlin/kotlinx.serialization#android
-keepattributes InnerClasses

-if @kotlinx.serialization.Serializable class
io.sentry.tests.nav3.r8.NamedCompanionKeyWithRules
{
    static **$* *;
}
-keepnames class <1>$$serializer {
    static <1>$$serializer INSTANCE;
}

-keepclasseswithmembers, allowshrinking, allowobfuscation, allowaccessmodification class
io.sentry.tests.nav3.r8.NamedCompanionKeyWithRules
{
    *;
}

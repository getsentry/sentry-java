# `io.sentry.compose.navigation3.internal.backstack.kotlinx`

Package contains code that accesses kotlinx.serialization APIs.

## Dependency isolation

kotlinx.serialization is a `compileOnly` dependency of `sentry-android-navigation3` that we assume is present, as every Nav3 app depends on
it via `navigation3-runtime`.

The kotlinx.serialization ***version***, however, is determined by the host app, and may be incompatible with the APIs we use. To protect
against crashes, only classes in the `kotlinx` subpackage may reference kotlinx.serialization APIs, and code outside is limited to a single
entry point, viz., `BuiltInBackStackEntryMappers.create`. That's where we catch the `LinkageError` thrown if an incompatible version can't
be linked.

Creating a mapper also runs `KotlinxCompatibilityProbe`, which names and encodes SDK-owned `@Serializable` types. Those run no app code,
so a `LinkageError` from them shows that the app's version is incompatible. If naming fails, creating the mapper fails. If encoding fails,
`SerialNameAndProperties` keeps entry names but drops their arguments with `encoding_failed`. A `LinkageError` thrown while mapping an entry
then fails only that entry's type.

To keep our isolation policy intact:

- put any new classes that reference kotlinx.serialization in the `.kotlinx` subpackage and give them a `Kotlinx*` prefix;
- don't expose kotlinx.serialization types outside the `Kotlinx*` classes; and
- reference a `Kotlinx*` class from outside the subpackage only via `BuiltInBackStackEntryMappers.create`.

In addition, the `.kotlinx` subpackage may ***not*** depend on Android framework APIs, AndroidX, or the `internal.lifecycle` package.

## Minification

`sentry-android-navigation3` doesn't ship any R8 rules for kotlinx.serialization. Serial names survive R8 minification because Kotlin's
serialization compiler plugin fixes them at compile time. `KotlinxBackStackEntryMapper`'s fallback for private `@Serializable` objects
relies on members preserved by kotlinx.serialization's own consumer rules.

`KotlinxBackStackEntryMapper` only names classes whose `@Serializable` annotation is present at runtime. kotlinx.serialization's consumer
rules (from our 1.7.3 floor on) keep that annotation: `kotlinx-serialization-common.pro` keeps `RuntimeVisibleAnnotations`, and
`kotlinx-serialization-r8.pro` keeps every `@Serializable` class (allowing shrinking and obfuscation) so that R8 full mode doesn't strip
the annotation. kotlinx.serialization relies on the annotation itself, e.g., to pick sealed-class serializers, so an app that disables those
rules breaks kotlinx.serialization as well as our mappers.

We also don't ship rules for `@Serializable` classes with named companion objects. kotlinx.serialization's rules only cover those from
1.10.0 on. A generic rule set that fixes older versions exists, but we don't apply it because it would keep `InnerClasses` and
named-companion class names in every host app.

`nav3-backstack-entry-mapper-r8-check` verifies our built-in `SerialName` and `SerialNameAndProperties` mappers behave
correctly despite R8 minification. It does so against the kotlinx.serialization version we use for compilation, along with newer versions,
and in both R8 full and compat modes. See its README for more details.

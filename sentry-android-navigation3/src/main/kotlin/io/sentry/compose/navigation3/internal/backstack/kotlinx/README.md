# `io.sentry.compose.navigation3.internal.backstack.kotlinx`

This package isolates access to kotlinx.serialization APIs.

## Dependency isolation

kotlinx.serialization is an `implementation` dependency of `sentry-android-navigation3`, declared as a minimum at the floor, so its classes
are always present. Every Nav3 release depends on the same version via `navigation3-runtime`, so the dependency never raises a Nav3 app's
version. We compile against exactly the floor through a separate, unpublished `compileOnly` declaration.

The kotlinx.serialization ***version***, however, is determined by the host app, and may be incompatible with the APIs we use. To protect
against crashes, only classes in the `kotlinx` subpackage may reference kotlinx.serialization APIs, and code outside is limited to a single
entry point, viz., `BuiltInBackStackEntryMappers.create`. That's where we catch the `LinkageError` thrown if an incompatible version can't
be linked.

Creating a mapper runs `KotlinxCompatibilityProbe`'s name lookup on SDK-owned `@Serializable` types, without running app serializers.
A `LinkageError` prevents mapper creation. Other non-fatal lookup failures are inconclusive; mapping continues with failures handled per
entry type. Later name-lookup failures are cached per entry type.

To keep our isolation policy intact:

- put any new classes that reference kotlinx.serialization in the `.kotlinx` subpackage and give them a `Kotlinx*` prefix;
- don't expose kotlinx.serialization types outside the `Kotlinx*` classes; and
- reference a `Kotlinx*` class from outside the subpackage only via `BuiltInBackStackEntryMappers.create`.

In addition, the `.kotlinx` subpackage may ***not*** depend on Android framework APIs, AndroidX, or the `internal.lifecycle` package.

## Version floor

The floor (`kotlinxSerialization-floor` in `gradle/dependency-matrix.versions.toml`) is the oldest kotlinx.serialization version our
built-in mappers support. It matches the oldest supported Nav3 release's dependency: 1.7.3 for `navigation3-runtime` 1.0.0. We publish
the floor as a minimum, so raising it upgrades kotlinx.serialization in apps on older Nav3 releases.

We compile against exactly the floor. `build.gradle.kts` pins it strictly for compiling, as other dependencies also bring in
kotlinx.serialization, and `verifyKotlinxSerializationCompileVersion` fails the build if the compile classpath resolves any other version.
Compiling against a newer version could produce calls the floor's runtime lacks.

The unit tests run against the floor, or the version `-Pnav3TestKotlinxVersion` names, as a minimum rather than strictly, and
`verifyKotlinxSerializationTestVersion` fails the build if a test dependency raises it. A strict pin would instead silently run that
dependency on a version it doesn't support, which no real app can have. If a test dependency, e.g., `navigation3-runtime`, starts requiring
a newer version, test the floor with the newest release of that dependency that accepts the floor, as apps on its older releases still run
the floor.

Rules for changing the floor:

- Raise it only when the oldest supported Nav3 release requires a newer kotlinx.serialization, never as a side effect of another
  dependency bump.
- Raise it in a reviewed change of its own, with a CHANGELOG entry and a note in the Nav3 integration docs.
- Update `MIN_KOTLINX_SERIALIZATION_VERSION` in `KotlinxBackStackEntryMapper.kt` to match. A unit test fails if the two differ.
- Support behavior that only newer kotlinx.serialization versions need with a version-specific code path, not by raising the floor.

## Minification

`sentry-android-navigation3` ships no rules for apps' kotlinx.serialization classes, only one that keeps the compatibility probe's own
object fixture (see `proguard-rules.pro`). kotlinx.serialization is an `implementation` dependency at the floor, which every Nav3
release also depends on, so its classes are always present. Serial names survive R8 minification because Kotlin's serialization
compiler plugin fixes them at compile time. `KotlinxBackStackEntryMapper`'s fallback for private `@Serializable` objects
relies on members preserved by kotlinx.serialization's own consumer rules.

`KotlinxBackStackEntryMapper` only names classes whose `@Serializable` annotation is present at runtime. kotlinx.serialization's consumer
rules (from our 1.7.3 floor on) keep that annotation: `kotlinx-serialization-common.pro` keeps `RuntimeVisibleAnnotations`, and
`kotlinx-serialization-r8.pro` keeps every `@Serializable` class (allowing shrinking and obfuscation) so that R8 full mode doesn't strip
the annotation. kotlinx.serialization relies on the annotation itself, e.g., to pick sealed-class serializers, so an app that disables those
rules breaks kotlinx.serialization as well as our mappers.

We also don't ship rules for `@Serializable` classes with named companion objects. kotlinx.serialization's rules only cover those from
1.10.0 on. A generic rule set that fixes older versions exists, but we don't apply it because it would keep `InnerClasses` and
named-companion class names in every host app.

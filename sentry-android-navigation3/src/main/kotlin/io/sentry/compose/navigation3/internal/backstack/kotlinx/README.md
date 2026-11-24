# `io.sentry.compose.navigation3.internal.backstack.kotlinx`

This package isolates access to kotlinx.serialization APIs.

## Dependency isolation

kotlinx.serialization is a `compileOnly` dependency of `sentry-android-navigation3` that we assume is present, as every Nav3 app depends on
it via `navigation3-runtime`.

The kotlinx.serialization ***version***, however, is determined by the host app, and may be incompatible with the APIs we use. To protect
against crashes, only classes in the `kotlinx` subpackage may reference kotlinx.serialization APIs, and code outside is limited to a single
entry point, viz., `BuiltInBackStackEntryMappers.create`. That's where we catch the `LinkageError` thrown if an incompatible version can't
be linked.

Creating a mapper runs `KotlinxCompatibilityProbe` on SDK-owned `@Serializable` types, without running app serializers:

- A `LinkageError` during name lookup prevents mapper creation. Other non-fatal lookup failures are inconclusive; mapping continues
  with failures handled per entry type.

- Any non-fatal encoding failure or unexpected probe output disables property encoding. `SerialNameAndProperties` preserves names
  but replaces arguments with `incompatible_kotlinx_serialization`.

Disabling a mapper or property encoding adds `ComposeNavigation3SerialNameMappersUnavailable` or
`ComposeNavigation3PropertyEncodingDisabled`, respectively, to event SDK info, making the problem visible without debug logs.
Later name-lookup failures are cached per entry type. Property-encoding failures drop only the affected entry's arguments.

To keep our isolation policy intact:

- put any new classes that reference kotlinx.serialization in the `.kotlinx` subpackage and give them a `Kotlinx*` prefix;
- don't expose kotlinx.serialization types outside the `Kotlinx*` classes; and
- reference a `Kotlinx*` class from outside the subpackage only via `BuiltInBackStackEntryMappers.create`.

In addition, the `.kotlinx` subpackage may ***not*** depend on Android framework APIs, AndroidX, or the `internal.lifecycle` package.

## Version floor

The floor (`kotlinxSerialization-floor` in `gradle/dependency-matrix.versions.toml`) is the oldest kotlinx.serialization version our
built-in mappers support. It matches the oldest supported Nav3 release's dependency: 1.7.3 for `navigation3-runtime` 1.0.0. Raising it
could make existing Nav3 apps fall back to `/unknown` unless they also upgrade kotlinx.serialization.

We compile and test against exactly the floor. `build.gradle.kts` pins it strictly, as other dependencies also bring in
kotlinx.serialization, and `verifyKotlinxSerializationCompileVersion` fails the build if the compile classpath resolves any other version.
Compiling against a newer version could produce calls the floor's runtime lacks.

Rules for changing the floor:

- Raise it only when the oldest supported Nav3 release requires a newer kotlinx.serialization, never as a side effect of another
  dependency bump.
- Raise it in a reviewed change of its own, with a CHANGELOG entry and a note in the Nav3 integration docs.
- Update `MIN_KOTLINX_SERIALIZATION_VERSION` in `KotlinxBackStackEntryMapper.kt` to match. A unit test fails if the two differ.
- Support behavior that only newer kotlinx.serialization versions need with a version-specific code path, not by raising the floor.

## Minification

`sentry-android-navigation3` ships no keep rules for kotlinx.serialization, only `-dontwarn kotlinx.serialization.**`, so that apps without
kotlinx.serialization still build (see `nav3-no-kotlinx-r8-check`). Serial names survive R8 minification because Kotlin's serialization
compiler plugin fixes them at compile time. `KotlinxBackStackEntryMapper`'s fallback for private `@Serializable` objects
relies on members preserved by kotlinx.serialization's own consumer rules.

`KotlinxBackStackEntryMapper` only names classes whose `@Serializable` annotation is present at runtime, and `KotlinxPropertiesEncoder` only
looks up the serializers of open polymorphic values' runtime classes if they have it. kotlinx.serialization's consumer
rules (from our 1.7.3 floor on) keep that annotation: `kotlinx-serialization-common.pro` keeps `RuntimeVisibleAnnotations`, and
`kotlinx-serialization-r8.pro` keeps every `@Serializable` class (allowing shrinking and obfuscation) so that R8 full mode doesn't strip
the annotation. kotlinx.serialization relies on the annotation itself, e.g., to pick sealed-class serializers, so an app that disables those
rules breaks kotlinx.serialization as well as our mappers.

We also don't ship rules for `@Serializable` classes with named companion objects. kotlinx.serialization's rules only cover those from
1.10.0 on. A generic rule set that fixes older versions exists, but we don't apply it because it would keep `InnerClasses` and
named-companion class names in every host app.

The [R8 check](../../../../../../../../../../../sentry-android-integration-tests/nav3-backstack-entry-mapper-r8-check/README.md)
verifies both built-in mappers after minification, including named-companion lookups.

## Newer versions

CI also runs the module's unit tests that use kotlinx.serialization (`scripts/test-nav3-kotlinx-versions.sh`), and
`nav3-backstack-entry-mapper-r8-check`, against the latest stable release
(`kotlinxSerialization-stable`, bumped by Dependabot) and any newer release candidate (`kotlinxSerialization-preview`, bumped by a
weekly job in `update-deps.yml`). Release candidate failures block only PRs that change the preview version. For failure handling,
see "When a kotlinx.serialization bump fails" in the R8 check's README.

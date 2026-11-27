# `io.sentry.compose.navigation3.internal.backstack.kotlinx`

This package isolates access to kotlinx.serialization APIs used by our Nav3 integration.

## Dependency isolation

`kotlinx-serialization-core` is a `compileOnly` dependency of `sentry-android-navigation3` assumed always to be present, as every Nav3 app
depends on it via `navigation3-runtime`. We compile against the lowest version required.

The actual `kotlinx-serialization-core` version available at runtime is determined by the host app, and may be incompatible with the APIs we
compile against. To protect against crashes, only classes in the `kotlinx` subpackage may reference kotlinx.serialization APIs, and code
outside is limited to a single entry point, viz., `BuiltInBackStackEntryMappers.create`. That entry point is where we catch any
`LinkageError`s thrown if the host app uses an incompatible `kotlinx-serialization-core` version.

To keep our isolation policy intact:

- put any new classes that reference kotlinx.serialization in the `.kotlinx` subpackage and name them using a `Kotlinx*` prefix;
- don't expose kotlinx.serialization types outside the `Kotlinx*` classes; and
- reference `Kotlinx*` classes from outside the `.kotlinx` subpackage only via `BuiltInBackStackEntryMappers.create`.

## Version floor

The `kotlinxSerialization-floor` in `gradle/dependency-matrix.versions.toml` is the earliest `kotlinx-serialization-core` version our
built-in mappers support. It matches the oldest supported Nav3 release's dependency, viz., 1.7.3 for `navigation3-runtime` 1.0.0.

Support behavior that only newer `kotlinx-serialization-core` versions need with guarded, version-specific code paths.

CI runs all of `sentry-android-navigation3`'s unit tests against:

1. the `kotlinxSerialization-floor`, and
2. the latest stable release (bumped by Dependabot).

For failure handling, see "When a kotlinx.serialization bump fails" in the `nav3-built-in-back-stack-mappers-r8-check` README.

Recommended rules for raising the floor:

- Keep the current floor as long as our built-in mappers can support it. Raise it only when the oldest supported Nav3 release mandates it or
  a required SDK change is incompatible with the current floor. (In particular, verify Kotlin compiler, language version, and serialization
  plugin upgrades against the current floor. A test dependency that needs a newer version doesn't change what the floor tests run: the
  unit tests pin the version under test strictly.)

- Raise it in a reviewed change of its own, with a CHANGELOG entry and a corresponding update to the
  [Sentry Nav3 docs](https://docs.sentry.io/platforms/android/integrations/navigation/#navigation-3).

- Update `MIN_KOTLINX_SERIALIZATION_VERSION` in `KotlinxBackStackEntryMapper.kt` to match. A unit test will fail if the two differ.

## Minification

The `nav3-built-in-back-stack-mappers-r8-check` verifies the behavior of our built-in mappers after minification.

See the `proguard-rules.pro` file in `sentry-android-navigation3` for info about the minification rules applied by kotlinx.serialization.

## What if a host app doesn't use kotlinx-serialization-core?

If a host app uses `sentry-android-navigation3` without also depending on `kotlinx-serialization-core` (via Nav3 or otherwise), R8 will fail
the app's release builds, as we do not include a `-dontwarn` proguard rule for kotlinx.serialization. The host app can add that rule itself
or – more likely – remove an unintended `sentry-android-navigation3` dependency. (See the `proguard-rules.pro` file in
`sentry-android-navigation3` for more info.)

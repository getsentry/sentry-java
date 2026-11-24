# nav3-no-kotlinx-r8-check

Verifies that an R8-minified app that uses `sentry-android-navigation3` without kotlinx.serialization builds and runs.

The module can be used without Nav3, and kotlinx.serialization is a `compileOnly` dependency. R8 therefore encounters missing classes
referenced by the built-in mappers. The module's consumer `-dontwarn kotlinx.serialization.**` rule allows the build to succeed.

At runtime, the app checks that kotlinx.serialization is absent, that `BackStackEntryMapper.SerialName` and `SerialNameAndProperties` map
an entry to `null` (recorded as `/unknown`) instead of crashing, that the `ComposeNavigation3SerialNameMappersUnavailable` integration
was added to the SDK's integrations (so events show why), and that R8 actually renamed a representative class.

It's built and run by `scripts/test-nav3-backstack-entry-mapper-r8.sh`, along with `nav3-backstack-entry-mapper-r8-check`, and shares
that app's application ID, Activity name, and log tag so the script runs both the same way.

# nav3-no-kotlinx-r8-check

Verifies that an R8-minified app that uses `sentry-android-navigation3` without kotlinx.serialization builds and runs.

kotlinx.serialization is a `compileOnly` dependency of `sentry-android-navigation3`, which doesn't require Nav3, so an app can include the
module without kotlinx.serialization. R8 then sees missing classes referenced by the built-in `BackStackEntryMapper`s. The module's
consumer `-dontwarn kotlinx.serialization.**` rule keeps R8 from failing the build on them. Without the rule, this app's build fails.

At runtime, the app checks that kotlinx.serialization is absent, that `BackStackEntryMapper.SerialName` and `SerialNameAndProperties` map
an entry to `null` (recorded as `/unknown`) instead of crashing, that the `ComposeNavigation3SerialNameMappersUnavailable` integration
was added to the SDK's integrations (so events show why), and that R8 actually renamed a representative class.

It's built and run by `scripts/test-nav3-backstack-entry-mapper-r8.sh`, along with `nav3-backstack-entry-mapper-r8-check`, and shares
that app's application ID, Activity name, and log tag so the script runs both the same way.

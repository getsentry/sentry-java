# nav3-backstack-entry-mapper-r8-check

Verifies that `BackStackEntryMapper.SerialName` and
`BackStackEntryMapper.SerialNameAndProperties` from `sentry-android-navigation3` return expected Sentry names and arguments despite
R8 obfuscation. `sentry-android-navigation3` ships no kotlinx.serialization R8 rules, so the check relies on kotlinx.serialization's own
consumer rules, plus the per-class rules in this app's `proguard-rules.pro` file.

The tests check serialization names, argument encoding, and sensitive-value filtering. They also verify that R8 actually renamed
representative classes and fields.

## How to run it

Run the tests against a booted emulator or device:

```bash
./scripts/test-nav3-backstack-entry-mapper-r8.sh
```


The script builds and runs four APKs: R8 full and compat modes, each with the pinned and latest kotlinx.serialization versions. Every APK
must report `RESULT=PASS` in order to pass. Set `NAV3_EXTRA_KOTLINX_VERSIONS` to override the extra versions tested (i.e., the versions
besides the compile floor, which is always tested).

## When the weekly kotlinx.serialization bump fails

Each week, `update-deps.yml` opens or updates a PR that sets `defaultLatestKotlinx` in `scripts/test-nav3-backstack-entry-mapper-r8.sh`
to the latest kotlinx.serialization release. That PR runs this check and the module's unit tests against the new version. Apps can
adopt a release as soon as it ships, so treat a failure as a sign that apps on that version may already be affected, not as CI noise.
Don't raise the kotlinx.serialization floor to fix it (see "Version floor" in the `kotlinx` package README of
`sentry-android-navigation3`).

**The PR doesn't compile.** A kotlinx.serialization release can require a newer Kotlin compiler than the repo uses (e.g., errors about
an incompatible Kotlin metadata version or `Require-Kotlin-Version`). Our tests and this app are compiled by our Kotlin compiler against
the new release, so they can't be built until the repo upgrades Kotlin.

1. Leave the PR open and unmerged, so `defaultLatestKotlinx` stays at the last release that compiles. The updater keeps updating the
   same PR as new releases appear.
2. Open an issue to upgrade the repo's Kotlin version, and link it from the PR.
3. Merge the PR after the Kotlin upgrade lands and the PR passes.

This doesn't mean apps on the new release are broken: they compile with their own, newer Kotlin compiler. It does mean that release is
untested until the Kotlin upgrade lands.

**Tests or this check fail.** kotlinx.serialization changed behavior our mappers rely on.

1. Read the release's changelog, especially for changes to experimental APIs. `KotlinxPropertiesEncoder` subclasses the experimental
   `AbstractEncoder`, so changes to the calls serializers make on encoders matter most. A failing `KotlinxEncoderContractTest` shows
   exactly which calls changed. Its KDoc describes how to review and update its baselines.
2. Determine whether apps on the new release now record wrong names or arguments. If so, fix it with a code path for the versions that
   behave differently, so apps on older versions keep working.
3. Merge the PR together with the fix.

# nav3-backstack-entry-mapper-r8-check

Verifies that `BackStackEntryMapper.SerialName` and
`BackStackEntryMapper.SerialNameAndProperties` from `sentry-android-navigation3` return expected Sentry names and arguments despite
R8 obfuscation. `sentry-android-navigation3` ships no kotlinx.serialization keep rules, so the check relies on kotlinx.serialization's own
consumer rules, plus the per-class rules in this app's `proguard-rules.pro` file.

The tests check serialization names, argument encoding (including open polymorphic values, whose serializers are looked up by
reflection), and sensitive-value filtering. They also check that the mappers' compatibility probes passed (neither
`ComposeNavigation3SerialNameMappersUnavailable` nor `ComposeNavigation3PropertyEncodingDisabled` was added to the SDK's integrations),
and that R8 actually renamed representative classes and fields.

## How to run it

Run the tests against a booted emulator or device:

```bash
./scripts/test-nav3-backstack-entry-mapper-r8.sh
```
The script tests the kotlinx.serialization versions in `gradle/dependency-matrix.versions.toml`: the floor
(`kotlinxSerialization-floor`), the latest stable release (`kotlinxSerialization-stable`), and the latest release candidate
(`kotlinxSerialization-preview`), but the release candidate only while it's newer than the stable release. It builds and runs an APK in
R8 full and compat modes for each, so four or six APKs. It also builds and runs an APK from `nav3-no-kotlinx-r8-check`, which checks apps
without kotlinx.serialization (see its README).

Every APK must report `RESULT=PASS` in order to pass, except the release candidate's. Its build and run failures are warnings, unless
`NAV3_PREVIEW_REQUIRED=true`. CI sets that on PRs that change `kotlinxSerialization-preview`, so a release candidate that breaks
something only blocks its own PR. Set `NAV3_EXTRA_KOTLINX_VERSIONS` to override the extra versions tested (i.e., the versions besides the
floor, which is always tested). Each of those is required.

## When a kotlinx.serialization bump fails

Two kinds of PRs bump the versions this check and the module's unit tests run against:

- Dependabot opens a PR that sets `kotlinxSerialization-stable` to the latest stable release. It waits until a release is three days old
  (Dependabot's default cooldown), so it may skip a release that's quickly followed by another.
- Each week, `update-deps.yml` opens or updates a PR that sets `kotlinxSerialization-preview` to the latest release candidate, using
  `scripts/update-kotlinx-serialization-preview.sh`.

Each PR runs this check and the module's unit tests against the new version. Apps can adopt a release as soon as it ships, so treat a
failure as a sign that apps on that version may already be affected, not as CI noise. For a release candidate, it's a sign that the
next stable release may break them. Don't raise the kotlinx.serialization floor to fix it (see "Version floor" in the `kotlinx` package
README of `sentry-android-navigation3`).

**The PR doesn't compile.** A kotlinx.serialization release can require a newer Kotlin compiler than the repo uses (e.g., errors about
an incompatible Kotlin metadata version or `Require-Kotlin-Version`). Our tests and this app are compiled by
our Kotlin compiler against the new release, so they can't be built until the repo upgrades Kotlin.

1. Leave the PR open and unmerged, so the version stays at the last release that compiles. Don't close a Dependabot PR: Dependabot then
   ignores that version. Dependabot replaces its PR, and the updater updates its own, as new releases appear.
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

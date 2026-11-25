# nav3-backstack-entry-mapper-r8-check

Verifies that `BackStackEntryMapper.SerialName` and `SerialNameAndProperties` return expected names and arguments after R8 obfuscation.
The SDK ships no kotlinx.serialization keep rules; this app uses the library's consumer rules and per-class rules in `proguard-rules.pro`.

The tests check serialization names, argument encoding (including values recorded as `[Unencodable]`), and sensitive-value filtering,
which also shows that the mappers' compatibility probes passed: a failed probe records `/unknown` names or
`kotlinx_serialization_version` arguments. The app initializes Sentry with a logger that records the mappers' messages, and checks
that neither probe was inconclusive or failed (`ProbeLogChecks.kt`): an inconclusive name-lookup probe leaves names right but can't detect
an incompatible kotlinx.serialization version in minified apps. They also check that R8 actually renamed representative classes and fields.

## How to run it

Run the tests against a booted emulator or device:

```bash
./scripts/test-nav3-backstack-entry-mapper-r8.sh
```
The script tests the kotlinx.serialization versions in `gradle/dependency-matrix.versions.toml`: the floor
(`kotlinxSerialization-floor`), the latest stable release (`kotlinxSerialization-stable`), and the latest release candidate
(`kotlinxSerialization-preview`), when newer than stable. Each version runs in R8 full mode; the floor also runs in compat mode to cover
named-companion lookups with older consumer rules.

`scripts/test-nav3-kotlinx-versions.sh` runs all of the module's unit tests against the same newer versions.

Each APK must report `RESULT=PASS`. Release candidate build and run failures are advisory unless `NAV3_PREVIEW_REQUIRED=true`, which
CI sets on PRs that change `kotlinxSerialization-preview`.

Set `NAV3_EXTRA_KOTLINX_VERSIONS="1.10.0 1.12.0"` to replace the extra versions with required versions of your choice. An empty string
tests only the floor, which always runs.

## When a kotlinx.serialization bump fails

Two kinds of PRs bump the versions this check and the module's unit tests run against:

- Dependabot opens a PR that sets `kotlinxSerialization-stable` to the latest stable release. It waits until a release is three days old
  (Dependabot's default cooldown), so it may skip a release that's quickly followed by another.
- Each week, `update-deps.yml` opens or updates a PR that sets `kotlinxSerialization-preview` to the latest release candidate, using
  `scripts/update-kotlinx-serialization-preview.sh`.

Each PR runs the R8 check and unit tests against the new version. A failure may affect apps already using that release or signal a
problem in the next stable release. Keep the existing floor supported; see "Version floor" in the `kotlinx` package README.

**The PR doesn't compile.** A kotlinx.serialization release can require a newer Kotlin compiler than the repo uses (e.g., errors about
an incompatible Kotlin metadata version or `Require-Kotlin-Version`). Our tests and this app are compiled by
our Kotlin compiler against the new release, so they can't be built until the repo upgrades Kotlin.

1. Leave the PR open and unmerged, so the version stays at the last release that compiles. Don't close a Dependabot PR: Dependabot then
   ignores that version. Dependabot replaces its PR, and the updater updates its own, as new releases appear.
2. Open an issue to upgrade the repo's Kotlin version, and link it from the PR.
3. Merge the PR after the Kotlin upgrade lands and the PR passes.

This doesn't mean apps on the new release are broken: they compile with their own, newer Kotlin compiler. It does mean that release is
untested until the Kotlin upgrade lands.

**Tests or the R8 check fail.** Investigate whether kotlinx.serialization changed behavior the mappers rely on.

1. Read the release's changelog, especially for changes to experimental APIs. `KotlinxPropertiesEncoder` subclasses the experimental
   `AbstractEncoder`, so changes to the calls serializers make on encoders matter most. A failing `KotlinxEncoderContractTest` shows
   exactly which calls changed. Its KDoc describes how to review and update its baselines.
2. Determine whether apps on the new release now record wrong names or arguments. If so, fix it with a code path for the versions that
   behave differently, so apps on older versions keep working.
3. Merge the PR together with the fix.

**The compatibility probe turns off property encoding.** The R8 check reports every entry's arguments as
`{"dropped_by_sentry": "kotlinx_serialization_version"}` instead of the expected ones, and `KotlinxCompatibilityProbeTest`'s
`encoderIncompatibility()` test fails. Apps on the new release then record every
entry's arguments as `kotlinx_serialization_version`, as the probe's fixture (`ProbeClass`) didn't encode to
`EXPECTED_PROBE_ENCODING`.

1. Find out whether the arguments are wrong or only different. Compare the probe's output with `EXPECTED_PROBE_ENCODING`, and check the R8
   check's expected arguments and `KotlinxEncoderContractTest` for the same release.
2. If arguments are wrong (e.g., a wrong value, or a sensitive value that's no longer `[Filtered]`), the probe worked: fix the encoder as
   above.
3. If they're correct but recorded differently (e.g., a number's type changed), decide whether the new output is what Sentry should
   record. If it is, add the release's output as a version-specific expectation, so older versions are still checked against theirs.
   Don't loosen the comparison, e.g., to compare only keys: it exists to catch wrong values that don't throw.
4. Ship the fix in a patch release, as apps on the new release record no arguments until they update the SDK.

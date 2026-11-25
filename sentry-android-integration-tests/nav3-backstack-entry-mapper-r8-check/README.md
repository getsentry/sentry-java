# nav3-backstack-entry-mapper-r8-check

Verifies that `BackStackEntryMapper.SerialName` returns the expected names after R8 obfuscation. The SDK ships no keep rules for apps'
kotlinx.serialization classes; this app uses the library's consumer rules and per-class rules in `proguard-rules.pro`.

The tests check serial names, including `/unknown` for keys the mapper can't name, which also shows that the mapper's compatibility probe
passed: a failed probe records `/unknown` for every key. The app initializes Sentry with a logger that records the mapper's messages, and
checks that the probe wasn't inconclusive and didn't fail (`ProbeLogChecks.kt`): an inconclusive name-lookup probe leaves names right but
can't detect an incompatible kotlinx.serialization version in minified apps. It also checks that R8 actually renamed a representative
class.

## How to run it

Run the tests against a booted emulator or device:

```bash
./scripts/test-nav3-backstack-entry-mapper-r8.sh
```

The script tests the kotlinx.serialization floor (`kotlinxSerialization-floor` in `gradle/dependency-matrix.versions.toml`) in R8 full and
compat mode. Compat mode covers named-companion lookups with older consumer rules.

Each APK must report `RESULT=PASS`.

Set `NAV3_EXTRA_KOTLINX_VERSIONS="1.10.0 1.12.0"` to also test other versions, in R8 full mode.

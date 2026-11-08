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

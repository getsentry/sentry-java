# nav3-serialname-r8-check

Checks that `BackStackEntryMapper.serialName()` and `BackStackEntryMapper.serialNameWithArguments()` from
`sentry-android-navigation3` still name back stack entries and record their arguments correctly in
R8-minified release builds, using the consumer R8 rules the library ships.

The app maps a set of keys with both mappers on launch, compares each name and each set of arguments with
the expected ones, and reports the results on screen and in logcat (tag `NAV3_R8_CHECK`). One key uses
every argument encoding rule (value classes, enums, lists, sets, maps, nested and polymorphic properties,
`null`, and `@Transient`) along with sensitive-value filtering. The app also fails if R8 didn't rename
the key classes, or that key's fields, since the check would then prove nothing.

Two flavors cover apps with and without kotlinx.serialization:

- `withSerialization`: `@Serializable` keys resolve to their serial names and encoded arguments; other
  keys resolve to `null` (recorded as `/unknown`).
- `withoutSerialization`: every key resolves to `null` with both mappers, and the app must build and run
  without kotlinx.serialization on its classpath.

Run it against a booted emulator or device:

```bash
./scripts/test-nav3-serialname-r8.sh
```

The script builds both flavors in R8 full mode and compat mode, installs each APK, and fails unless every
run reports `RESULT=PASS`. CI runs the same script (`.github/workflows/integration-tests-nav3-r8.yml`).

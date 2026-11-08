# nav3-serialname-r8-check

Checks that `BackStackEntryMapper.serialName()` from `sentry-android-navigation3` still names back stack
entries correctly in R8-minified release builds, using the consumer R8 rules the library ships.

The app maps a set of keys on launch, compares each name with the expected one, and reports the results
on screen and in logcat (tag `NAV3_R8_CHECK`). It also fails if R8 didn't rename the key classes, since
the check would then prove nothing.

Two flavors cover apps with and without kotlinx.serialization:

- `withSerialization`: `@Serializable` keys resolve to their serial names; other keys resolve to `null`
  (recorded as `/unknown`).
- `withoutSerialization`: every key resolves to `null`, and the app must build and run without
  kotlinx.serialization on its classpath.

Run it against a booted emulator or device:

```bash
./scripts/test-nav3-serialname-r8.sh
```

The script builds both flavors in R8 full mode and compat mode, installs each APK, and fails unless every
run reports `RESULT=PASS`. CI runs the same script (`.github/workflows/integration-tests-nav3-r8.yml`).

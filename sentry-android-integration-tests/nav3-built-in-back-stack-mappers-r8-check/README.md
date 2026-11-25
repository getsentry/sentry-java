# nav3-built-in-back-stack-mappers-r8-check

Checks that `BackStackEntryMapper.SerialName` works after R8 obfuscation:

- Serial names, `/unknown` results, and warning reasons match expectations.
- The compatibility probe succeeds conclusively.
- R8 actually obfuscates a representative class.

The app uses kotlinx.serialization's consumer rules and its own `proguard-rules.pro`; the SDK supplies no keep rules for app serialization
classes.

## Run

Boot an emulator or device, then run:

```bash
./scripts/test-nav3-built-in-back-stack-mappers-r8.sh
```

Versions come from `gradle/dependency-matrix.versions.toml`. Both floor and latestStable run in R8 full mode; floor also runs in compat mode
to cover older named-companion rules. Each APK must report `RESULT=PASS`.

CI also runs the module's unit tests against latestStable.

## Handle a failing update

Dependabot updates `kotlinxSerialization-latestStable` after a three-day cooldown.

**Compilation failure:** if the release requires newer Kotlin, leave the PR open, file a linked Kotlin upgrade issue, and merge after the
upgrade and passing checks. Closing the PR tells Dependabot to ignore that version. The release remains untested here; apps with newer
compilers may still work.

**Test failure:** investigate changes to serializer lookup, serial descriptors, or R8 rules. Fix incorrect names or unexpected `/unknown`
results, then merge the fix with the update.

Preserve support for the floor in either case; see "Version floor" in the `kotlinx` package README.

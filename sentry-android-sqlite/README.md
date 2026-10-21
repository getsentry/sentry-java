# sentry-android-sqlite

SQLite instrumentation for AndroidX APIs.

Two instrumentation paths are supported:

- **`androidx.sqlite.SQLiteDriver`**: Used by Room 2.7+ and 3.0+. Applied automatically by the Sentry Android Gradle Plugin.
- **`androidx.sqlite.db.SupportSQLiteOpenHelper`**: Used by SQLDelight and legacy (pre-2.7) Room. Applied automatically by the Sentry Android Gradle Plugin.

To avoid duplicate spans, only one path should be used per database file. Most Room and SQLDelight APIs enforce that division. The exception is Room's `SupportSQLiteDriver`: either the `SupportSQLiteOpenHelper` it consumes should be wrapped or the support driver itself, but never both.

See the [SQLite integration docs](https://docs.sentry.io/platforms/android/integrations/room-and-sqlite/) for more details.

## Package layout

The module is organized as two separate packages:

- **`io.sentry.android.sqlite`**: Android-specific code. Depends on `android.database.*` and/or on `androidx.sqlite.db.*`.
- **`io.sentry.sqlite`**: No Android-specific code. Depends only on multiplatform `androidx.sqlite.*`.

The split anticipates future Kotlin Multiplatform support. The `androidx.sqlite.*` interfaces are defined in KMP's `commonMain` source set and are used by Room in non-JVM environments. Classes in `io.sentry.sqlite` are written against those portable interfaces and are intended to lift cleanly into a KMP `commonMain` source set if/when the `sentry` core gains multiplatform targets.

Note that the module artifact itself (`sentry-android-sqlite`) is currently an Android-only AAR regardless of package layout.

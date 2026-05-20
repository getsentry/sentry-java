# sentry-android-sqlite

This module provides automatic SQLite query instrumentation for Android.

Two instrumentation paths are supported, matching the two SQLite APIs offered by AndroidX:

- **`androidx.sqlite.SQLiteDriver`** — used by Room 2.7+ via `Room.databaseBuilder(...).setDriver(...)` and by SQLDelight via its AndroidX SQLite driver.
- **`androidx.sqlite.db.SupportSQLiteOpenHelper`** — used by legacy Room via `Room.databaseBuilder(...).openHelperFactory(...)`, or applied automatically by the Sentry Android Gradle plugin.

Please consult the [Sentry Docs](https://docs.sentry.io/platforms/android/integrations/room-and-sqlite/) for usage and migration guidance, as well as how to avoid duplicate spans when using Room's `SupportSQLiteDriver` adapter.

## Package layout

This module is organized as two separate packages:

- **`io.sentry.android.sqlite`**: Android-specific code. Classes here depend on `android.database.*` (e.g., `CrossProcessCursor`, `SQLException`) and/or on `androidx.sqlite.db.*`, the Android-only compatibility layer over the platform's SQLite. The `SentrySupportSQLiteOpenHelper` path and its span helper `SQLiteSpanManager` live here.
- **`io.sentry.sqlite`**: Code whose contract depends only on the multiplatform `androidx.sqlite.*` interfaces (e.g., `SQLiteDriver` and `SQLiteConnection`). `SentrySQLiteDriver` and its span helper `SQLiteSpanRecorder` live here.

The split anticipates the possibility of future Kotlin Multiplatform support. The `androidx.sqlite.*` driver interfaces are defined in the library's `commonMain` source set and are reused by Room across Android, JVM, and native targets. Classes in `io.sentry.sqlite` are written against those portable interfaces and are intended to lift cleanly into a KMP `commonMain` source set if/when the `sentry` core gains multiplatform targets. Classes in `io.sentry.android.sqlite` are Android-only by construction and will stay where they are.

Note that the module artifact itself (`sentry-android-sqlite`) is currently an Android-only AAR regardless of package layout.

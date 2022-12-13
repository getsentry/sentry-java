# Sentry Compose Helper Library

This utility library is used to access internal Jetpack Compose APIs using Java.

Due to [this open issue](https://youtrack.jetbrains.com/issue/KT-30878) you can not have
java sources in a KMP-enabled project which has the android-lib plugin applied.
Thus we place all relevant java code in this library for compilation,
and embed it as part of `sentry-compose`.

Once the above issue is resolved, the code of this module can be safely moved to `sentry-compose`.

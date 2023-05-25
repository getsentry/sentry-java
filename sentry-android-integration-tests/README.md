# Android performance-impact and integration tests

* [Sample app without sentry](./test-app-plain) created with Android Studio -> New Project -> Basic Activity
* [Same app, but with Sentry included](./test-app-sentry) - made part of the root project
* [App metrics test specification (yaml)](./metrics-test.yml)
* [Espresso-based benchmarks](./sentry-uitest-android-benchmark) - run within SauceLabs (see /.sauce/*.yml)
* [Espresso-based UI tests](./sentry-uitest-android) - run within SauceLabs (see /.sauce/*.yml)
  * Also used for compatibility test matrix against new AGP versions

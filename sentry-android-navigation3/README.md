# sentry-android-navigation3

This module provides an integration for [AndroidX Navigation 3](https://developer.android.com/guide/navigation/navigation-3).
It currently supports Android targets. The Maven artifact remains
`io.sentry:sentry-android-navigation3`, while public APIs live under the
`io.sentry.compose.navigation3` Kotlin package so imports can stay stable if the integration moves
to a multiplatform artifact later.

Please consult the documentation on how to install and use this integration in the Sentry Docs for [Android](https://docs.sentry.io/platforms/android/integrations/navigation3/).

## Privacy note

By default this integration does **not** attach navigation route arguments. Arguments are only
captured when you supply an `argumentsExtractor`. Anything that extractor returns is sent to Sentry
as-is (in breadcrumbs, `contexts.navigation`, and the navigation transaction); it is not gated by
`SentryOptions.isSendDefaultPii()` and is not automatically PII-scrubbed. Route arguments frequently
contain PII or secrets (user IDs, email addresses, auth tokens or deep-link query params), so only
return values that are safe to send and redact sensitive data in the extractor (or via
`beforeBreadcrumb` / `beforeSend`).

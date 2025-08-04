package io.sentry.android.core

class SentryNdk {
  companion object {
    @JvmStatic fun init(options: SentryAndroidOptions) {}

    @JvmStatic fun close() {}
  }
}

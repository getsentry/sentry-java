package io.sentry.android.distribution

import io.sentry.android.core.EmptySecureContentProvider

/**
 * ContentProvider that automatically initializes the Sentry Distribution SDK.
 *
 * This provider is automatically instantiated by the Android system when the app starts, ensuring
 * the Distribution SDK is available without requiring manual initialization in
 * Application.onCreate().
 */
public class SentryDistributionProvider : EmptySecureContentProvider() {
  override fun onCreate(): Boolean {
    // TODO: Automatic initialization will be implemented in future PR
    return true
  }
}

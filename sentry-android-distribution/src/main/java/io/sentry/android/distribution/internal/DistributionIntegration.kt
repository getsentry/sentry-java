package io.sentry.android.distribution.internal

import io.sentry.IScopes
import io.sentry.Integration
import io.sentry.SentryOptions

/**
 * Integration that automatically enables distribution functionality when the module is included.
 */
public class DistributionIntegration : Integration {
  public override fun register(scopes: IScopes, options: SentryOptions) {
    // Distribution integration automatically enables when module is present
    // No configuration needed - just having this class on the classpath enables the feature
    
    // If needed, we could initialize DistributionInternal here in the future
    // For now, Distribution.init() still needs to be called manually
  }
}
package io.sentry.gradle

import org.gradle.api.provider.SetProperty

/** Configuration for the `io.sentry.api-validation` convention plugin. */
abstract class ApiValidationExtension {
  /**
   * Packages that are public to the JVM but not part of the SDK's API, and so are left out of the
   * dump. Subpackages are left out too.
   */
  abstract val ignoredPackages: SetProperty<String>
}

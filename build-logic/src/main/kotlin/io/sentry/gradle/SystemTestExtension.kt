package io.sentry.gradle

import org.gradle.api.provider.Property

/** Configuration for the `io.sentry.systemtest` convention plugin. */
abstract class SystemTestExtension {
  /**
   * Set to `true` for samples that the system-test runner launches with the Sentry OpenTelemetry
   * Java agent (`-javaagent`). The agent jar is then tracked as a `systemTest` input so the task
   * re-runs when the agent changes, even though it is started outside the test JVM.
   */
  abstract val usesOpenTelemetryAgent: Property<Boolean>

  init {
    usesOpenTelemetryAgent.convention(false)
  }
}

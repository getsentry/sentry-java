package io.sentry.android.replay.util

/**
 * Internal, undocumented escape hatch used to make Session Replay fail fast instead of silently
 * degrading masking when an exception is swallowed (e.g. unsupported/obfuscated Compose internals).
 *
 * It is intended to be enabled only in our own sample/UI-test apps that run on real devices in CI
 * (which are release/obfuscated builds, so [io.sentry.android.replay.BuildConfig.DEBUG] can't be
 * used), so that regressions surface as crashes rather than under-masked replays. Customers should
 * never set this.
 *
 * Enable via:
 * ```
 * System.setProperty("io.sentry.replay.compose.fail-fast", "true")
 * ```
 */
internal object SentryReplayDebug {
  private const val FAIL_FAST_PROPERTY = "io.sentry.replay.compose.fail-fast"

  /**
   * Read live (not cached) so it's only evaluated on the error path and unit tests can toggle it
   * between cases.
   */
  val failFast: Boolean
    get() = "true".equals(System.getProperty(FAIL_FAST_PROPERTY), ignoreCase = true)
}

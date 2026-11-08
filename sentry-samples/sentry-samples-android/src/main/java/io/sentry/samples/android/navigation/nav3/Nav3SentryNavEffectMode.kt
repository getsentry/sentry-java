package io.sentry.samples.android.navigation.nav3

import android.content.Intent
import android.os.Bundle
import io.sentry.SentryIntegrationPackageStorage
import io.sentry.samples.android.BuildConfig

/**
 * How the Nav3 sample records navigation: through its own `SentryNavEffect` calls, through the
 * Sentry Android Gradle Plugin's Navigation 3 auto-instrumentation, or both.
 *
 * Every mode except [MANUAL] needs a build with auto-instrumentation (see
 * [Nav3AutoInstrumentationStatus]). Without it, [AUTO] records nothing.
 */
internal enum class Nav3SentryNavEffectMode(val label: String, val description: String) {
  MANUAL(
    label = "Manual",
    description = "The sample calls SentryNavEffect before each NavDisplay.",
  ),
  AUTO(
    label = "Auto-instrumentation",
    description =
      "The sample doesn't call SentryNavEffect. Auto-instrumented NavDisplays record, naming " +
        "routes after their serial names with default options, so the navigation toggles above " +
        "don't apply.",
  ),
  BOTH(
    label = "Manual + auto",
    description =
      "The sample calls SentryNavEffect before each NavDisplay, which is also " +
        "auto-instrumented. Only the manual effect should record, once per navigation.",
  ),
  MANUAL_AFTER_NAV_DISPLAY(
    label = "Manual after NavDisplay",
    description =
      "The sample calls SentryNavEffect after its NavDisplay, which is also auto-instrumented. " +
        "Unsupported placement: the first screen is recorded twice, then once per navigation.",
  );

  /** Whether the sample calls `SentryNavEffect` before its `NavDisplay`s. */
  val callsSentryNavEffectBeforeNavDisplay: Boolean
    get() = this == MANUAL || this == BOTH

  /** Whether the sample calls `SentryNavEffect` after its `NavDisplay`s. */
  val callsSentryNavEffectAfterNavDisplay: Boolean
    get() = this == MANUAL_AFTER_NAV_DISPLAY

  /** Whether the mode only makes sense in a build with auto-instrumentation. */
  val needsAutoInstrumentation: Boolean
    get() = this != MANUAL
}

/** Whether this build of the sample has Navigation 3 auto-instrumentation. */
internal object Nav3AutoInstrumentationStatus {

  /**
   * Whether the build was configured with `-PuseSagp -PsagpNavigation3` (see
   * `navigation/README.md`).
   */
  val isRequestedByBuild: Boolean
    get() = BuildConfig.SAGP_NAVIGATION3

  /**
   * Whether the plugin actually instrumented `NavDisplay`. It reports `Navigation3Instrumentation`
   * in the manifest only if the SDK and Navigation 3 versions qualify, and the SDK reads that list
   * at init.
   */
  val isInstrumented: Boolean
    get() =
      SentryIntegrationPackageStorage.getInstance()
        .integrations
        .contains(NAVIGATION3_INSTRUMENTATION)

  /** A one-line summary for the sample's UI. */
  fun summary(mode: Nav3SentryNavEffectMode): String {
    val build =
      when {
        isInstrumented -> "auto-instrumented"
        isRequestedByBuild -> "auto-instrumentation requested but not applied"
        else -> "no auto-instrumentation"
      }
    return "Recording: ${mode.label} ($build)"
  }

  private const val NAVIGATION3_INSTRUMENTATION = "Navigation3Instrumentation"
}

internal fun Intent.nav3SentryNavEffectMode(): Nav3SentryNavEffectMode =
  nav3SentryNavEffectModeOrDefault(getStringExtra(EXTRA_SENTRY_NAV_EFFECT_MODE))

internal fun Intent.putNav3SentryNavEffectMode(mode: Nav3SentryNavEffectMode): Intent =
  putExtra(EXTRA_SENTRY_NAV_EFFECT_MODE, mode.name)

internal fun Bundle.nav3SentryNavEffectMode(): Nav3SentryNavEffectMode =
  nav3SentryNavEffectModeOrDefault(getString(EXTRA_SENTRY_NAV_EFFECT_MODE))

internal fun Bundle.putNav3SentryNavEffectMode(mode: Nav3SentryNavEffectMode) {
  putString(EXTRA_SENTRY_NAV_EFFECT_MODE, mode.name)
}

private fun nav3SentryNavEffectModeOrDefault(name: String?): Nav3SentryNavEffectMode =
  Nav3SentryNavEffectMode.entries.firstOrNull { it.name == name } ?: Nav3SentryNavEffectMode.MANUAL

private const val EXTRA_SENTRY_NAV_EFFECT_MODE =
  "io.sentry.samples.android.navigation.nav3.sentry_nav_effect_mode"

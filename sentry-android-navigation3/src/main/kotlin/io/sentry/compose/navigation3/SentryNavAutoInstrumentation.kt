package io.sentry.compose.navigation3

import androidx.compose.runtime.Composable
import io.sentry.IScopes
import io.sentry.ScopesAdapter
import org.jetbrains.annotations.ApiStatus

/**
 * Entry point for the Sentry Android Gradle Plugin's Nav3 auto-instrumentation. Not for use by
 * apps; call [SentryNavEffect] instead.
 *
 * The plugin injects a call to [Effect] before each `NavDisplay(backStack = ...)` call. Apps can
 * combine any plugin version with any SDK version that has this entry point, so the compiled
 * signatures of [Effect] and [BackStackMappingType] must never change. Add a new entry point
 * instead.
 */
@ApiStatus.Internal
public object SentryNavAutoInstrumentation {

  /** How auto-instrumented back stack entries are named. */
  @ApiStatus.Internal
  public enum class BackStackMappingType {
    /**
     * Lets the SDK choose at runtime. Currently resolves to [BackStackEntryMapper.serialName]:
     * names only, no arguments.
     */
    DEFAULT,

    /** [BackStackEntryMapper.serialName]: names only, no arguments. */
    NAMES_ONLY,

    /** [BackStackEntryMapper.serialNameWithArguments]: names plus filtered arguments. */
    NAMES_AND_ARGUMENTS,
  }

  /**
   * Same as [SentryNavEffect], with the back stack entry mapper chosen by [backStackMappingType].
   *
   * Has no default parameters, so injected calls only need to pass the composer and `$changed`.
   */
  @JvmStatic
  @Composable
  public fun Effect(
    backStack: List<Any>,
    backStackMappingType: BackStackMappingType,
    options: SentryNavOptions,
  ) {
    Effect(
      backStack = backStack,
      backStackMappingType = backStackMappingType,
      options = options,
      scopes = ScopesAdapter.getInstance(),
    )
  }

  @Composable
  internal fun Effect(
    backStack: List<Any>,
    backStackMappingType: BackStackMappingType,
    options: SentryNavOptions,
    scopes: IScopes,
  ) {
    SentryNavEffect(
      backStack = backStack,
      backStackEntryMapper = backStackMappingType.toBackStackEntryMapper(),
      options = options,
      scopes = scopes,
    )
  }
}

internal fun SentryNavAutoInstrumentation.BackStackMappingType.toBackStackEntryMapper():
  BackStackEntryMapper<Any> =
  when (this) {
    SentryNavAutoInstrumentation.BackStackMappingType.DEFAULT -> defaultBackStackEntryMapper()
    SentryNavAutoInstrumentation.BackStackMappingType.NAMES_ONLY ->
      BackStackEntryMapper.serialName()
    SentryNavAutoInstrumentation.BackStackMappingType.NAMES_AND_ARGUMENTS ->
      BackStackEntryMapper.serialNameWithArguments()
  }

/**
 * Resolves [SentryNavAutoInstrumentation.BackStackMappingType.DEFAULT]. The only place that decides
 * what auto-instrumented apps record by default, so it can later read a runtime option (e.g. a Data
 * Collection setting) without changing the injected call.
 */
private fun defaultBackStackEntryMapper(): BackStackEntryMapper<Any> =
  BackStackEntryMapper.serialName()

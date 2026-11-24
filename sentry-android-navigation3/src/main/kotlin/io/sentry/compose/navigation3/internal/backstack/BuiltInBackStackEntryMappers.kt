package io.sentry.compose.navigation3.internal.backstack

import androidx.annotation.VisibleForTesting
import io.sentry.ILogger
import io.sentry.ScopesAdapter
import io.sentry.SentryLevel.WARNING
import io.sentry.compose.navigation3.BackStackEntryMapper
import io.sentry.compose.navigation3.SentryBackStackEntry
import io.sentry.compose.navigation3.internal.backstack.NormalizedSentryBackStackEntry.Companion.UNKNOWN_ENTRY_NAME
import io.sentry.compose.navigation3.internal.backstack.kotlinx.KotlinxBackStackEntryMapper
import io.sentry.compose.navigation3.internal.backstack.kotlinx.KotlinxMapperDiagnostics.Companion.INCOMPATIBLE_KOTLINX_ADVICE
import io.sentry.util.IntegrationUtils
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

internal object BuiltInBackStackEntryMappers {

  /**
   * A built-in [BackStackEntryMapper] for apps that use kotlinx.serialization and only need to
   * track the serial name of each of their back stack entries (and not each entry's properties).
   *
   * Falls back to [UnavailableBackStackEntryMapper] if a mapper can't be created.
   *
   * See [BackStackEntryMapper.SerialName] for more details.
   */
  val serialName: BackStackEntryMapper<Any> by
    lazy(LazyThreadSafetyMode.PUBLICATION) {
      // PUBLICATION avoids holding a lock while mapper creation calls the app's logger. Racing
      // threads may each create and probe a mapper and log warnings, and all but the first
      // published instance would be discarded.
      create(logger) {
        KotlinxBackStackEntryMapper(includeProperties = false, logger = logger)
      }
    }

  /**
   * Like [serialName], but also returns the properties of each back stack entry as Sentry
   * arguments.
   *
   * Falls back to [UnavailableBackStackEntryMapper] if a mapper can't be created.
   *
   * See [BackStackEntryMapper.SerialNameAndProperties] for more details.
   */
  val serialNameAndProperties: BackStackEntryMapper<Any> by
    lazy(LazyThreadSafetyMode.PUBLICATION) {
      // PUBLICATION, as for serialName.
      create(logger) {
        KotlinxBackStackEntryMapper(includeProperties = true, logger = logger)
      }
    }

  private val logger: () -> ILogger = { ScopesAdapter.getInstance().options.logger }

  /**
   * Returns the mapper created by [newKotlinxMapper], or an [UnavailableBackStackEntryMapper] if
   * the mapper can't be linked or initialized.
   */
  @VisibleForTesting
  fun create(
    logger: () -> ILogger,
    newKotlinxMapper: () -> BackStackEntryMapper<Any>,
  ): BackStackEntryMapper<Any> {
    return try {
      newKotlinxMapper()
    } catch (e: LinkageError) {
      // An incompatible kotlinx.serialization version can throw a LinkageError while the mapper is
      // created, either because a class the mapper uses can't be linked or because the mapper's
      // compatibility probe failed to look up names. We catch the error here so that every entry
      // falls back to UNKNOWN_ENTRY_NAME instead of crashing the app.
      //
      // Construction runs no host app code, only kotlinx.serialization's and the SDK's, so this
      // also catches an ExceptionInInitializerError from either. We fall back rather than crash,
      // and log the error for diagnosis.
      logger()
        .log(
          WARNING,
          // The compiler inlines INCOMPATIBLE_KOTLINX_ADVICE, and its use here doesn't access any
          // kotlinx.serialization code.
          "Nav3 built-in mapper creation failed. Using $UNKNOWN_ENTRY_NAME for all entries. See " +
            "the attached error. $INCOMPATIBLE_KOTLINX_ADVICE",
          e,
        )
      // The log above is silent unless debug is enabled. The SDK's integrations are sent with
      // every event, so this also shows why every entry is UNKNOWN_ENTRY_NAME without debug logs.
      IntegrationUtils.addIntegrationToSdkVersion(MAPPERS_UNAVAILABLE_INTEGRATION)
      UnavailableBackStackEntryMapper(logger)
    }
  }

  /**
   * Added to the SDK's integrations when a built-in mapper can't be created, e.g., because the
   * app's kotlinx.serialization version is incompatible or missing, so every entry is recorded as
   * [UNKNOWN_ENTRY_NAME].
   */
  const val MAPPERS_UNAVAILABLE_INTEGRATION: String =
    "ComposeNavigation3SerialNameMappersUnavailable"
}

/**
 * A fallback for when a built-in [BackStackEntryMapper] isn't supported by the host app.
 *
 * Maps every back stack entry to `null`, which Sentry records as [UNKNOWN_ENTRY_NAME]. Logs once
 * per entry type.
 */
internal class UnavailableBackStackEntryMapper(
  private val logger: () -> ILogger,
) : BackStackEntryMapper<Any> {

  private val loggedTypes = Collections.newSetFromMap(ConcurrentHashMap<Class<*>, Boolean>())

  override fun map(backStackEntry: Any): SentryBackStackEntry? {
    val type = backStackEntry.javaClass

    if (loggedTypes.add(type)) {
      logger()
        .log(
          WARNING,
          "Nav3 mapper unavailable for type %s. Using $UNKNOWN_ENTRY_NAME. Use a custom " +
            "BackStackEntryMapper.",
          type.name,
        )
    }

    return null
  }
}

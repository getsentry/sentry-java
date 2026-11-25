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
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

internal object BuiltInBackStackEntryMappers {

  private val logger: () -> ILogger = { ScopesAdapter.getInstance().options.logger }

  /**
   * A built-in [BackStackEntryMapper] for apps that use kotlinx.serialization and only need to
   * track the serial name of each of their back stack entries (and not each entry's properties).
   *
   * The kotlinx mapper is created when this first maps an entry; see
   * [DeferredBackStackEntryMapper]. Falls back to [UnavailableBackStackEntryMapper] if it can't be
   * created.
   *
   * See [BackStackEntryMapper.SerialName] for more details.
   */
  val serialName: BackStackEntryMapper<Any> = DeferredBackStackEntryMapper {
    create(logger) { KotlinxBackStackEntryMapper(includeProperties = false, logger = logger) }
  }

  /**
   * Like [serialName], but also returns the properties of each back stack entry as Sentry
   * arguments.
   *
   * The kotlinx mapper is created when this first maps an entry, as for [serialName].
   *
   * See [BackStackEntryMapper.SerialNameAndProperties] for more details.
   */
  val serialNameAndProperties: BackStackEntryMapper<Any> = DeferredBackStackEntryMapper {
    create(logger) { KotlinxBackStackEntryMapper(includeProperties = true, logger = logger) }
  }

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
      UnavailableBackStackEntryMapper(logger)
    }
  }
}

/**
 * A built-in [BackStackEntryMapper] that creates the mapper it delegates to when it first maps an
 * entry, rather than when the app reads it.
 *
 * Apps read the built-in mappers while composing, e.g., as an argument to `SentryNavEffect`.
 * Creating a mapper looks up serializers by reflection, encodes a probe fixture, and may log.
 * Deferring that work to the first [map] call keeps it out of composition, which Compose may
 * discard, and runs it where `SentryNavEffect` maps entries: in an effect, after the composition is
 * applied, where a failure only affects the entries being mapped.
 *
 * If [create] throws, [map] throws too, and the next [map] call tries to create the mapper again.
 */
internal class DeferredBackStackEntryMapper(
  create: () -> BackStackEntryMapper<Any>,
) : BackStackEntryMapper<Any> {

  // PUBLICATION avoids holding a lock while mapper creation calls the app's logger. Threads that
  // race on the first map() may each create and probe a mapper and log warnings, but all of them
  // use the instance published first.
  private val mapper by lazy(LazyThreadSafetyMode.PUBLICATION, create)

  override fun map(backStackEntry: Any): SentryBackStackEntry? = mapper.map(backStackEntry)
}

/**
 * A fallback for when a built-in [BackStackEntryMapper] isn't supported by the host app.
 *
 * Maps every back stack entry to `null`, which Sentry records as [UNKNOWN_ENTRY_NAME] with
 * [UnnamedReason.KOTLINX_SERIALIZATION_VERSION]. Logs once per entry type.
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

    UnnamedReasonReports.report(UnnamedReason.KOTLINX_SERIALIZATION_VERSION)
    return null
  }
}

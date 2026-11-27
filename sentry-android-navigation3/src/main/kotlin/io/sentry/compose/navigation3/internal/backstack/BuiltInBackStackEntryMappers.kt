package io.sentry.compose.navigation3.internal.backstack

import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.snapshots.Snapshot
import io.sentry.ILogger
import io.sentry.ScopesAdapter
import io.sentry.SentryLevel.WARNING
import io.sentry.compose.navigation3.BackStackEntryMapper
import io.sentry.compose.navigation3.SentryBackStackEntry
import io.sentry.compose.navigation3.internal.backstack.kotlinx.KotlinxBackStackEntryMapper
import io.sentry.compose.navigation3.internal.backstack.kotlinx.KotlinxMapperDiagnostics.Companion.INCOMPATIBLE_KOTLINX_ADVICE
import io.sentry.compose.navigation3.internal.contract.UNKNOWN_ENTRY_NAME
import io.sentry.compose.navigation3.internal.contract.UnknownEntryReason
import io.sentry.compose.navigation3.internal.contract.UnknownEntryReasonReporter
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

internal object BuiltInBackStackEntryMappers {

  private val logger: () -> ILogger = { ScopesAdapter.getInstance().options.logger }

  /**
   * A built-in [BackStackEntryMapper] for apps that use kotlinx.serialization and only need to
   * track the serial name of each of their back stack entries (and not each entry's properties).
   *
   * Falls back to [UnavailableBackStackEntryMapper] if the kotlinx mapper can't be created.
   *
   * See [BackStackEntryMapper.SerialName] for more details.
   */
  val serialName: BackStackEntryMapper<Any> =
    // Uses a deferred map because apps may read this while composing, which Compose can discard.
    // SentryNavEffect is careful to call serialName.map() only from an effect.
    DeferredBackStackEntryMapper {
      create(logger) { KotlinxBackStackEntryMapper(logger = logger) }
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
      // A missing or incompatible kotlinx-serialization-core version will throw a LinkageError, as
      // will a failed initializer in it or in the SDK. Fall back instead of crashing.
      logger()
        .log(
          WARNING,
          // Note: The compiler inlines INCOMPATIBLE_KOTLINX_ADVICE, and its use here doesn't access
          // any kotlinx.serialization code.
          "Nav3 built-in mapper creation failed. Using $UNKNOWN_ENTRY_NAME for all entries. See " +
            "the attached error. $INCOMPATIBLE_KOTLINX_ADVICE",
          e,
        )
      UnavailableBackStackEntryMapper(logger)
    }
  }
}

/**
 * A fallback for situations where the host app doesn't support a built-in [BackStackEntryMapper].
 *
 * Maps every back stack entry to `null`, which Sentry records as [UNKNOWN_ENTRY_NAME] with
 * [UnknownEntryReason.KOTLINX_SERIALIZATION_VERSION]. Logs once per entry type.
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

    UnknownEntryReasonReporter.report(UnknownEntryReason.KOTLINX_SERIALIZATION_VERSION)
    return null
  }
}

/**
 * A [BackStackEntryMapper] that creates the mapper it delegates to on its first [map] call, rather
 * than when it's constructed.
 *
 * If [create] throws, [map] throws too and the next [map] call will try to create the mapper again.
 */
internal class DeferredBackStackEntryMapper(
  create: () -> BackStackEntryMapper<Any>,
) : BackStackEntryMapper<Any> {

  // PUBLICATION avoids holding a lock while mapper creation calls the app's logger.
  private val mapper by lazy(LazyThreadSafetyMode.PUBLICATION, create)

  override fun map(backStackEntry: Any): SentryBackStackEntry? = mapper.map(backStackEntry)
}

/**
 * A Compose-compatible version of [BackStackEntryMapper] that generates [SentryBackStackEntry]s by
 * forwarding the request to the host app mapper in effect at the time [map] is called.
 *
 * Dynamically determining the current mapper lets us separate two concerns:
 *
 * 1. the lifetime of a consumer that tracks navigation state over time (e.g., [BackStackObserver]);
 *    and
 * 2. the lifetime of the mapper used to generate `SentryBackStackEntry`s.
 *
 * Without that separation, a long-lived consumer would have to choose between holding stale mapping
 * logic or recreating its own state whenever the mapper changed.
 */
internal class ForwardingBackStackEntryMapper<T : Any>(
  private val currentMapper: () -> BackStackEntryMapper<T>,
) {

  fun map(backStackEntry: T): SentryBackStackEntry? = Snapshot.withoutReadObservation {
    currentMapper().map(backStackEntry)
  }
}

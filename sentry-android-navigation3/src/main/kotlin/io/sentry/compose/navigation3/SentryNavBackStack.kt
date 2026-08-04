package io.sentry.compose.navigation3

import org.jetbrains.annotations.ApiStatus

/**
 * Describes a single app-owned Navigation 3 back stack and how Sentry should represent its entries.
 *
 * **Warning!** Values returned from [nameExtractor] and [argumentsExtractor] are sent to Sentry in
 * breadcrumbs, navigation state, and navigation transactions and are not scrubbed by the Sentry
 * SDK. Only return route names and arguments that are known to be safe or have been pre-scrubbed.
 */
@ApiStatus.Experimental
public class SentryNavBackStack<T : Any>(
  /** The navigation back stack to observe and instrument. */
  public val entries: List<T>
) {

  /** Optional extractor for a stable, human-readable route name. */
  public var nameExtractor: ((T) -> String)? = null

  /**
   * Optional extractor for route arguments. Values should be primitives (`String`, `Number`,
   * `Boolean`), `null`, or nested `Map`/`Collection` values. Other values are converted to strings.
   */
  public var argumentsExtractor: ((T) -> Map<String, Any?>)? = null

  // TODO ADAM: Scrub phase 1 references throughout.
  /**
   * Optional extractor for the [androidx.navigation3.runtime.NavEntry] content key associated with
   * a back-stack entry. This is reserved for future visible-entry tracking; phase 1 instrumentation
   * only observes [entries].
   */
  public var contentKeyExtractor: ((T) -> Any)? = null
}

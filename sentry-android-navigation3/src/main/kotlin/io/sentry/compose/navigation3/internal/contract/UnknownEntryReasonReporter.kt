package io.sentry.compose.navigation3.internal.contract

/**
 * Passes the [UnknownEntryReason] a built-in mapper knows to the back stack converter, as the
 * mapper reports a failure by returning `null`, which is public API.
 *
 * A built-in mapper [report]s the reason right before returning `null`, and the converter
 * [collect]s it around each call to the app's mapper. So a custom mapper that forwards a built-in
 * mapper's `null` forwards its reason too, while one that returns `null` itself reports nothing,
 * and one that returns a name has its reason ignored. If a custom mapper calls built-in mappers
 * several times for one entry, the last reason reported is kept.
 *
 * Kept per thread, as mappers run on the converter's thread.
 */
internal object UnknownEntryReasonReporter {

  private val reports = ThreadLocal<UnknownEntryReason?>()

  /** Reports why the built-in mapper that's about to return `null` can't name its entry. */
  fun report(reason: UnknownEntryReason) {
    reports.set(reason)
  }

  /**
   * Runs [map] and returns its result with the last reason reported while it ran, or `null` if none
   * was. Clears the report before and after, even if [map] throws, so a reason can't carry over to
   * another entry.
   */
  fun <R> collect(map: () -> R): Collected<R> {
    reports.remove()
    try {
      val result = map()
      return Collected(result, reports.get())
    } finally {
      reports.remove()
    }
  }

  /** What [collect] returns: the mapper's [result] and the [reason] reported while it ran. */
  data class Collected<out R>(val result: R, val reason: UnknownEntryReason?)
}

package io.sentry.tests.nav3.r8

import io.sentry.Hint
import io.sentry.SentryEvent
import io.sentry.SentryOptions
import java.util.Collections

/**
 * The `unknown_entry_reason` values this check expects, as `UnknownEntryReason` serializes them.
 */
const val ENTRY_NOT_ANNOTATED = "entry_not_annotated"
const val GENERIC_ENTRY_TYPE = "generic_entry_type"
const val SERIALIZER_NOT_FOUND = "serializer_not_found"
const val SERIALIZER_ACCESS_THREW = "serializer_access_threw"

/** One back stack entry as SentryNavEffect records it in the `Navigation` context. */
class RecordedEntry(val name: String?, val unknownEntryReason: String?, val hasArguments: Boolean)

/**
 * Captures the back stack SentryNavEffect records, by reading the `Navigation` context of an event
 * that's dropped before it's sent.
 */
class BackStackRecorder : SentryOptions.BeforeSendCallback {

  @Volatile private var backStack: List<RecordedEntry>? = null

  /** What the last check event carried, for failure messages. */
  @Volatile
  var lastEventSummary: String = "no check event reached beforeSend"
    private set

  override fun execute(event: SentryEvent, hint: Hint): SentryEvent? {
    if (event.message?.formatted == CHECK_EVENT_MESSAGE) {
      val context = event.contexts[NAVIGATION_CONTEXT]
      lastEventSummary =
        "last check event's contexts: ${Collections.list(event.contexts.keys())}, $NAVIGATION_CONTEXT: $context"
      backStack = context?.let(::readBackStack)
    }
    return null
  }

  /**
   * Captures an event and returns the back stack recorded on it, top entry first, or `null` if none
   * is recorded yet.
   */
  fun recordedBackStack(capture: (String) -> Unit): List<RecordedEntry>? {
    backStack = null
    capture(CHECK_EVENT_MESSAGE)
    return backStack
  }

  private fun readBackStack(context: Any): List<RecordedEntry>? =
    ((context as? Map<*, *>)?.get(BACK_STACK) as? List<*>)?.map { entry ->
      val fields = entry as Map<*, *>
      RecordedEntry(
        name = fields["entry"] as String?,
        unknownEntryReason = fields["unknown_entry_reason"] as String?,
        hasArguments = "entry_arguments" in fields,
      )
    }

  private companion object {
    const val CHECK_EVENT_MESSAGE = "nav3-r8-check"
    const val NAVIGATION_CONTEXT = "Navigation"
    const val BACK_STACK = "Back Stack"
  }
}

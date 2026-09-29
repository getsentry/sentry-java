package io.sentry.android.buddy

import io.sentry.SentryEvent
import io.sentry.android.buddy.model.BuddyExceptionFrame
import io.sentry.android.buddy.model.BuddyExceptionReport
import io.sentry.protocol.SentryStackFrame

/**
 * Flattens a captured [SentryEvent] into a [BuddyExceptionReport] for the overlay, or null when the
 * event carries no exception. Frames are reversed so the crash site is first.
 */
internal fun SentryEvent.toBuddyExceptionReport(id: Long): BuddyExceptionReport? {
  val exception = exceptions?.lastOrNull() ?: return null
  val frames =
    exception.stacktrace?.frames.orEmpty().asReversed().map { it.toBuddyExceptionFrame() }
  return BuddyExceptionReport(
    id = id,
    type = exception.type ?: throwable?.javaClass?.name ?: "Exception",
    value = exception.value ?: throwable?.message,
    frames = frames,
  )
}

private fun SentryStackFrame.toBuddyExceptionFrame(): BuddyExceptionFrame =
  BuddyExceptionFrame(
    function = function,
    module = module,
    filename = filename,
    lineno = lineno,
    inApp = isInApp == true,
  )

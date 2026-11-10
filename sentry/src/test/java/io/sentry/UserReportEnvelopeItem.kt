package io.sentry

import io.sentry.protocol.SentryId

/**
 * The SDK no longer creates `user_report` items, but hybrid SDKs and envelopes cached by older
 * versions can still send them through rate limiting and client reports.
 */
fun userReportEnvelopeItem(): SentryEnvelopeItem {
  val data = "{\"event_id\":\"${SentryId()}\"}".toByteArray()
  val header =
    SentryEnvelopeItemHeader(
      SentryItemType.UserFeedback,
      data.size,
      "application/json",
      null,
      null,
      null,
      null,
    )
  return SentryEnvelopeItem(header, data)
}

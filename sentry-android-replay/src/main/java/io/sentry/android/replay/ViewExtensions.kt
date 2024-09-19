package io.sentry.android.replay

import android.view.View

/**
 * Marks this view to be redacted in session replay.
 */
fun View.sentryReplayRedact() {
    setTag(R.id.sentry_privacy, "redact")
}

/**
 * Marks this view to be ignored from redaction in session.
 * All its content will be visible in the replay, use with caution.
 */
fun View.sentryReplayIgnore() {
    setTag(R.id.sentry_privacy, "ignore")
}

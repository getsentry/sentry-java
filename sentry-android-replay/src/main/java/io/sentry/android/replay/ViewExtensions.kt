package io.sentry.android.replay

import android.view.View

/** Marks this view to be masked in session replay. */
public fun View.sentryReplayMask() {
  setTag(R.id.sentry_privacy, "mask")
}

/**
 * Marks this view to be unmasked in session replay. All its content will be visible in the replay,
 * use with caution.
 */
public fun View.sentryReplayUnmask() {
  setTag(R.id.sentry_privacy, "unmask")
}

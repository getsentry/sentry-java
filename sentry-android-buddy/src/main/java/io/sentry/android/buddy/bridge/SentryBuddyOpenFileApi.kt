package io.sentry.android.buddy.bridge

import android.content.Context
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
public interface SentryBuddyOpenFileApi {
  /** Asks the buddy host to open [path] (optionally at [line]) in a running Android Studio. */
  public fun open(context: Context, path: String, line: Int?)
}

@ApiStatus.Experimental
public object DummySentryBuddyOpenFileApi : SentryBuddyOpenFileApi {
  override fun open(context: Context, path: String, line: Int?) {
    // Without a buddy host there is nothing on the device that can open a host source file.
  }
}

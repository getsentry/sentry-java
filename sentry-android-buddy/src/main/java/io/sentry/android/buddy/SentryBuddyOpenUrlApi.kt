package io.sentry.android.buddy

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
public interface SentryBuddyOpenUrlApi {
  public fun open(context: Context, url: String)
}

@ApiStatus.Experimental
public object DummySentryBuddyOpenUrlApi : SentryBuddyOpenUrlApi {
  override fun open(context: Context, url: String) {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      Handler(Looper.getMainLooper()).post { open(context, url) }
      return
    }
    try {
      context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
      // A debug overlay should not crash the app when no browser can handle the link.
    }
  }
}

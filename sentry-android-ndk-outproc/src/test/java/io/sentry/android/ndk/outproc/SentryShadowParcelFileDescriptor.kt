package io.sentry.android.ndk.outproc

import android.os.ParcelFileDescriptor
import java.io.File
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowParcelFileDescriptor

// the real detachFd needs Android's FileDescriptor ownership API, missing on the JVM
@Implements(ParcelFileDescriptor::class)
class SentryShadowParcelFileDescriptor : ShadowParcelFileDescriptor() {
  @Suppress("unused") @Implementation fun detachFd(): Int = -1

  companion object {
    // the real adoptFd needs the same API
    @Suppress("unused", "UNUSED_PARAMETER")
    @Implementation
    @JvmStatic
    fun adoptFd(fd: Int): ParcelFileDescriptor =
      ParcelFileDescriptor.open(File("/dev/null"), ParcelFileDescriptor.MODE_READ_ONLY)
  }
}

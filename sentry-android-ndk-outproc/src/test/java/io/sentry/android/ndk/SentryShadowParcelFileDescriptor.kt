package io.sentry.android.ndk

import android.os.ParcelFileDescriptor
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowParcelFileDescriptor

// the real detachFd needs Android's FileDescriptor ownership API, missing on the JVM
@Implements(ParcelFileDescriptor::class)
class SentryShadowParcelFileDescriptor : ShadowParcelFileDescriptor() {
  @Suppress("unused") @Implementation fun detachFd(): Int = -1
}

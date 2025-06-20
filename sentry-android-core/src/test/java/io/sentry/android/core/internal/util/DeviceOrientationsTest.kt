package io.sentry.android.core.internal.util

import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.content.res.Configuration.ORIENTATION_PORTRAIT
import android.content.res.Configuration.ORIENTATION_SQUARE
import android.content.res.Configuration.ORIENTATION_UNDEFINED
import io.sentry.android.core.internal.util.DeviceOrientations.getOrientation
import io.sentry.protocol.Device
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeviceOrientationsTest {
  @Test
  fun `ORIENTATION_UNDEFINED returns null`() {
    assertNull(getOrientation(ORIENTATION_UNDEFINED))
  }

  @Test
  fun `ORIENTATION_SQUARE returns null`() {
    assertNull(getOrientation(ORIENTATION_SQUARE))
  }

  @Test
  fun `ORIENTATION_PORTRAIT returns PORTRAIT`() {
    assertEquals(Device.DeviceOrientation.PORTRAIT, getOrientation(ORIENTATION_PORTRAIT))
  }

  @Test
  fun `ORIENTATION_LANDSCAPE returns LANDSCAPE`() {
    assertEquals(Device.DeviceOrientation.LANDSCAPE, getOrientation(ORIENTATION_LANDSCAPE))
  }
}

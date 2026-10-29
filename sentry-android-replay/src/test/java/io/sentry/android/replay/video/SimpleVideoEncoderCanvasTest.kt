package io.sentry.android.replay.video

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBuild

@RunWith(AndroidJUnit4::class)
@Config(sdk = [30])
class SimpleVideoEncoderCanvasTest {

  @Test
  fun `uses hardware canvas on regular devices`() {
    ShadowBuild.setManufacturer("Google")
    ShadowBuild.setHardware("tensor")

    assertThat(shouldUseSoftwareCanvas()).isFalse()
  }

  @Test
  fun `uses software canvas on Xiaomi devices`() {
    ShadowBuild.setManufacturer("Xiaomi")

    assertThat(shouldUseSoftwareCanvas()).isTrue()
  }

  @Test
  fun `uses software canvas on Motorola devices`() {
    ShadowBuild.setManufacturer("motorola")

    assertThat(shouldUseSoftwareCanvas()).isTrue()
  }

  @Test
  fun `uses software canvas on ranchu emulators`() {
    ShadowBuild.setManufacturer("Google")
    ShadowBuild.setHardware("ranchu")

    assertThat(shouldUseSoftwareCanvas()).isTrue()
  }

  @Test
  fun `uses software canvas on goldfish emulators`() {
    ShadowBuild.setManufacturer("unknown")
    ShadowBuild.setHardware("goldfish")

    assertThat(shouldUseSoftwareCanvas()).isTrue()
  }
}

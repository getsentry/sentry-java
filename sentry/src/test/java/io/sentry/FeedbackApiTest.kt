package io.sentry

import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class FeedbackApiTest {

  private class Fixture {
    val shakeController = mock<SentryFeedbackOptions.IShakeController>()
    val options = SentryOptions().apply { feedbackOptions.setShakeController(shakeController) }
    val scopes = mock<IScopes>().also { whenever(it.options).thenReturn(options) }

    fun getSut(): FeedbackApi = FeedbackApi(scopes)
  }

  private val fixture = Fixture()

  @Test
  fun `enableOnShake delegates to the shake controller`() {
    fixture.getSut().enableOnShake()

    verify(fixture.shakeController).enableOnShake()
  }

  @Test
  fun `disableOnShake delegates to the shake controller`() {
    fixture.getSut().disableOnShake()

    verify(fixture.shakeController).disableOnShake()
  }

  @Test
  fun `isOnShakeEnabled delegates to the shake controller`() {
    whenever(fixture.shakeController.isOnShakeEnabled).thenReturn(true)

    assertThat(fixture.getSut().isOnShakeEnabled).isTrue()
    verify(fixture.shakeController).isOnShakeEnabled
  }

  @Test
  fun `default shake controller is disabled`() {
    assertThat(SentryOptions().feedbackOptions.shakeController.isOnShakeEnabled).isFalse()
  }
}

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
  fun `enableFeedbackOnShake delegates to the shake controller`() {
    fixture.getSut().enableFeedbackOnShake()

    verify(fixture.shakeController).enable()
  }

  @Test
  fun `disableFeedbackOnShake delegates to the shake controller`() {
    fixture.getSut().disableFeedbackOnShake()

    verify(fixture.shakeController).disable()
  }

  @Test
  fun `isFeedbackOnShakeEnabled delegates to the shake controller`() {
    whenever(fixture.shakeController.isEnabled).thenReturn(true)

    assertThat(fixture.getSut().isFeedbackOnShakeEnabled).isTrue()
    verify(fixture.shakeController).isEnabled
  }

  @Test
  fun `default shake controller is disabled`() {
    assertThat(SentryOptions().feedbackOptions.shakeController.isEnabled).isFalse()
  }
}

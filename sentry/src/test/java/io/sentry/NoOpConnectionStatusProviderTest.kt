package io.sentry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import org.mockito.kotlin.mock

class NoOpConnectionStatusProviderTest {
  private val provider = NoOpConnectionStatusProvider()

  @Test
  fun `provider returns unknown status`() {
    assertEquals(IConnectionStatusProvider.ConnectionStatus.UNKNOWN, provider.connectionStatus)
  }

  @Test
  fun `connection type returns null`() {
    assertNull(provider.connectionType)
  }

  @Test
  fun `adding a listener is a no-op and returns false`() {
    val result =
      provider.addConnectionStatusObserver(
        mock<IConnectionStatusProvider.IConnectionStatusObserver>()
      )
    assertFalse(result)
  }

  @Test
  fun `removing a listener is a no-op`() {
    provider.addConnectionStatusObserver(
      mock<IConnectionStatusProvider.IConnectionStatusObserver>()
    )
  }
}

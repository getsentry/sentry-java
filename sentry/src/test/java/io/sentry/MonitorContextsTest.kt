package io.sentry

import io.sentry.protocol.SerializationUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import org.mockito.kotlin.mock

class MonitorContextsTest {
  @Test
  fun `serializes entries in alphabetical order`() {
    val contexts =
      MonitorContexts().apply {
        put("b", 2)
        put("a", 1)
      }

    assertEquals("{\"a\":1,\"b\":2}", SerializationUtils.serializeToString(contexts, mock()))
  }
}

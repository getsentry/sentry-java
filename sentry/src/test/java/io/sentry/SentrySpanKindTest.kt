package io.sentry

import com.google.common.truth.Truth.assertThat
import kotlin.test.Test

class SentrySpanKindTest {
  @Test
  fun `values match the sentry kind convention`() {
    assertThat(SentrySpanKind.CLIENT).isEqualTo("client")
    assertThat(SentrySpanKind.SERVER).isEqualTo("server")
    assertThat(SentrySpanKind.PRODUCER).isEqualTo("producer")
    assertThat(SentrySpanKind.CONSUMER).isEqualTo("consumer")
    assertThat(SentrySpanKind.INTERNAL).isEqualTo("internal")
  }
}

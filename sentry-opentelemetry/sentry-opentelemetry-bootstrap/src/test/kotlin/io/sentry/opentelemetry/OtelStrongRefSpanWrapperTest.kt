package io.sentry.opentelemetry

import com.google.common.truth.Truth.assertThat
import io.opentelemetry.api.trace.Span
import io.sentry.ISpan
import io.sentry.SentryLongDate
import kotlin.test.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class OtelStrongRefSpanWrapperTest {
  @Test
  fun `startChild with timestamp forwards to delegate`() {
    val delegate = mock<IOtelSpanWrapper>()
    val wrapper = OtelStrongRefSpanWrapper(mock<Span>(), delegate)
    val timestamp = SentryLongDate(1234)
    val expectedChild = mock<ISpan>()
    whenever(delegate.startChild("child-op", "description", timestamp)).thenReturn(expectedChild)

    val child = wrapper.startChild("child-op", "description", timestamp)

    verify(delegate).startChild("child-op", "description", timestamp)
    assertThat(child).isSameInstanceAs(expectedChild)
  }
}

package io.sentry.compose.navigation3.internal.backstack.kotlinx

import com.google.common.truth.Truth.assertThat
import io.sentry.compose.navigation3.internal.backstack.ArgumentDropReason
import io.sentry.compose.navigation3.internal.backstack.ArgumentSink
import io.sentry.compose.navigation3.internal.backstack.DropArguments
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions

class KotlinxUnencodableArgumentsTest {

  @Test
  fun `reads as only the encoding failure`() {
    val arguments: Map<String, Any?> = KotlinxUnencodableArguments

    assertThat(arguments)
      .containsExactly(
        ArgumentDropReason.ARGUMENT_DROP_REASON_KEY,
        ArgumentDropReason.ENCODING_FAILED.serializedValue,
      )
  }

  @Test
  fun `writeTo() - drops the arguments as an unlogged encoding failure without writing to the sink`() {
    val sink = mock<ArgumentSink>()

    val thrown =
      assertThrows(DropArguments::class.java) { KotlinxUnencodableArguments.writeTo(sink) }

    assertThat(thrown.reason).isEqualTo(ArgumentDropReason.ENCODING_FAILED)
    assertThat(thrown.warning).isNull()
    verifyNoInteractions(sink)
  }
}

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
  fun `reads as only the kotlinx serialization incompatibility drop reason`() {
    val arguments: Map<String, Any?> = KotlinxUnencodableArguments

    assertThat(arguments)
      .containsExactly(
        ArgumentDropReason.ARGUMENT_DROP_REASON_KEY,
        ArgumentDropReason.KOTLINX_SERIALIZATION_VERSION.serializedValue,
      )
  }

  @Test
  fun `writeTo() - drops the arguments for kotlinx serialization incompatibility without logging or writing to the sink`() {
    val sink = mock<ArgumentSink>()

    val thrown =
      assertThrows(DropArguments::class.java) { KotlinxUnencodableArguments.writeTo(sink) }

    assertThat(thrown.reason).isEqualTo(ArgumentDropReason.KOTLINX_SERIALIZATION_VERSION)
    assertThat(thrown.warning).isNull()
    verifyNoInteractions(sink)
  }
}

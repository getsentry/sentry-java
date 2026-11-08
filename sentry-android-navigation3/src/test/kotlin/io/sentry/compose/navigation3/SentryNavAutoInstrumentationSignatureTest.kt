package io.sentry.compose.navigation3

import androidx.compose.runtime.Composer
import com.google.common.truth.Truth.assertThat
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import org.junit.Test

/**
 * The Sentry Android Gradle Plugin redirects navigation3-ui's call to `rememberDecoratedNavEntries`
 * to [SentryNavAutoInstrumentation.rememberSentryDecoratedNavEntries] by owner and name, keeping
 * the call's descriptor. Any SDK version may meet any plugin version, so that compiled method must
 * keep this exact signature.
 */
class SentryNavAutoInstrumentationSignatureTest {

  private val redirectedParameterTypes =
    arrayOf(
      List::class.java,
      List::class.java,
      Function1::class.java,
      Composer::class.java,
      Int::class.javaPrimitiveType,
      Int::class.javaPrimitiveType,
    )

  @Test
  fun `the redirect target keeps its frozen public static (List, List, Function1, Composer, int, int) signature`() {
    val method: Method =
      SentryNavAutoInstrumentation::class
        .java
        .getMethod("rememberSentryDecoratedNavEntries", *redirectedParameterTypes)

    assertThat(Modifier.isPublic(method.modifiers)).isTrue()
    assertThat(Modifier.isStatic(method.modifiers)).isTrue()
    assertThat(method.returnType).isEqualTo(List::class.java)
  }

  @Test
  fun `the frozen signature matches navigation3's rememberDecoratedNavEntries, whose calls are redirected`() {
    val original: Method =
      Class.forName("androidx.navigation3.runtime.DecoratedNavEntriesKt")
        .getMethod("rememberDecoratedNavEntries", *redirectedParameterTypes)

    assertThat(Modifier.isStatic(original.modifiers)).isTrue()
    assertThat(original.returnType).isEqualTo(List::class.java)
  }
}

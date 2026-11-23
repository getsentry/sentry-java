package io.sentry.compose.navigation3.internal

import com.google.common.truth.Truth.assertThat
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import org.junit.Test

/**
 * The Sentry Android Gradle Plugin redirects navigation3-ui's call to `rememberDecoratedNavEntries`
 * to [SentryNavAutoInstrumentation.rememberSentryDecoratedNavEntries] by owner and name, keeping
 * the call's descriptor. Any SDK version may meet any plugin version, so the owner's binary name,
 * the method's name, and its descriptor must never change.
 *
 * The owner, name, and descriptor are the same literals the plugin uses, not class or method
 * references, so that moving, renaming, or re-signing the method fails here rather than with
 * `NoClassDefFoundError` or `NoSuchMethodError` in apps.
 */
class SentryNavAutoInstrumentationSignatureTest {

  /** The plugin's redirect owner, as a binary name. Must match the plugin's. */
  private val redirectOwner = "io.sentry.compose.navigation3.internal.SentryNavAutoInstrumentation"
  /** The plugin's redirect target name. Must match the plugin's. */
  private val redirectName = "rememberSentryDecoratedNavEntries"
  /** The descriptor of the redirected call, which the plugin keeps unchanged. */
  private val redirectedDescriptor =
    "(Ljava/util/List;Ljava/util/List;Lkotlin/jvm/functions/Function1;" +
      "Landroidx/compose/runtime/Composer;II)Ljava/util/List;"

  @Test
  fun `the redirect target exists under its frozen owner, name, and descriptor and is public static`() {
    val method = declaredMethod(redirectOwner, redirectName, redirectedDescriptor)

    assertThat(Modifier.isPublic(method.modifiers)).isTrue()
    assertThat(Modifier.isStatic(method.modifiers)).isTrue()
  }

  @Test
  fun `navigation3's rememberDecoratedNavEntries, whose calls are redirected, has the frozen descriptor`() {
    val original =
      declaredMethod(
        "androidx.navigation3.runtime.DecoratedNavEntriesKt",
        "rememberDecoratedNavEntries",
        redirectedDescriptor,
      )

    assertThat(Modifier.isStatic(original.modifiers)).isTrue()
  }

  /**
   * Looks up a method by JVM descriptor, failing with the owner's actual descriptors for `name`.
   */
  private fun declaredMethod(owner: String, name: String, descriptor: String): Method {
    val methodsByDescriptor =
      Class.forName(owner)
        .declaredMethods
        .filter { it.name == name }
        .associateBy { it.descriptor() }
    assertThat(methodsByDescriptor).containsKey(descriptor)
    return methodsByDescriptor.getValue(descriptor)
  }

  private fun Method.descriptor(): String =
    parameterTypes.joinToString("", "(", ")") { it.descriptor() } + returnType.descriptor()

  private fun Class<*>.descriptor(): String =
    when {
      this == Void.TYPE -> "V"
      this == Integer.TYPE -> "I"
      this == java.lang.Long.TYPE -> "J"
      this == java.lang.Boolean.TYPE -> "Z"
      this == java.lang.Byte.TYPE -> "B"
      this == Character.TYPE -> "C"
      this == java.lang.Short.TYPE -> "S"
      this == java.lang.Float.TYPE -> "F"
      this == java.lang.Double.TYPE -> "D"
      isArray -> name.replace('.', '/')
      else -> "L${name.replace('.', '/')};"
    }
}

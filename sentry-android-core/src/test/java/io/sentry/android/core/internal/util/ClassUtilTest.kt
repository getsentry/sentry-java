package io.sentry.android.core.internal.util

import java.util.concurrent.Callable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClassUtilTest {
  class Outer {
    class Inner {
      val x: Callable<Boolean> = Callable<Boolean> { false }
    }
  }

  @Test
  fun `getClassName returns cannonical name by default`() {
    val name = ClassUtil.getClassName(Outer.Inner())
    assertEquals("io.sentry.android.core.internal.util.ClassUtilTest.Outer.Inner", name)
  }

  @Test
  fun `getClassName falls back to simple name for anonymous classes`() {
    val name = ClassUtil.getClassName(Outer.Inner().x)
    assertTrue(name!!.contains("$"))
  }

  @Test
  fun `getClassName returns null when obj is null`() {
    val name = ClassUtil.getClassName(null)
    assertNull(name)
  }
}

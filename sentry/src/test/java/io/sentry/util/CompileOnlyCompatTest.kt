package io.sentry.util

import io.sentry.util.CompileOnlyCompat.CompileOnlyCall
import io.sentry.util.CompileOnlyCompat.Fallback
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CompileOnlyCompatTest {

  @Test
  fun `ifAbsent returns call result when method exists`() {
    val result = CompileOnlyCall { "hello" }.ifAbsent("fallback")
    assertEquals("hello", result)
  }

  @Test
  fun `ifAbsent returns constant fallback on LinkageError`() {
    val result = CompileOnlyCall<String> { throw NoSuchMethodError() }.ifAbsent("fallback")
    assertEquals("fallback", result)
  }

  @Test
  fun `ifAbsent does not catch non-LinkageErrors`() {
    assertFailsWith<IllegalStateException> {
      CompileOnlyCall<String> { throw IllegalStateException() }.ifAbsent("fallback")
    }
  }

  @Test
  fun `ifAbsent with Fallback returns call result when method exists`() {
    val result = CompileOnlyCall { "hello" }.ifAbsent(Fallback { _ -> "fallback" })
    assertEquals("hello", result)
  }

  @Test
  fun `ifAbsent with Fallback invokes fallback on LinkageError`() {
    var fallbackInvoked = false
    val result =
      CompileOnlyCall<String> { throw NoSuchMethodError() }
        .ifAbsent { _ ->
          fallbackInvoked = true
          "fallback"
        }
    assertEquals("fallback", result)
    assertTrue(fallbackInvoked)
  }

  @Test
  fun `ifAbsent with Fallback passes the LinkageError`() {
    var captured: LinkageError? = null
    CompileOnlyCall<String> { throw NoSuchMethodError("test") }
      .ifAbsent { error ->
        captured = error
        "fallback"
      }
    assertTrue(captured is NoSuchMethodError)
    assertEquals("test", captured!!.message)
  }

  @Test
  fun `ifAbsent with Fallback does not invoke fallback on success`() {
    var fallbackInvoked = false
    CompileOnlyCall { "hello" }
      .ifAbsent { _ ->
        fallbackInvoked = true
        "fallback"
      }
    assertTrue(!fallbackInvoked)
  }

  @Test
  fun `ifAbsent with Fallback does not catch non-LinkageErrors`() {
    assertFailsWith<IllegalStateException> {
      CompileOnlyCall<String> { throw IllegalStateException() }.ifAbsent { _ -> "fallback" }
    }
  }
}

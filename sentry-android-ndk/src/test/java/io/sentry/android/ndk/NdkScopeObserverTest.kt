package io.sentry.android.ndk

import io.sentry.Breadcrumb
import io.sentry.DateUtils
import io.sentry.JsonSerializer
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.ndk.INativeScope
import io.sentry.protocol.User
import io.sentry.test.DeferredExecutorService
import io.sentry.test.ImmediateExecutorService
import kotlin.test.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class NdkScopeObserverTest {
  private class Fixture {
    val nativeScope = mock<INativeScope>()
    val options =
      SentryOptions().apply {
        setSerializer(JsonSerializer(mock()))
        executorService = ImmediateExecutorService()
      }

    fun getSut(): NdkScopeObserver = NdkScopeObserver(options, nativeScope)
  }

  private val fixture = Fixture()

  @Test
  fun `set tag forwards call to scope sync`() {
    val sut = fixture.getSut()

    sut.setTag("a", "b")

    verify(fixture.nativeScope).setTag("a", "b")
  }

  @Test
  fun `remove tag forwards call to scope sync`() {
    val sut = fixture.getSut()

    sut.removeTag("a")

    verify(fixture.nativeScope).removeTag("a")
  }

  @Test
  fun `set extra forwards call to scope sync`() {
    val sut = fixture.getSut()

    sut.setExtra("a", "b")

    verify(fixture.nativeScope).setExtra("a", "b")
  }

  @Test
  fun `remove extra forwards call to scope sync`() {
    val sut = fixture.getSut()

    sut.removeExtra("a")

    verify(fixture.nativeScope).removeExtra("a")
  }

  @Test
  fun `set user forwards call to scope sync`() {
    val sut = fixture.getSut()

    val user =
      User().apply {
        id = "id"
        email = "email"
        ipAddress = "ip"
        username = "username"
      }
    sut.setUser(user)

    verify(fixture.nativeScope)
      .setUser(eq(user.id), eq(user.email), eq(user.ipAddress), eq(user.username))
  }

  @Test
  fun `remove user forwards call to scope sync`() {
    val sut = fixture.getSut()

    sut.setUser(null)

    verify(fixture.nativeScope).removeUser()
  }

  @Test
  fun `set breadcrumb forwards call to scope sync`() {
    val sut = fixture.getSut()

    val breadcrumb =
      Breadcrumb().apply {
        level = SentryLevel.DEBUG
        message = "message"
        category = "category"
        setData("a", "b")
        type = "type"
      }
    val timestamp = DateUtils.getTimestamp(breadcrumb.timestamp)
    val data = "{\"a\":\"b\"}"

    sut.addBreadcrumb(breadcrumb)

    verify(fixture.nativeScope)
      .addBreadcrumb(
        eq("debug"),
        eq(breadcrumb.message),
        eq(breadcrumb.category),
        eq(breadcrumb.type),
        eq(timestamp),
        eq(data),
      )
  }

  @Test
  fun `scope sync utilizes executor service`() {
    val executorService = DeferredExecutorService()
    fixture.options.executorService = executorService
    val sut = fixture.getSut()

    sut.setTag("a", "b")
    sut.removeTag("a")
    sut.setExtra("a", "b")
    sut.removeExtra("a")
    sut.setUser(User())
    sut.addBreadcrumb(Breadcrumb())

    // as long as the executor service is not run, the scope sync is not called
    verify(fixture.nativeScope, never()).setTag(any(), any())
    verify(fixture.nativeScope, never()).removeTag(any())
    verify(fixture.nativeScope, never()).setExtra(any(), any())
    verify(fixture.nativeScope, never()).removeExtra(any())
    verify(fixture.nativeScope, never()).setUser(any(), any(), any(), any())
    verify(fixture.nativeScope, never()).addBreadcrumb(any(), any(), any(), any(), any(), any())

    // when the executor service is run, the scope sync is called
    executorService.runAll()

    verify(fixture.nativeScope).setTag(any(), any())
    verify(fixture.nativeScope).removeTag(any())
    verify(fixture.nativeScope).setExtra(any(), any())
    verify(fixture.nativeScope).removeExtra(any())
    verify(fixture.nativeScope).setUser(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
    verify(fixture.nativeScope)
      .addBreadcrumb(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
  }
}

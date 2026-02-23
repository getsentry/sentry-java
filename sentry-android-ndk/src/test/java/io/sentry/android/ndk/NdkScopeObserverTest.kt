package io.sentry.android.ndk

import io.sentry.Attachment
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

/** A mock-friendly interface combining INativeScope and INativeScopeAttachments. */
private interface NativeScopeWithAttachments : INativeScope, INativeScopeAttachments

class NdkScopeObserverTest {
  private class Fixture {
    val nativeScope = mock<NativeScopeWithAttachments>()
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

  @Test
  fun `addAttachment with pathname calls attachFile`() {
    val sut = fixture.getSut()

    sut.addAttachment(Attachment("/data/files/log.txt", "log.txt", "text/plain"))

    verify(fixture.nativeScope).attachFile("/data/files/log.txt")
  }

  @Test
  fun `addAttachment with bytes calls attachBytes`() {
    val sut = fixture.getSut()
    val bytes = byteArrayOf(1, 2, 3)

    sut.addAttachment(Attachment(bytes, "data.bin"))

    verify(fixture.nativeScope).attachBytes(eq(bytes), eq("data.bin"))
  }

  @Test
  fun `addAttachment with byteProvider calls attachBytes`() {
    val sut = fixture.getSut()
    val bytes = byteArrayOf(4, 5, 6)

    sut.addAttachment(Attachment.fromByteProvider({ bytes }, "provided.bin", null, false))

    verify(fixture.nativeScope).attachBytes(eq(bytes), eq("provided.bin"))
  }

  @Test
  fun `setAttachments clears and re-adds`() {
    val sut = fixture.getSut()

    sut.setAttachments(
      listOf(
        Attachment("/data/files/a.txt", "a.txt", "text/plain"),
        Attachment(byteArrayOf(1), "b.bin"),
      )
    )

    verify(fixture.nativeScope).clearAttachments()
    verify(fixture.nativeScope).attachFile("/data/files/a.txt")
    verify(fixture.nativeScope).attachBytes(eq(byteArrayOf(1)), eq("b.bin"))
  }

  @Test
  fun `addAttachment does nothing when nativeScope does not support attachments`() {
    val plainScope = mock<INativeScope>()
    val sut = NdkScopeObserver(fixture.options, plainScope)

    // should not throw
    sut.addAttachment(Attachment("/data/files/log.txt"))
  }
}

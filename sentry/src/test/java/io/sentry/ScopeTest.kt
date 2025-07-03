package io.sentry

import io.sentry.SentryLevel.WARNING
import io.sentry.protocol.Request
import io.sentry.protocol.SentryId
import io.sentry.protocol.User
import io.sentry.test.callMethod
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Assert.assertArrayEquals
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class ScopeTest {
  @Test
  fun `copying scope wont have the same references`() {
    val scope = Scope(SentryOptions())
    val level = SentryLevel.DEBUG
    scope.level = level

    val user = User()
    user.email = "a@a.com"
    user.id = "123"
    user.ipAddress = "123.x"
    user.username = "userName"
    val data = mutableMapOf(Pair("data", "data"))
    user.data = data

    scope.user = user

    val request = Request()
    request.method = "post"
    request.cookies = "cookies"
    request.data = "cookies"
    request.envs = mapOf("env" to "value")
    request.headers = mapOf("header" to "value")
    request.others = mapOf("other" to "value")
    request.queryString = "?foo=bar"
    request.url = "http://localhost:8080/url"

    scope.request = request

    val fingerprints = mutableListOf("abc", "def")
    scope.fingerprint = fingerprints

    val breadcrumb = Breadcrumb()
    breadcrumb.message = "message"
    breadcrumb.setData("data", "data")

    breadcrumb.type = "type"
    breadcrumb.level = SentryLevel.DEBUG
    breadcrumb.category = "category"

    scope.addBreadcrumb(breadcrumb)
    scope.setTag("tag", "tag")
    scope.setExtra("extra", "extra")

    scope.screen = "MainActivity"

    val processor = CustomEventProcessor()
    scope.addEventProcessor(processor)

    scope.setContexts("key", "value")
    scope.addAttachment(Attachment("file name"))

    val clone = scope.clone()

    assertNotNull(clone)
    assertNotSame(scope, clone)
    assertNotSame(scope.user, clone.user)
    assertNotSame(scope.request, clone.request)
    assertNotSame(scope.contexts, clone.contexts)
    assertNotSame(scope.fingerprint, clone.fingerprint)
    assertNotSame(scope.breadcrumbs, clone.breadcrumbs)
    assertNotSame(scope.tags, clone.tags)
    assertNotSame(scope.extras, clone.extras)
    assertNotSame(scope.eventProcessors, clone.eventProcessors)
    assertNotSame(scope.attachments, clone.attachments)
  }

  @Test
  fun `copying scope will have the same values`() {
    val scope = Scope(SentryOptions())
    val level = SentryLevel.DEBUG
    scope.level = level

    val user = User()
    user.id = "123"
    scope.user = user

    val request = Request()
    request.method = "get"
    scope.request = request

    val fingerprints = mutableListOf("abc")
    scope.fingerprint = fingerprints

    val breadcrumb = Breadcrumb()
    breadcrumb.message = "message"

    scope.addBreadcrumb(breadcrumb)
    scope.setTag("tag", "tag")
    scope.setExtra("extra", "extra")

    val transaction =
      SentryTracer(TransactionContext("transaction-name", "op"), NoOpScopes.getInstance())
    scope.transaction = transaction

    val attachment = Attachment("path/log.txt")
    scope.addAttachment(attachment)

    scope.screen = "MainActivity"

    scope.setContexts("contexts", "contexts")

    val clone = scope.clone()

    assertEquals(SentryLevel.DEBUG, clone.level)

    assertEquals("123", clone.user?.id)

    assertEquals("get", clone.request?.method)

    assertEquals("abc", clone.fingerprint.first())

    assertEquals("message", clone.breadcrumbs.first().message)
    assertEquals("transaction-name", (clone.span as SentryTracer).name)

    assertEquals("tag", clone.tags["tag"])
    assertEquals("MainActivity", clone.screen)

    assertEquals("extra", clone.extras["extra"])
    assertEquals("contexts", (clone.contexts["contexts"] as HashMap<*, *>)["value"])
    assertEquals(transaction, clone.span)

    assertEquals(1, clone.attachments.size)
    val actual = clone.attachments.first()
    assertEquals(attachment.pathname, actual.pathname)
    assertArrayEquals(attachment.bytes ?: byteArrayOf(), actual.bytes ?: byteArrayOf())
    assertEquals(attachment.filename, actual.filename)
    assertEquals(attachment.contentType, actual.contentType)
  }

  @Test
  fun `copying scope and changing the original values wont change the clone values`() {
    val scope = Scope(SentryOptions())
    val level = SentryLevel.DEBUG
    scope.level = level

    val user = User()
    user.id = "123"
    scope.user = user

    val request = Request()
    request.method = "get"
    scope.request = request

    val fingerprints = mutableListOf("abc")
    scope.fingerprint = fingerprints

    val breadcrumb = Breadcrumb()
    breadcrumb.message = "message"

    scope.addBreadcrumb(breadcrumb)
    scope.setTag("tag", "tag")
    scope.setExtra("extra", "extra")
    scope.screen = "MainActivity"

    val processor = CustomEventProcessor()
    scope.addEventProcessor(processor)

    val attachment = Attachment("path/log.txt")
    scope.addAttachment(attachment)

    val clone = scope.clone()

    scope.level = SentryLevel.FATAL
    user.id = "456"
    request.method = "post"

    scope.setTransaction(
      SentryTracer(TransactionContext("newTransaction", "op"), NoOpScopes.getInstance())
    )

    // because you can only set a new list to scope
    val newFingerprints = mutableListOf("def", "ghf")
    scope.fingerprint = newFingerprints

    breadcrumb.message = "newMessage"
    scope.addBreadcrumb(Breadcrumb())
    scope.setTag("tag", "newTag")
    scope.setTag("otherTag", "otherTag")
    scope.setExtra("extra", "newExtra")
    scope.setExtra("otherExtra", "otherExtra")
    scope.screen = "LoginActivity"

    scope.addEventProcessor(processor)

    assertEquals(SentryLevel.DEBUG, clone.level)

    assertEquals("123", clone.user?.id)

    assertEquals("get", clone.request?.method)

    assertEquals("abc", clone.fingerprint.first())
    assertEquals(1, clone.fingerprint.size)

    assertEquals(1, clone.breadcrumbs.size)
    assertEquals("message", clone.breadcrumbs.first().message)

    assertEquals("tag", clone.tags["tag"])
    assertEquals(1, clone.tags.size)
    assertEquals("extra", clone.extras["extra"])
    assertEquals("MainActivity", clone.screen)
    assertEquals(1, clone.extras.size)
    assertEquals(1, clone.eventProcessors.size)
    assertNull(clone.span)

    scope.addAttachment(Attachment("path/image.png"))

    assertEquals(1, clone.attachments.size)
    assertTrue(clone.attachments is CopyOnWriteArrayList)
  }

  @Test
  fun `copying scope won't crash if there are concurrent operations`() {
    val options = SentryOptions().apply { maxBreadcrumbs = 10000 }
    val scope = Scope(options)
    for (i in 0 until options.maxBreadcrumbs) {
      scope.addBreadcrumb(Breadcrumb.info("item"))
    }

    // remove one breadcrumb after the other on an extra thread
    Thread(
        {
          while (scope.breadcrumbs.isNotEmpty()) {
            scope.breadcrumbs.remove()
          }
        },
        "thread-breadcrumb-remover",
      )
      .start()

    // clone in the meantime
    while (scope.breadcrumbs.isNotEmpty()) {
      scope.clone()
    }

    // expect no exception to be thrown ¯\_(ツ)_/¯
  }

  @Test
  fun `clear scope resets scope to default state`() {
    val scope = Scope(SentryOptions())
    scope.level = SentryLevel.WARNING
    scope.setTransaction(SentryTracer(TransactionContext("", "op"), NoOpScopes.getInstance()))
    scope.user = User()
    scope.request = Request()
    scope.fingerprint = mutableListOf("finger")
    scope.addBreadcrumb(Breadcrumb())
    scope.setTag("some", "tag")
    scope.screen = "MainActivity"
    scope.setExtra("some", "extra")
    scope.addEventProcessor(eventProcessor())
    scope.addAttachment(Attachment("path"))

    scope.clear()

    assertNull(scope.level)
    assertNull(scope.transaction)
    assertNull(scope.user)
    assertNull(scope.request)
    assertNull(scope.screen)
    assertEquals(0, scope.fingerprint.size)
    assertEquals(0, scope.breadcrumbs.size)
    assertEquals(0, scope.tags.size)
    assertEquals(0, scope.extras.size)
    assertEquals(0, scope.eventProcessors.size)
    assertEquals(0, scope.attachments.size)
  }

  @Test
  fun `when adding breadcrumb, executeBreadcrumb will be executed and breadcrumb will be added`() {
    val options = SentryOptions().apply { setBeforeBreadcrumb { breadcrumb, _ -> breadcrumb } }

    val scope = Scope(options)
    scope.addBreadcrumb(Breadcrumb())
    assertEquals(1, scope.breadcrumbs.count())
  }

  @Test
  fun `when adding breadcrumb, executeBreadcrumb will be executed and breadcrumb will be discarded`() {
    val options = SentryOptions().apply { setBeforeBreadcrumb { _, _ -> null } }

    val scope = Scope(options)
    scope.addBreadcrumb(Breadcrumb())
    assertEquals(0, scope.breadcrumbs.count())
  }

  @Test
  fun `when adding breadcrumb, executeBreadcrumb will be executed and throw, but breadcrumb will be added`() {
    val exception = Exception("test")

    val options = SentryOptions().apply { setBeforeBreadcrumb { _, _ -> throw exception } }

    val scope = Scope(options)
    val actual = Breadcrumb()
    scope.addBreadcrumb(actual)

    assertEquals("test", actual.data["sentry:message"])
  }

  @Test
  fun `when adding breadcrumb, executeBreadcrumb wont be executed as its not set, but it will be added`() {
    val options = SentryOptions()

    val scope = Scope(options)
    scope.addBreadcrumb(Breadcrumb())
    assertEquals(1, scope.breadcrumbs.count())
  }

  @Test
  fun `when adding breadcrumb and maxBreadcrumb is 0, beforeBreadcrumb is not executed`() {
    var called = false
    val options =
      SentryOptions().apply {
        maxBreadcrumbs = 0
        beforeBreadcrumb =
          SentryOptions.BeforeBreadcrumbCallback { breadcrumb, _ ->
            called = true
            breadcrumb
          }
      }

    val scope = Scope(options)
    scope.addBreadcrumb(Breadcrumb())
    assertEquals(0, scope.breadcrumbs.count())
    assertFalse(called)
  }

  @Test
  fun `when adding breadcrumb and maxBreadcrumb is not 0, beforeBreadcrumb is executed`() {
    var called = false
    val options =
      SentryOptions().apply {
        beforeBreadcrumb =
          SentryOptions.BeforeBreadcrumbCallback { breadcrumb, _ ->
            called = true
            breadcrumb
          }
      }

    val scope = Scope(options)
    scope.addBreadcrumb(Breadcrumb())
    assertEquals(1, scope.breadcrumbs.count())
    assertTrue(called)
  }

  @Test
  fun `when adding breadcrumb and maxBreadcrumb is 0, scopesObservers are not called`() {
    val observer = mock<IScopeObserver>()
    val options =
      SentryOptions().apply {
        maxBreadcrumbs = 0
        addScopeObserver(observer)
      }

    val scope = Scope(options)
    scope.addBreadcrumb(Breadcrumb())
    assertEquals(0, scope.breadcrumbs.count())
    verifyNoInteractions(observer)
  }

  @Test
  fun `when adding breadcrumb and maxBreadcrumb is not 0, scopesObservers are called`() {
    val observer = mock<IScopeObserver>()
    val options = SentryOptions().apply { addScopeObserver(observer) }

    val scope = Scope(options)
    scope.addBreadcrumb(Breadcrumb())
    assertEquals(1, scope.breadcrumbs.count())
    verify(observer).addBreadcrumb(any())
  }

  @Test
  fun `when adding eventProcessor, eventProcessor should be in the list`() {
    val processor = CustomEventProcessor()
    val scope = Scope(SentryOptions())
    scope.addEventProcessor(processor)
    assertEquals(processor, scope.eventProcessors.first())
  }

  @Test
  fun `Scope starts a new session with release, env and user`() {
    val options = SentryOptions().apply { distinctId = "123" }
    options.release = "rel"
    options.environment = "env"
    val user = User()

    val scope = Scope(options)
    scope.user = user

    val sessionPair = scope.startSession()
    assertNotNull(sessionPair) {
      assertNotNull(it.current)
      assertEquals("rel", it.current.release)
      assertEquals("env", it.current.environment)
      assertEquals("123", it.current.distinctId)
    }
  }

  @Test
  fun `Scope ends a session and returns it if theres one`() {
    val options = SentryOptions().apply { release = "0.0.1" }

    val scope = Scope(options)

    scope.startSession()
    val session = scope.endSession()
    assertNotNull(session)
  }

  @Test
  fun `Scope ends a session and returns null if none exist`() {
    val options = SentryOptions().apply { release = "0.0.1" }
    val scope = Scope(options)

    val session = scope.endSession()
    assertNull(session)
  }

  @Test
  fun `Starting a session multiple times reevaluates profileSessionSampleRate`() {
    val profiler = mock<IContinuousProfiler>()
    val options =
      SentryOptions().apply {
        release = "0.0.1"
        setContinuousProfiler(profiler)
        profileSessionSampleRate = 1.0
      }

    val scope = Scope(options)
    // The first time a session is started, sample rate is not reevaluated, as there's no need
    scope.startSession()
    verify(profiler, never()).reevaluateSampling()
    // The second time a session is started, sample rate is reevaluated
    scope.startSession()
    verify(profiler).reevaluateSampling()
    // Every time a session is started with an already running one, sample rate is reevaluated
    scope.startSession()
    verify(profiler, times(2)).reevaluateSampling()
  }

  @Test
  fun `Scope ends a session and reevaluates profileSessionSampleRate`() {
    val profiler = mock<IContinuousProfiler>()
    val options =
      SentryOptions().apply {
        release = "0.0.1"
        setContinuousProfiler(profiler)
        profileSessionSampleRate = 1.0
      }

    val scope = Scope(options)
    scope.startSession()
    verify(profiler, never()).reevaluateSampling()
    scope.endSession()
    verify(profiler).reevaluateSampling()
  }

  @Test
  fun `Scope ends a session and does not reevaluate profileSessionSampleRate if none exist`() {
    val profiler = mock<IContinuousProfiler>()
    val options =
      SentryOptions().apply {
        release = "0.0.1"
        setContinuousProfiler(profiler)
        profileSessionSampleRate = 1.0
      }

    val scope = Scope(options)
    scope.endSession()
    verify(profiler, never()).reevaluateSampling()
  }

  @Test
  fun `withSession returns a callback with the current Session`() {
    val options = SentryOptions().apply { release = "0.0.1" }
    val scope = Scope(options)

    scope.startSession()
    scope.withSession { assertNotNull(it) }
  }

  @Test
  fun `withSession returns a callback with a null session if theres none`() {
    val options = SentryOptions().apply { release = "0.0.1" }
    val scope = Scope(options)

    scope.withSession { assertNull(it) }
  }

  @Test
  fun `Scope clones the start and end session objects`() {
    val options = SentryOptions().apply { release = "0.0.1" }
    val scope = Scope(options)

    val sessionPair = scope.startSession()
    assertNotNull(sessionPair) {
      val endSession = scope.endSession()!!

      assertNotSame(it.current, endSession)
    }
  }

  @Test
  fun `when release is not set, startSession returns null`() {
    val options = SentryOptions()
    val scope = Scope(options)
    assertNull(scope.startSession())
  }

  @Test
  fun `Scope sets init to null when mutating a session`() {
    val options = SentryOptions().apply { release = "0.0.1" }
    val scope = Scope(options)

    val sessionPair = scope.startSession()
    assertNotNull(sessionPair) {
      val start = it.current

      scope.withSession { session -> session!!.update(null, null, false) }

      val end = scope.endSession()!!

      assertTrue(start.init!!)
      assertNull(end.init)
    }
  }

  @Test
  fun `Scope increases session error count when capturing an error`() {
    val options = SentryOptions().apply { release = "0.0.1" }
    val scope = Scope(options)

    val sessionPair = scope.startSession()
    assertNotNull(sessionPair) {
      val start = it.current

      scope.withSession { session -> session!!.update(null, null, true) }

      val end = scope.endSession()!!

      assertEquals(0, start.errorCount())
      assertEquals(1, end.errorCount())
    }
  }

  @Test
  fun `Scope sets status when capturing a fatal error`() {
    val options = SentryOptions().apply { release = "0.0.1" }
    val scope = Scope(options)

    val sessionPair = scope.startSession()
    assertNotNull(sessionPair) {
      val start = it.current
      scope.withSession { session -> session!!.update(Session.State.Crashed, null, true) }

      val end = scope.endSession()!!

      assertEquals(Session.State.Ok, start.status)
      assertEquals(Session.State.Crashed, end.status)
    }
  }

  @Test
  fun `Scope sets user agent when capturing an error`() {
    val options = SentryOptions().apply { release = "0.0.1" }
    val scope = Scope(options)

    val sessionPair = scope.startSession()
    assertNotNull(sessionPair) {
      val start = it.current
      scope.withSession { session -> session!!.update(null, "jamesBond", true) }

      val end = scope.endSession()!!

      assertNull(start.userAgent)
      assertEquals("jamesBond", end.userAgent)
    }
  }

  @Test
  fun `Scope sets timestamp when capturing an error`() {
    val options = SentryOptions().apply { release = "0.0.1" }
    val scope = Scope(options)

    val sessionPair = scope.startSession()
    assertNotNull(sessionPair) {
      val start = it.current
      scope.withSession { session -> session!!.update(null, null, true) }
      val end = scope.endSession()!!

      assertNotSame(end.timestamp!!, start.timestamp)
    }
  }

  @Test
  fun `Scope increases sequence when capturing an error`() {
    val options = SentryOptions().apply { release = "0.0.1" }
    val scope = Scope(options)

    val sessionPair = scope.startSession()
    assertNotNull(sessionPair) {
      val start = it.current
      scope.withSession { session -> session!!.update(null, null, true) }

      val end = scope.endSession()!!

      assertNull(start.sequence)
      assertTrue(end.sequence!! > 0)
    }
  }

  @Test
  fun `Scope sets duration when ending a session`() {
    val options = SentryOptions().apply { release = "0.0.1" }
    val scope = Scope(options)

    val sessionPair = scope.startSession()
    assertNotNull(sessionPair) {
      val start = it.current
      scope.withSession { session -> session!!.update(null, null, true) }

      val end = scope.endSession()!!

      assertNull(start.duration)
      assertNotNull(end.duration)
    }
  }

  @Test
  fun `Scope set user sync scopes`() {
    val observer = mock<IScopeObserver>()
    val options = SentryOptions().apply { addScopeObserver(observer) }
    val scope = Scope(options)

    val user = User()
    scope.user = user
    verify(observer).setUser(eq(user))
  }

  @Test
  fun `Scope add breadcrumb sync scopes`() {
    val observer = mock<IScopeObserver>()
    val options = SentryOptions().apply { addScopeObserver(observer) }
    val scope = Scope(options)

    val breadcrumb = Breadcrumb()
    scope.addBreadcrumb(breadcrumb)
    verify(observer).addBreadcrumb(eq(breadcrumb))
    verify(observer).setBreadcrumbs(argThat { elementAt(0) == breadcrumb })

    scope.addBreadcrumb(Breadcrumb.debug("test"))
    verify(observer).addBreadcrumb(argThat { message == "test" })
    verify(observer, times(2))
      .setBreadcrumbs(argThat { elementAt(0) == breadcrumb && elementAt(1).message == "test" })
  }

  @Test
  fun `Scope clear breadcrumbs sync scopes`() {
    val observer = mock<IScopeObserver>()
    val options = SentryOptions().apply { addScopeObserver(observer) }
    val scope = Scope(options)

    val breadcrumb = Breadcrumb()
    scope.addBreadcrumb(breadcrumb)
    assertFalse(scope.breadcrumbs.isEmpty())

    scope.clearBreadcrumbs()
    verify(observer, times(2)).setBreadcrumbs(argThat { isEmpty() })
  }

  @Test
  fun `Scope set tag sync scopes`() {
    val observer = mock<IScopeObserver>()
    val options = SentryOptions().apply { addScopeObserver(observer) }
    val scope = Scope(options)

    scope.setTag("a", "b")
    verify(observer).setTag(eq("a"), eq("b"))
    verify(observer).setTags(argThat { get("a") == "b" })

    scope.setTag("one", "two")
    verify(observer).setTag(eq("one"), eq("two"))
    verify(observer, times(2)).setTags(argThat { get("a") == "b" && get("one") == "two" })
  }

  @Test
  fun `Scope remove tag sync scopes`() {
    val observer = mock<IScopeObserver>()
    val options = SentryOptions().apply { addScopeObserver(observer) }
    val scope = Scope(options)

    scope.setTag("a", "b")
    assertFalse(scope.tags.isEmpty())

    scope.removeTag("a")
    verify(observer).removeTag(eq("a"))
    verify(observer, times(2)).setTags(emptyMap())
  }

  @Test
  fun `Scope set extra sync scopes`() {
    val observer = mock<IScopeObserver>()
    val options = SentryOptions().apply { addScopeObserver(observer) }
    val scope = Scope(options)

    scope.setExtra("a", "b")
    verify(observer).setExtra(eq("a"), eq("b"))
    verify(observer).setExtras(argThat { get("a") == "b" })

    scope.setExtra("one", "two")
    verify(observer).setExtra(eq("one"), eq("two"))
    verify(observer, times(2)).setExtras(argThat { get("a") == "b" && get("one") == "two" })
  }

  @Test
  fun `Scope remove extra sync scopes`() {
    val observer = mock<IScopeObserver>()
    val options = SentryOptions().apply { addScopeObserver(observer) }
    val scope = Scope(options)

    scope.setExtra("a", "b")
    assertFalse(scope.extras.isEmpty())

    scope.removeExtra("a")
    verify(observer).removeExtra(eq("a"))
    verify(observer, times(2)).setExtras(emptyMap())
  }

  @Test
  fun `Scope set level sync scopes`() {
    val observer = mock<IScopeObserver>()
    val options = SentryOptions().apply { addScopeObserver(observer) }
    val scope = Scope(options)

    scope.level = WARNING
    verify(observer).setLevel(eq(WARNING))
  }

  @Test
  fun `Scope set transaction name sync scopes`() {
    val observer = mock<IScopeObserver>()
    val options = SentryOptions().apply { addScopeObserver(observer) }
    val scope = Scope(options)

    scope.setTransaction("main")
    verify(observer).setTransaction(eq("main"))
  }

  @Test
  fun `Scope set transaction sync scopes`() {
    val observer = mock<IScopeObserver>()
    val options = SentryOptions().apply { addScopeObserver(observer) }
    val scope = Scope(options)

    scope.transaction = mock {
      whenever(mock.name).thenReturn("main")
      whenever(mock.spanContext).thenReturn(SpanContext("ui.load"))
    }
    verify(observer).setTransaction(eq("main"))
    verify(observer).setTrace(argThat { operation == "ui.load" }, eq(scope))
  }

  @Test
  fun `Scope set transaction null sync scopes`() {
    val observer = mock<IScopeObserver>()
    val options = SentryOptions().apply { addScopeObserver(observer) }
    val scope = Scope(options)

    scope.transaction = null
    verify(observer).setTransaction(null)
    verify(observer).setTrace(null, scope)
  }

  @Test
  fun `Scope clear transaction sync scopes`() {
    val observer = mock<IScopeObserver>()
    val options = SentryOptions().apply { addScopeObserver(observer) }
    val scope = Scope(options)

    scope.transaction = mock {
      whenever(mock.name).thenReturn("main")
      whenever(mock.spanContext).thenReturn(SpanContext("ui.load"))
    }
    verify(observer).setTransaction(eq("main"))
    verify(observer).setTrace(argThat { operation == "ui.load" }, eq(scope))

    scope.clearTransaction()
    verify(observer).setTransaction(null)
    verify(observer).setTrace(null, scope)
  }

  @Test
  fun `Scope set request sync scopes`() {
    val observer = mock<IScopeObserver>()
    val options = SentryOptions().apply { addScopeObserver(observer) }
    val scope = Scope(options)

    scope.request = Request().apply { url = "https://google.com" }
    verify(observer).setRequest(argThat { url == "https://google.com" })
  }

  @Test
  fun `Scope set fingerprint sync scopes`() {
    val observer = mock<IScopeObserver>()
    val options = SentryOptions().apply { addScopeObserver(observer) }
    val scope = Scope(options)

    scope.fingerprint = listOf("finger", "print")
    verify(observer).setFingerprint(argThat { elementAt(0) == "finger" && elementAt(1) == "print" })
  }

  @Test
  fun `Scope set contexts sync scopes`() {
    val observer = mock<IScopeObserver>()
    val options = SentryOptions().apply { addScopeObserver(observer) }
    val scope = Scope(options)

    data class Obj(val stuff: Int)
    scope.setContexts("test", Obj(3))
    verify(observer).setContexts(argThat { (get("test") as Obj).stuff == 3 })
  }

  @Test
  fun `Scope set propagation context sync scopes`() {
    val observer = mock<IScopeObserver>()
    val options = SentryOptions().apply { addScopeObserver(observer) }
    val scope = Scope(options)

    scope.propagationContext =
      PropagationContext(SentryId("64cf554cc8d74c6eafa3e08b7c984f6d"), SpanId(), null, null, null)
    verify(observer)
      .setTrace(argThat { traceId.toString() == "64cf554cc8d74c6eafa3e08b7c984f6d" }, eq(scope))
  }

  @Test
  fun `Scope getTransaction returns the transaction if there is no active span`() {
    val scope = Scope(SentryOptions())
    val transaction = SentryTracer(TransactionContext("name", "op"), NoOpScopes.getInstance())
    scope.transaction = transaction
    assertEquals(transaction, scope.span)
  }

  @Test
  fun `Scope getTransaction returns the current span if there is an unfinished span`() {
    val scope = Scope(SentryOptions())
    val transaction = SentryTracer(TransactionContext("name", "op"), NoOpScopes.getInstance())
    scope.transaction = transaction
    val span = transaction.startChild("op")
    assertEquals(span, scope.span)
  }

  @Test
  fun `Scope getTransaction returns the current span if there is a finished span`() {
    val scope = Scope(SentryOptions())
    val transaction = SentryTracer(TransactionContext("name", "op"), NoOpScopes.getInstance())
    scope.transaction = transaction
    val span = transaction.startChild("op")
    span.finish()
    assertEquals(transaction, scope.span)
  }

  @Test
  fun `Scope getTransaction returns the latest span if there is a list of active span`() {
    val scope = Scope(SentryOptions())
    val transaction = SentryTracer(TransactionContext("name", "op"), NoOpScopes.getInstance())
    scope.transaction = transaction
    val span = transaction.startChild("op")
    val innerSpan = span.startChild("op")
    assertEquals(innerSpan, scope.span)
  }

  @Test
  fun `Scope setTransaction sets transaction name`() {
    val scope = Scope(SentryOptions())
    val transaction = SentryTracer(TransactionContext("name", "op"), NoOpScopes.getInstance())
    scope.transaction = transaction
    scope.setTransaction("new-name")
    assertNotNull(scope.transaction) { assertEquals("new-name", it.name) }
    assertEquals("new-name", scope.transactionName)
  }

  @Test
  fun `Scope setTransaction with null does not clear transaction`() {
    val scope = Scope(SentryOptions())
    val transaction = SentryTracer(TransactionContext("name", "op"), NoOpScopes.getInstance())
    scope.transaction = transaction
    scope.callMethod("setTransaction", String::class.java, null)
    assertNotNull(scope.transaction)
    assertNotNull(scope.transactionName)
  }

  @Test
  fun `attachments are thread safe`() {
    val scope = Scope(SentryOptions())
    assertTrue(scope.attachments is CopyOnWriteArrayList)

    scope.clear()
    assertTrue(scope.attachments is CopyOnWriteArrayList)

    val cloned = scope.clone()
    assertTrue(cloned.attachments is CopyOnWriteArrayList)
  }

  @Test
  fun `getAttachments returns new instance`() {
    val scope = Scope(SentryOptions())
    scope.addAttachment(Attachment(""))

    assertNotSame(
      scope.attachments,
      scope.attachments,
      "Scope.attachments must return a new instance on each call.",
    )
  }

  @Test
  fun `clearAttachments clears all attachments`() {
    val scope = Scope(SentryOptions())
    scope.addAttachment(Attachment(""))
    scope.addAttachment(Attachment(""))

    assertEquals(2, scope.attachments.count())
    scope.clearAttachments()
    assertEquals(0, scope.attachments.count())
  }

  @Test
  fun `setting null fingerprint do not overwrite current value`() {
    val scope = Scope(SentryOptions())
    // sanity check
    assertNotNull(scope.fingerprint)

    scope.callMethod("setFingerprint", List::class.java, null)

    assertNotNull(scope.fingerprint)
  }

  @Test
  fun `when transaction is not started, sets transaction name on the field`() {
    val scope = Scope(SentryOptions())
    scope.setTransaction("transaction-name")
    assertEquals("transaction-name", scope.transactionName)
    assertNull(scope.transaction)
  }

  @Test
  fun `when transaction is started, sets transaction name on the transaction object`() {
    val scope = Scope(SentryOptions())
    val sentryTransaction =
      SentryTracer(TransactionContext("transaction-name", "op"), NoOpScopes.getInstance())
    scope.transaction = sentryTransaction
    assertEquals("transaction-name", scope.transactionName)
    scope.setTransaction("new-name")
    assertEquals("new-name", scope.transactionName)
    sentryTransaction.name = "another-name"
    assertEquals("another-name", scope.transactionName)
  }

  @Test
  fun `when transaction is set after transaction name is set, clearing transaction does not bring back old transaction name`() {
    val scope = Scope(SentryOptions())
    scope.setTransaction("transaction-a")
    val sentryTransaction =
      SentryTracer(TransactionContext("transaction-name", "op"), NoOpScopes.getInstance())
    scope.setTransaction(sentryTransaction)
    assertEquals("transaction-name", scope.transactionName)
    scope.clearTransaction()
    assertNull(scope.transactionName)
  }

  @Test
  fun `withTransaction returns the current Transaction bound to the Scope`() {
    val scope = Scope(SentryOptions())
    val sentryTransaction =
      SentryTracer(TransactionContext("transaction-name", "op"), NoOpScopes.getInstance())
    scope.setTransaction(sentryTransaction)

    scope.withTransaction { assertEquals(sentryTransaction, it) }
  }

  @Test
  fun `withTransaction returns null if no transaction bound to the Scope`() {
    val scope = Scope(SentryOptions())

    scope.withTransaction { assertNull(it) }
  }

  @Test
  fun `when setFingerprints receives immutable list as an argument, its still possible to add more fingerprints`() {
    val scope =
      Scope(SentryOptions()).apply {
        fingerprint = listOf("a", "b")
        fingerprint.add("c")
      }
    assertNotNull(scope.fingerprint) { assertEquals(listOf("a", "b", "c"), it) }
  }

  @Test
  fun `when setting the screen, it's stored in the app context as well`() {
    val scope = Scope(SentryOptions()).apply { screen = "MainActivity" }
    assertEquals(listOf("MainActivity"), scope.contexts.app!!.viewNames)
  }

  @Test
  fun `when setting the screen, app context change is propagated`() {
    val options = SentryOptions()
    val observer = mock<IScopeObserver>()
    options.addScopeObserver(observer)

    Scope(options).apply { screen = "MainActivity" }
    verify(observer)
      .setContexts(
        check { contexts -> assertEquals("MainActivity", contexts.app?.viewNames?.first()) }
      )
  }

  @Test
  fun `creating a new scope won't crash if max breadcrumbs is set to zero`() {
    val options = SentryOptions().apply { maxBreadcrumbs = 0 }
    val scope = Scope(options)

    // expect no exception to be thrown
    // previously was crashing, see https://github.com/getsentry/sentry-java/issues/3313
  }

  @Test
  fun `null tags do not cause NPE`() {
    val scope = Scope(SentryOptions.empty())
    scope.setTag("k", "oldvalue")
    scope.setTag(null, null)
    scope.setTag("k", null)
    scope.setTag(null, "v")
    scope.removeTag(null)
    assertTrue(scope.tags.isEmpty())
  }

  @Test
  fun `null extras do not cause NPE`() {
    val scope = Scope(SentryOptions.empty())
    scope.setExtra("k", "oldvalue")
    scope.setExtra(null, null)
    scope.setExtra("k", null)
    scope.setExtra(null, "v")
    scope.removeExtra(null)
    assertTrue(scope.extras.isEmpty())
  }

  @Test
  fun `null contexts do not cause NPE`() {
    val scope = Scope(SentryOptions.empty())

    scope.setContexts("obj", null as Any?)
    scope.setContexts("bool", true)
    scope.setContexts("string", "hello")
    scope.setContexts("num", 100)
    scope.setContexts("list", listOf("a", "b"))
    scope.setContexts("array", arrayOf("c", "d"))
    scope.setContexts("char", 'z')

    assertFalse(scope.contexts.isEmpty)

    scope.setContexts(null, null as Any?)
    scope.setContexts(null, null as Boolean?)
    scope.setContexts(null, null as String?)
    scope.setContexts(null, null as Number?)
    scope.setContexts(null, null as List<Any>?)
    scope.setContexts(null, null as Array<Any>?)
    scope.setContexts(null, null as Character?)

    scope.setContexts("obj", null as Any?)
    scope.setContexts("bool", null as Boolean?)
    scope.setContexts("string", null as String?)
    scope.setContexts("num", null as Number?)
    scope.setContexts("list", null as List<Any>?)
    scope.setContexts("array", null as Array<Any>?)
    scope.setContexts("char", null as Character?)

    scope.removeContexts(null)

    assertTrue(scope.contexts.isEmpty)
  }

  private fun eventProcessor(): EventProcessor =
    object : EventProcessor {
      override fun process(event: SentryEvent, hint: Hint): SentryEvent? = event
    }
}

package io.sentry

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import io.sentry.protocol.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScopeTest {
    @Test
    fun `cloning scope wont have the same references`() {
        val scope = Scope(SentryOptions())
        val level = SentryLevel.DEBUG
        scope.level = level

        val user = User()
        user.email = "a@a.com"
        user.id = "123"
        user.ipAddress = "123.x"
        user.username = "userName"
        val others = mutableMapOf(Pair("others", "others"))
        user.others = others

        scope.user = user

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

        val processor = CustomEventProcessor()
        scope.addEventProcessor(processor)

        val clone = scope.clone()

        assertNotNull(clone)
        assertNotSame(scope, clone)
        assertNotSame(scope.user, clone.user)
        assertNotSame(scope.contexts, clone.contexts)
        assertNotSame(scope.fingerprint, clone.fingerprint)
        assertNotSame(scope.breadcrumbs, clone.breadcrumbs)
        assertNotSame(scope.tags, clone.tags)
        assertNotSame(scope.extras, clone.extras)
        assertNotSame(scope.eventProcessors, clone.eventProcessors)
    }

    @Test
    fun `cloning scope will have the same values`() {
        val scope = Scope(SentryOptions())
        val level = SentryLevel.DEBUG
        scope.level = level

        val user = User()
        user.id = "123"

        scope.user = user

        val fingerprints = mutableListOf("abc")
        scope.fingerprint = fingerprints

        val breadcrumb = Breadcrumb()
        breadcrumb.message = "message"

        scope.addBreadcrumb(breadcrumb)
        scope.setTag("tag", "tag")
        scope.setExtra("extra", "extra")

        val transaction = SentryTransaction("transaction-name")
        scope.setTransaction(transaction)

        val clone = scope.clone()

        assertEquals(SentryLevel.DEBUG, clone.level)

        assertEquals("123", clone.user?.id)

        assertEquals("abc", clone.fingerprint.first())

        assertEquals("message", clone.breadcrumbs.first().message)
        assertEquals("transaction-name", (clone.span as SentryTransaction).transaction)

        assertEquals("tag", clone.tags["tag"])
        assertEquals("extra", clone.extras["extra"])
        assertEquals(transaction, clone.span)
    }

    @Test
    fun `cloning scope and changing the original values wont change the clone values`() {
        val scope = Scope(SentryOptions())
        val level = SentryLevel.DEBUG
        scope.level = level

        val user = User()
        user.id = "123"

        scope.user = user

        val fingerprints = mutableListOf("abc")
        scope.fingerprint = fingerprints

        val breadcrumb = Breadcrumb()
        breadcrumb.message = "message"

        scope.addBreadcrumb(breadcrumb)
        scope.setTag("tag", "tag")
        scope.setExtra("extra", "extra")

        val processor = CustomEventProcessor()
        scope.addEventProcessor(processor)

        val clone = scope.clone()

        scope.level = SentryLevel.FATAL
        user.id = "456"

        scope.setTransaction(SentryTransaction("newTransaction"))

        // because you can only set a new list to scope
        val newFingerprints = mutableListOf("def", "ghf")
        scope.fingerprint = newFingerprints

        breadcrumb.message = "newMessage"
        scope.addBreadcrumb(Breadcrumb())
        scope.setTag("tag", "newTag")
        scope.setTag("otherTag", "otherTag")
        scope.setExtra("extra", "newExtra")
        scope.setExtra("otherExtra", "otherExtra")

        scope.addEventProcessor(processor)

        assertEquals(SentryLevel.DEBUG, clone.level)

        assertEquals("123", clone.user?.id)

        assertEquals("abc", clone.fingerprint.first())
        assertEquals(1, clone.fingerprint.size)

        assertEquals(1, clone.breadcrumbs.size)
        assertEquals("message", clone.breadcrumbs.first().message)

        assertEquals("tag", clone.tags["tag"])
        assertEquals(1, clone.tags.size)
        assertEquals("extra", clone.extras["extra"])
        assertEquals(1, clone.extras.size)
        assertEquals(1, clone.eventProcessors.size)
        assertNull(clone.span)
    }

    @Test
    fun `when adding breadcrumb, executeBreadcrumb will be executed and breadcrumb will be added`() {
        val options = SentryOptions().apply {
            setBeforeBreadcrumb { breadcrumb, _ -> breadcrumb }
        }

        val scope = Scope(options)
        scope.addBreadcrumb(Breadcrumb())
        assertEquals(1, scope.breadcrumbs.count())
    }

    @Test
    fun `when adding breadcrumb, executeBreadcrumb will be executed and breadcrumb will be discarded`() {
        val options = SentryOptions().apply {
            setBeforeBreadcrumb { _, _ -> null }
        }

        val scope = Scope(options)
        scope.addBreadcrumb(Breadcrumb())
        assertEquals(0, scope.breadcrumbs.count())
    }

    @Test
    fun `when adding breadcrumb, executeBreadcrumb will be executed and throw, but breadcrumb will be added`() {
        val exception = Exception("test")

        val options = SentryOptions().apply {
            setBeforeBreadcrumb { _, _ -> throw exception }
        }

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
    fun `when adding eventProcessor, eventProcessor should be in the list`() {
        val processor = CustomEventProcessor()
        val scope = Scope(SentryOptions())
        scope.addEventProcessor(processor)
        assertEquals(processor, scope.eventProcessors.first())
    }

    @Test
    fun `Scope starts a new session with release, env and user`() {
        val options = SentryOptions().apply {
            distinctId = "123"
        }
        options.release = "rel"
        options.environment = "env"
        val user = User()

        val scope = Scope(options)
        scope.user = user

        val sessionPair = scope.startSession()
        assertNotNull(sessionPair.current)
        assertEquals("rel", sessionPair.current.release)
        assertEquals("env", sessionPair.current.environment)
        assertEquals("123", sessionPair.current.distinctId)
    }

    @Test
    fun `Scope ends a session and returns it if theres one`() {
        val options = SentryOptions()

        val scope = Scope(options)

        scope.startSession()
        val session = scope.endSession()
        assertNotNull(session)
    }

    @Test
    fun `Scope ends a session and returns null if none exist`() {
        val options = SentryOptions()
        val scope = Scope(options)

        val session = scope.endSession()
        assertNull(session)
    }

    @Test
    fun `withSession returns a callback with the current Session`() {
        val options = SentryOptions()
        val scope = Scope(options)

        scope.startSession()
        scope.withSession {
            assertNotNull(it)
        }
    }

    @Test
    fun `withSession returns a callback with a null session if theres none`() {
        val options = SentryOptions()
        val scope = Scope(options)

        scope.withSession {
            assertNull(it)
        }
    }

    @Test
    fun `Scope clones the start and end session objects`() {
        val options = SentryOptions()
        val scope = Scope(options)

        val sessionPair = scope.startSession()
        val endSession = scope.endSession()!!

        assertNotSame(sessionPair.current, endSession)
    }

    @Test
    fun `Scope sets init to null when mutating a session`() {
        val options = SentryOptions()
        val scope = Scope(options)

        val start = scope.startSession().current

        scope.withSession {
            it!!.update(null, null, false)
        }

        val end = scope.endSession()!!

        assertTrue(start.init!!)
        assertNull(end.init)
    }

    @Test
    fun `Scope increases session error count when capturing an error`() {
        val options = SentryOptions()
        val scope = Scope(options)

        val start = scope.startSession().current

        scope.withSession {
            it!!.update(null, null, true)
        }

        val end = scope.endSession()!!

        assertEquals(0, start.errorCount())
        assertEquals(1, end.errorCount())
    }

    @Test
    fun `Scope sets status when capturing a fatal error`() {
        val options = SentryOptions()
        val scope = Scope(options)

        val start = scope.startSession().current

        scope.withSession {
            it!!.update(Session.State.Crashed, null, true)
        }

        val end = scope.endSession()!!

        assertEquals(Session.State.Ok, start.status)
        assertEquals(Session.State.Crashed, end.status)
    }

    @Test
    fun `Scope sets user agent when capturing an error`() {
        val options = SentryOptions()
        val scope = Scope(options)

        val start = scope.startSession().current

        scope.withSession {
            it!!.update(null, "jamesBond", true)
        }

        val end = scope.endSession()!!

        assertNull(start.userAgent)
        assertEquals("jamesBond", end.userAgent)
    }

    @Test
    fun `Scope sets timestamp when capturing an error`() {
        val options = SentryOptions()
        val scope = Scope(options)

        val start = scope.startSession().current

        scope.withSession {
            it!!.update(null, null, true)
        }
        val end = scope.endSession()!!

        assertNotSame(end.timestamp!!, start.timestamp)
    }

    @Test
    fun `Scope increases sequence when capturing an error`() {
        val options = SentryOptions()
        val scope = Scope(options)

        val start = scope.startSession().current

        scope.withSession {
            it!!.update(null, null, true)
        }

        val end = scope.endSession()!!

        assertNull(start.sequence)
        assertTrue(end.sequence!! > 0)
    }

    @Test
    fun `Scope sets duration when ending a session`() {
        val options = SentryOptions()
        val scope = Scope(options)

        val start = scope.startSession().current

        scope.withSession {
            it!!.update(null, null, true)
        }

        val end = scope.endSession()!!

        assertNull(start.duration)
        assertNotNull(end.duration)
    }

    @Test
    fun `Scope set user sync scopes if enabled`() {
        val observer = mock<IScopeObserver>()
        val options = SentryOptions().apply {
            isEnableScopeSync = true
            addScopeObserver(observer)
        }
        val scope = Scope(options)

        val user = User()
        scope.user = user
        verify(observer).setUser(eq(user))
    }

    @Test
    fun `Scope set user wont sync scopes if disabled`() {
        val observer = mock<IScopeObserver>()
        val options = SentryOptions().apply {
            addScopeObserver(observer)
        }
        val scope = Scope(options)

        scope.user = User()
        verify(observer, never()).setUser(any())
    }

    @Test
    fun `Scope add breadcrumb sync scopes if enabled`() {
        val observer = mock<IScopeObserver>()
        val options = SentryOptions().apply {
            isEnableScopeSync = true
            addScopeObserver(observer)
        }
        val scope = Scope(options)

        val breadrumb = Breadcrumb()
        scope.addBreadcrumb(breadrumb)
        verify(observer).addBreadcrumb(eq(breadrumb))
    }

    @Test
    fun `Scope add breadcrumb wont sync scopes if disabled`() {
        val observer = mock<IScopeObserver>()
        val options = SentryOptions().apply {
            addScopeObserver(observer)
        }
        val scope = Scope(options)

        scope.addBreadcrumb(Breadcrumb())
        verify(observer, never()).addBreadcrumb(any())
    }

    @Test
    fun `Scope set tag sync scopes if enabled`() {
        val observer = mock<IScopeObserver>()
        val options = SentryOptions().apply {
            isEnableScopeSync = true
            addScopeObserver(observer)
        }
        val scope = Scope(options)

        scope.setTag("a", "b")
        verify(observer).setTag(eq("a"), eq("b"))
    }

    @Test
    fun `Scope set tag wont sync scopes if disabled`() {
        val observer = mock<IScopeObserver>()
        val options = SentryOptions().apply {
            addScopeObserver(observer)
        }
        val scope = Scope(options)

        scope.setTag("a", "b")
        verify(observer, never()).setTag(any(), any())
    }

    @Test
    fun `Scope remove tag sync scopes if enabled`() {
        val observer = mock<IScopeObserver>()
        val options = SentryOptions().apply {
            isEnableScopeSync = true
            addScopeObserver(observer)
        }
        val scope = Scope(options)

        scope.removeTag("a")
        verify(observer).removeTag(eq("a"))
    }

    @Test
    fun `Scope remove tag wont sync scopes if disabled`() {
        val observer = mock<IScopeObserver>()
        val options = SentryOptions().apply {
            addScopeObserver(observer)
        }
        val scope = Scope(options)

        scope.removeTag("a")
        verify(observer, never()).removeTag(any())
    }

    @Test
    fun `Scope set extra sync scopes if enabled`() {
        val observer = mock<IScopeObserver>()
        val options = SentryOptions().apply {
            isEnableScopeSync = true
            addScopeObserver(observer)
        }
        val scope = Scope(options)

        scope.setExtra("a", "b")
        verify(observer).setExtra(eq("a"), eq("b"))
    }

    @Test
    fun `Scope set extra wont sync scopes if disabled`() {
        val observer = mock<IScopeObserver>()
        val options = SentryOptions().apply {
            addScopeObserver(observer)
        }
        val scope = Scope(options)

        scope.setExtra("a", "b")
        verify(observer, never()).setExtra(any(), any())
    }

    @Test
    fun `Scope remove extra sync scopes if enabled`() {
        val observer = mock<IScopeObserver>()
        val options = SentryOptions().apply {
            isEnableScopeSync = true
            addScopeObserver(observer)
        }
        val scope = Scope(options)

        scope.removeExtra("a")
        verify(observer).removeExtra(eq("a"))
    }

    @Test
    fun `Scope remove extra wont sync scopes if enabled`() {
        val observer = mock<IScopeObserver>()
        val options = SentryOptions().apply {
            addScopeObserver(observer)
        }
        val scope = Scope(options)

        scope.removeExtra("a")
        verify(observer, never()).removeExtra(any())
    }

    @Test
    fun `Scope getTransaction returns the transaction if there is no active span`() {
        val scope = Scope(SentryOptions())
        val transaction = SentryTransaction("name")
        scope.setTransaction(transaction)
        assertEquals(transaction, scope.span)
    }

    @Test
    fun `Scope getTransaction returns the current span if there is an unfinished span`() {
        val scope = Scope(SentryOptions())
        val transaction = SentryTransaction("name")
        scope.setTransaction(transaction)
        val span = transaction.startChild()
        assertEquals(span, scope.span)
    }

    @Test
    fun `Scope getTransaction returns the current span if there is a finished span`() {
        val scope = Scope(SentryOptions())
        val transaction = SentryTransaction("name")
        scope.setTransaction(transaction)
        val span = transaction.startChild()
        span.finish()
        assertEquals(transaction, scope.span)
    }

    @Test
    fun `Scope getTransaction returns the latest span if there is a list of active span`() {
        val scope = Scope(SentryOptions())
        val transaction = SentryTransaction("name")
        scope.setTransaction(transaction)
        val span = transaction.startChild()
        val innerSpan = span.startChild()
        assertEquals(innerSpan, scope.span)
    }
}

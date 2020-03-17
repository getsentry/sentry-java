package io.sentry.core

import io.sentry.core.protocol.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull

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
        scope.transaction = "transaction"

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
        scope.transaction = "transaction"

        val fingerprints = mutableListOf("abc")
        scope.fingerprint = fingerprints

        val breadcrumb = Breadcrumb()
        breadcrumb.message = "message"

        scope.addBreadcrumb(breadcrumb)
        scope.setTag("tag", "tag")
        scope.setExtra("extra", "extra")

        val clone = scope.clone()

        assertEquals(SentryLevel.DEBUG, clone.level)
        assertEquals("transaction", clone.transaction)

        assertEquals("123", clone.user?.id)

        assertEquals("abc", clone.fingerprint.first())

        assertEquals("message", clone.breadcrumbs.first().message)

        assertEquals("tag", clone.tags["tag"])
        assertEquals("extra", clone.extras["extra"])
    }

    @Test
    fun `cloning scope and changing the original values wont change the clone values`() {
        val scope = Scope(SentryOptions())
        val level = SentryLevel.DEBUG
        scope.level = level

        val user = User()
        user.id = "123"

        scope.user = user
        scope.transaction = "transaction"

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

        scope.transaction = "newTransaction"

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
        assertEquals("transaction", clone.transaction)

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
        val options = SentryOptions()
        options.release = "rel"
        options.environment = "env"
        val user = User()
        user.id = "123"

        val scope = Scope(options)
        scope.user = user

        val sessionPair = scope.startSession()
        assertNotNull(sessionPair.current)
        assertEquals("rel", sessionPair.current.release)
        assertEquals("env", sessionPair.current.environment)
        assertEquals("123", sessionPair.current.deviceId)
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
}

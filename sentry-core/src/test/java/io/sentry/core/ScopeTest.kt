package io.sentry.core

import io.sentry.core.protocol.User
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame

class ScopeTest {
    @Test
    fun `cloning scope wont have the same references`() {
        val scope = Scope(1)
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
        val data = mutableMapOf(Pair("data", "data"))
        breadcrumb.data = data

        val date = Date()
        breadcrumb.timestamp = date
        breadcrumb.type = "type"
        breadcrumb.level = SentryLevel.DEBUG
        breadcrumb.category = "category"

        scope.addBreadcrumb(breadcrumb)
        scope.setTag("tag", "tag")
        scope.setExtra("extra", "extra")

        val clone = scope.clone()

        assertNotNull(clone)
        assertNotSame(scope, clone)
        assertNotSame(scope.user, clone.user)
        assertNotSame(scope.fingerprint, clone.fingerprint)
        assertNotSame(scope.breadcrumbs, clone.breadcrumbs)
        assertNotSame(scope.tags, clone.tags)
        assertNotSame(scope.extras, clone.extras)
    }

    @Test
    fun `cloning scope will have the same values`() {
        val scope = Scope(1)
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

        assertEquals("123", clone.user.id)

        assertEquals("abc", clone.fingerprint.first())

        assertEquals("message", clone.breadcrumbs.first().message)

        assertEquals("tag", clone.tags["tag"])
        assertEquals("extra", clone.extras["extra"])
    }

    @Test
    fun `cloning scope and changing the original values wont change the clone values`() {
        val scope = Scope(1)
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

        assertEquals(SentryLevel.DEBUG, clone.level)
        assertEquals("transaction", clone.transaction)

        assertEquals("123", clone.user.id)

        assertEquals("abc", clone.fingerprint.first())
        assertEquals(1, clone.fingerprint.size)

        assertEquals(1, clone.breadcrumbs.size)
        assertEquals("message", clone.breadcrumbs.first().message)

        assertEquals("tag", clone.tags["tag"])
        assertEquals(1, clone.tags.size)
        assertEquals("extra", clone.extras["extra"])
        assertEquals(1, clone.extras.size)
    }
}

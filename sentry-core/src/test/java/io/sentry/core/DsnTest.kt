package io.sentry.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class DsnTest {

    @Test
    fun `dsn parsed with path, sets all properties`() {
        val dsn = Dsn("https://publicKey:secretKey@host/path/id")

        assertEquals("https://host/path/api/id/store/", dsn.sentryUri.toURL().toString())
        assertEquals("publicKey", dsn.publicKey)
        assertEquals("secretKey", dsn.secretKey)
        assertEquals("/path/", dsn.path)
        assertEquals("id", dsn.projectId)
    }

    @Test
    fun `dsn parsed without path`() {
        val dsn = Dsn("https://key@host/id")
        assertEquals("https://host/api/id/store/", dsn.sentryUri.toURL().toString())
    }

    @Test
    fun `dsn parsed with port number`() {
        val dsn = Dsn("http://key@host:69/id")
        assertEquals("http://host:69/api/id/store/", dsn.sentryUri.toURL().toString())
    }

    @Test
    fun `dsn parsed with trailing slash`() {
        val dsn = Dsn("http://key@host/id/")
        assertEquals("http://host/api/id/store/", dsn.sentryUri.toURL().toString())
    }

    @Test
    fun `dsn parsed with no delimiter for key`() {
        val dsn = Dsn("https://publicKey@host/id")

        assertEquals("publicKey", dsn.publicKey)
        assertNull(dsn.secretKey)
    }

    @Test
    fun `when no project id exists, throws exception`() {
        val ex = assertFailsWith<InvalidDsnException> { Dsn("http://key@host/") }
        assertEquals("java.lang.IllegalArgumentException: Invalid DSN: A Project Id is required.", ex.message)
    }

    @Test
    fun `when no key exists, throws exception`() {
        val ex = assertFailsWith<InvalidDsnException> { Dsn("http://host/id") }
        assertEquals("java.lang.IllegalArgumentException: Invalid DSN: No public key provided.", ex.message)
    }

    @Test
    fun `when only passing secret key, throws exception`() {
        val ex = assertFailsWith<InvalidDsnException> { Dsn("https://:secret@host/path/id") }
        assertEquals("java.lang.IllegalArgumentException: Invalid DSN: No public key provided.", ex.message)
    }
}

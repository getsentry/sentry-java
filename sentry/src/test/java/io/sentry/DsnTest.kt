package io.sentry

import java.lang.IllegalArgumentException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class DsnTest {
    @Test
    fun `dsn parsed with path, sets all properties`() {
        val dsn = Dsn("https://publicKey:secretKey@host/path/id")

        assertEquals("https://host/path/api/id", dsn.sentryUri.toURL().toString())
        assertEquals("publicKey", dsn.publicKey)
        assertEquals("secretKey", dsn.secretKey)
        assertEquals("/path/", dsn.path)
        assertEquals("id", dsn.projectId)
    }

    @Test
    fun `dsn parsed with path, sets all properties and ignores query strings`() {
        // query strings were once a feature, but no more
        val dsn = Dsn("https://publicKey:secretKey@host/path/id?sample.rate=0.1")

        assertEquals("https://host/path/api/id", dsn.sentryUri.toURL().toString())
        assertEquals("publicKey", dsn.publicKey)
        assertEquals("secretKey", dsn.secretKey)
        assertEquals("/path/", dsn.path)
        assertEquals("id", dsn.projectId)
    }

    @Test
    fun `dsn parsed without path`() {
        val dsn = Dsn("https://key@host/id")
        assertEquals("https://host/api/id", dsn.sentryUri.toURL().toString())
    }

    @Test
    fun `dsn parsed with port number`() {
        val dsn = Dsn("http://key@host:69/id")
        assertEquals("http://host:69/api/id", dsn.sentryUri.toURL().toString())
    }

    @Test
    fun `dsn parsed with trailing slash`() {
        val dsn = Dsn("http://key@host/id/")
        assertEquals("http://host/api/id", dsn.sentryUri.toURL().toString())
    }

    @Test
    fun `dsn parsed with no delimiter for key`() {
        val dsn = Dsn("https://publicKey@host/id")

        assertEquals("publicKey", dsn.publicKey)
        assertNull(dsn.secretKey)
    }

    @Test
    fun `when no project id exists, throws exception`() {
        val ex = assertFailsWith<IllegalArgumentException> { Dsn("http://key@host/") }
        assertEquals("java.lang.IllegalArgumentException: Invalid DSN: A Project Id is required.", ex.message)
    }

    @Test
    fun `when no key exists, throws exception`() {
        val ex = assertFailsWith<IllegalArgumentException> { Dsn("http://host/id") }
        assertEquals("java.lang.IllegalArgumentException: Invalid DSN: No public key provided.", ex.message)
    }

    @Test
    fun `when only passing secret key, throws exception`() {
        val ex = assertFailsWith<IllegalArgumentException> { Dsn("https://:secret@host/path/id") }
        assertEquals("java.lang.IllegalArgumentException: Invalid DSN: No public key provided.", ex.message)
    }

    @Test
    fun `dsn is normalized`() {
        val dsn = Dsn("http://key@host//id")
        assertEquals("http://host/api/id", dsn.sentryUri.toURL().toString())
    }

    @Test
    fun `non http protocols are not accepted`() {
        assertFailsWith<IllegalArgumentException> { Dsn("ftp://publicKey:secretKey@host/path/id") }
        assertFailsWith<IllegalArgumentException> { Dsn("jar://publicKey:secretKey@host/path/id") }
    }

    @Test
    fun `both http and https protocols are accepted`() {
        Dsn("http://publicKey:secretKey@host/path/id")
        Dsn("https://publicKey:secretKey@host/path/id")

        Dsn("HTTP://publicKey:secretKey@host/path/id")
        Dsn("HTTPS://publicKey:secretKey@host/path/id")
    }
}

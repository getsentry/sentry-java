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
    assertEquals(
      "java.lang.IllegalArgumentException: Invalid DSN: A Project Id is required.",
      ex.message,
    )
  }

  @Test
  fun `when no key exists, throws exception`() {
    val ex = assertFailsWith<IllegalArgumentException> { Dsn("http://host/id") }
    assertEquals(
      "java.lang.IllegalArgumentException: Invalid DSN: No public key provided.",
      ex.message,
    )
  }

  @Test
  fun `when only passing secret key, throws exception`() {
    val ex = assertFailsWith<IllegalArgumentException> { Dsn("https://:secret@host/path/id") }
    assertEquals(
      "java.lang.IllegalArgumentException: Invalid DSN: No public key provided.",
      ex.message,
    )
  }

  @Test
  fun `dsn is normalized`() {
    val dsn = Dsn("http://key@host//id")
    assertEquals("http://host/api/id", dsn.sentryUri.toURL().toString())
  }

  @Test
  fun `dsn parsed with leading and trailing whitespace`() {
    val dsn = Dsn("  https://key@host/id  ")
    assertEquals("https://host/api/id", dsn.sentryUri.toURL().toString())
  }

  @Test
  fun `when dsn is empty, throws exception`() {
    val ex = assertFailsWith<IllegalArgumentException> { Dsn("") }
    assertEquals("java.lang.IllegalArgumentException: The DSN is empty.", ex.message)
  }

  @Test
  fun `when dsn is only whitespace, throws exception`() {
    val ex = assertFailsWith<IllegalArgumentException> { Dsn("   ") }
    assertEquals("java.lang.IllegalArgumentException: The DSN is empty.", ex.message)
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

  @Test
  fun `extracts org id from host`() {
    val dsn = Dsn("https://key@o123.ingest.sentry.io/456")
    assertEquals("123", dsn.orgId)
  }

  @Test
  fun `extracts single digit org id from host`() {
    val dsn = Dsn("https://key@o1.ingest.us.sentry.io/456")
    assertEquals("1", dsn.orgId)
  }

  @Test
  fun `returns null org id when host has no org prefix`() {
    val dsn = Dsn("https://key@sentry.io/456")
    assertNull(dsn.orgId)
  }

  @Test
  fun `returns null org id for non-standard host`() {
    val dsn = Dsn("http://key@localhost:9000/456")
    assertNull(dsn.orgId)
  }

  @Test
  fun `when dsn is null, throws exception`() {
    assertFailsWith<IllegalArgumentException> { Dsn(null) }
  }

  @Test
  fun `when dsn has no scheme separator, throws exception`() {
    assertFailsWith<IllegalArgumentException> { Dsn("httpspublicKey@host/id") }
  }

  @Test
  fun `when dsn has no slash after host, throws exception`() {
    assertFailsWith<IllegalArgumentException> { Dsn("https://key@host") }
  }

  @Test
  fun `dsn parsed with multiple path segments`() {
    val dsn = Dsn("https://key@host/path/to/sentry/id")

    assertEquals("https://host/path/to/sentry/api/id", dsn.sentryUri.toURL().toString())
    assertEquals("key", dsn.publicKey)
    assertEquals("/path/to/sentry/", dsn.path)
    assertEquals("id", dsn.projectId)
  }

  @Test
  fun `dsn parsed with port and path`() {
    val dsn = Dsn("http://key:secret@host:8080/path/id")

    assertEquals("http://host:8080/path/api/id", dsn.sentryUri.toURL().toString())
    assertEquals("key", dsn.publicKey)
    assertEquals("secret", dsn.secretKey)
    assertEquals("/path/", dsn.path)
    assertEquals("id", dsn.projectId)
  }

  @Test
  fun `dsn with multiple double slashes in path is normalized`() {
    val dsn = Dsn("http://key@host//path//id")
    assertEquals("http://host/path/api/id", dsn.sentryUri.toURL().toString())
  }

  @Test
  fun `dsn with query string and port`() {
    val dsn = Dsn("https://key@host:443/id?foo=bar&baz=1")

    assertEquals("https://host:443/api/id", dsn.sentryUri.toURL().toString())
    assertEquals("id", dsn.projectId)
  }

  @Test
  fun `dsn with empty secret key after colon`() {
    val dsn = Dsn("https://publicKey:@host/id")

    assertEquals("publicKey", dsn.publicKey)
    assertEquals("", dsn.secretKey)
  }

  @Test
  fun `dsn with numeric project id`() {
    val dsn = Dsn("https://key@o123.ingest.sentry.io/1234567")

    assertEquals("1234567", dsn.projectId)
    assertEquals("123", dsn.orgId)
    assertEquals("https://o123.ingest.sentry.io/api/1234567", dsn.sentryUri.toURL().toString())
  }
}

package io.sentry

import com.google.common.truth.Truth.assertThat
import java.lang.IllegalArgumentException
import kotlin.test.Test
import kotlin.test.assertFailsWith

class DsnTest {
  @Test
  fun `dsn parsed with path, sets all properties`() {
    val dsn = Dsn("https://publicKey:secretKey@host/path/id")

    assertThat(dsn.sentryUri.toURL().toString()).isEqualTo("https://host/path/api/id")
    assertThat(dsn.publicKey).isEqualTo("publicKey")
    assertThat(dsn.secretKey).isEqualTo("secretKey")
    assertThat(dsn.path).isEqualTo("/path/")
    assertThat(dsn.projectId).isEqualTo("id")
  }

  @Test
  fun `dsn parsed with path, sets all properties and ignores query strings`() {
    // query strings were once a feature, but no more
    val dsn = Dsn("https://publicKey:secretKey@host/path/id?sample.rate=0.1")

    assertThat(dsn.sentryUri.toURL().toString()).isEqualTo("https://host/path/api/id")
    assertThat(dsn.publicKey).isEqualTo("publicKey")
    assertThat(dsn.secretKey).isEqualTo("secretKey")
    assertThat(dsn.path).isEqualTo("/path/")
    assertThat(dsn.projectId).isEqualTo("id")
  }

  @Test
  fun `dsn parsed without path`() {
    val dsn = Dsn("https://key@host/id")
    assertThat(dsn.sentryUri.toURL().toString()).isEqualTo("https://host/api/id")
  }

  @Test
  fun `dsn parsed with port number`() {
    val dsn = Dsn("http://key@host:69/id")
    assertThat(dsn.sentryUri.toURL().toString()).isEqualTo("http://host:69/api/id")
  }

  @Test
  fun `dsn parsed with trailing slash`() {
    val dsn = Dsn("http://key@host/id/")
    assertThat(dsn.sentryUri.toURL().toString()).isEqualTo("http://host/api/id")
  }

  @Test
  fun `dsn parsed with no delimiter for key`() {
    val dsn = Dsn("https://publicKey@host/id")

    assertThat(dsn.publicKey).isEqualTo("publicKey")
    assertThat(dsn.secretKey).isNull()
  }

  @Test
  fun `when no project id exists, throws exception`() {
    val ex = assertFailsWith<IllegalArgumentException> { Dsn("http://key@host/") }
    assertThat(ex).hasMessageThat().isEqualTo("Invalid DSN: A Project Id is required.")
  }

  @Test
  fun `when no key exists, throws exception`() {
    val ex = assertFailsWith<IllegalArgumentException> { Dsn("http://host/id") }
    assertThat(ex).hasMessageThat().isEqualTo("Invalid DSN: No public key provided.")
  }

  @Test
  fun `when only passing secret key, throws exception`() {
    val ex = assertFailsWith<IllegalArgumentException> { Dsn("https://:secret@host/path/id") }
    assertThat(ex).hasMessageThat().isEqualTo("Invalid DSN: No public key provided.")
  }

  @Test
  fun `dsn is normalized`() {
    val dsn = Dsn("http://key@host//id")
    assertThat(dsn.sentryUri.toURL().toString()).isEqualTo("http://host/api/id")
  }

  @Test
  fun `dsn parsed with leading and trailing whitespace`() {
    val dsn = Dsn("  https://key@host/id  ")
    assertThat(dsn.sentryUri.toURL().toString()).isEqualTo("https://host/api/id")
  }

  @Test
  fun `when dsn is empty, throws exception`() {
    val ex = assertFailsWith<IllegalArgumentException> { Dsn("") }
    assertThat(ex).hasMessageThat().isEqualTo("The DSN is empty.")
  }

  @Test
  fun `when dsn is only whitespace, throws exception`() {
    val ex = assertFailsWith<IllegalArgumentException> { Dsn("   ") }
    assertThat(ex).hasMessageThat().isEqualTo("The DSN is empty.")
  }

  @Test
  fun `non http protocols are not accepted`() {
    val ftp =
      assertFailsWith<IllegalArgumentException> { Dsn("ftp://publicKey:secretKey@host/path/id") }
    assertThat(ftp).hasMessageThat().isEqualTo("Invalid DSN: Invalid scheme 'ftp'.")

    val jar =
      assertFailsWith<IllegalArgumentException> { Dsn("jar://publicKey:secretKey@host/path/id") }
    assertThat(jar).hasMessageThat().isEqualTo("Invalid DSN: Invalid scheme 'jar'.")
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
    assertThat(dsn.orgId).isEqualTo("123")
  }

  @Test
  fun `extracts single digit org id from host`() {
    val dsn = Dsn("https://key@o1.ingest.us.sentry.io/456")
    assertThat(dsn.orgId).isEqualTo("1")
  }

  @Test
  fun `returns null org id when host has no org prefix`() {
    val dsn = Dsn("https://key@sentry.io/456")
    assertThat(dsn.orgId).isNull()
  }

  @Test
  fun `returns null org id for non-standard host`() {
    val dsn = Dsn("http://key@localhost:9000/456")
    assertThat(dsn.orgId).isNull()
  }

  @Test
  fun `when dsn is null, throws exception`() {
    val ex = assertFailsWith<IllegalArgumentException> { Dsn(null) }
    assertThat(ex).hasMessageThat().isEqualTo("The DSN is required.")
  }

  @Test
  fun `when dsn has no scheme separator, throws exception`() {
    val ex = assertFailsWith<IllegalArgumentException> { Dsn("httpspublicKey@host/id") }
    assertThat(ex).hasMessageThat().isEqualTo("Invalid DSN: Missing scheme.")
  }

  @Test
  fun `when dsn has no slash after host, throws exception`() {
    val ex = assertFailsWith<IllegalArgumentException> { Dsn("https://key@host") }
    assertThat(ex).hasMessageThat().isEqualTo("Invalid DSN: A Project Id is required.")
  }

  @Test
  fun `when port is not a number, throws exception`() {
    val ex = assertFailsWith<IllegalArgumentException> { Dsn("http://key@host:abc/1") }
    assertThat(ex).hasMessageThat().isEqualTo("Invalid DSN: Invalid port 'abc'.")
  }

  @Test
  fun `dsn parsed with multiple path segments`() {
    val dsn = Dsn("https://key@host/path/to/sentry/id")

    assertThat(dsn.sentryUri.toURL().toString()).isEqualTo("https://host/path/to/sentry/api/id")
    assertThat(dsn.publicKey).isEqualTo("key")
    assertThat(dsn.path).isEqualTo("/path/to/sentry/")
    assertThat(dsn.projectId).isEqualTo("id")
  }

  @Test
  fun `dsn parsed with port and path`() {
    val dsn = Dsn("http://key:secret@host:8080/path/id")

    assertThat(dsn.sentryUri.toURL().toString()).isEqualTo("http://host:8080/path/api/id")
    assertThat(dsn.publicKey).isEqualTo("key")
    assertThat(dsn.secretKey).isEqualTo("secret")
    assertThat(dsn.path).isEqualTo("/path/")
    assertThat(dsn.projectId).isEqualTo("id")
  }

  @Test
  fun `dsn with multiple double slashes in path is normalized`() {
    val dsn = Dsn("http://key@host//path//id")
    assertThat(dsn.sentryUri.toURL().toString()).isEqualTo("http://host/path/api/id")
  }

  @Test
  fun `dsn with query string and port`() {
    val dsn = Dsn("https://key@host:443/id?foo=bar&baz=1")

    assertThat(dsn.sentryUri.toURL().toString()).isEqualTo("https://host:443/api/id")
    assertThat(dsn.projectId).isEqualTo("id")
  }

  @Test
  fun `dsn with fragment is stripped from project id`() {
    val dsn = Dsn("https://key@host/123#frag")

    assertThat(dsn.projectId).isEqualTo("123")
    assertThat(dsn.sentryUri.toURL().toString()).isEqualTo("https://host/api/123")
  }

  @Test
  fun `dsn with both query string and fragment is stripped from project id`() {
    val dsn = Dsn("https://key@host/123?foo=bar#frag")

    assertThat(dsn.projectId).isEqualTo("123")
    assertThat(dsn.sentryUri.toURL().toString()).isEqualTo("https://host/api/123")
  }

  @Test
  fun `dsn with ipv6 host and port`() {
    val dsn = Dsn("https://key@[2001:db8::1]:9000/1")

    assertThat(dsn.sentryUri.toURL().toString()).isEqualTo("https://[2001:db8::1]:9000/api/1")
    assertThat(dsn.projectId).isEqualTo("1")
  }

  @Test
  fun `dsn with ipv6 host and no port`() {
    val dsn = Dsn("https://key@[::1]/1")

    assertThat(dsn.sentryUri.toURL().toString()).isEqualTo("https://[::1]/api/1")
    assertThat(dsn.projectId).isEqualTo("1")
  }

  @Test
  fun `dsn with empty secret key after colon`() {
    val dsn = Dsn("https://publicKey:@host/id")

    assertThat(dsn.publicKey).isEqualTo("publicKey")
    assertThat(dsn.secretKey).isEqualTo("")
  }

  @Test
  fun `dsn with numeric project id`() {
    val dsn = Dsn("https://key@o123.ingest.sentry.io/1234567")

    assertThat(dsn.projectId).isEqualTo("1234567")
    assertThat(dsn.orgId).isEqualTo("123")
    assertThat(dsn.sentryUri.toURL().toString())
      .isEqualTo("https://o123.ingest.sentry.io/api/1234567")
  }
}

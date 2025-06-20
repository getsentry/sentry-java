package io.sentry

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DsnUtilTest {
  companion object {
    val DSN = "https://publicKey:secretKey@host/path/id?sample.rate=0.1"
  }

  @Test
  fun `returns false for null options`() {
    assertFalse(DsnUtil.urlContainsDsnHost(null, "sentry.io"))
  }

  @Test
  fun `returns false for options with null dsn`() {
    assertFalse(DsnUtil.urlContainsDsnHost(optionsWithDsn(null), "sentry.io"))
  }

  @Test
  fun `returns true for matching host`() {
    assertTrue(DsnUtil.urlContainsDsnHost(optionsWithDsn(DSN), "host"))
  }

  @Test
  fun `returns true for matching host with different case`() {
    assertTrue(DsnUtil.urlContainsDsnHost(optionsWithDsn(DSN), "HOST"))
  }

  @Test
  fun `returns true for matching host with different case 2`() {
    assertTrue(DsnUtil.urlContainsDsnHost(optionsWithDsn(DSN.uppercase()), "host"))
  }

  @Test
  fun `returns false for null url`() {
    assertFalse(DsnUtil.urlContainsDsnHost(optionsWithDsn(DSN), null))
  }

  @Test
  fun `returns false for same host but different port`() {
    assertFalse(
      DsnUtil.urlContainsDsnHost(
        optionsWithDsn("http://publicKey:secretKey@localhost:8080/path/id?sample.rate=0.1"),
        "localhost:8081",
      )
    )
  }

  @Test
  fun `returns true for same host and port`() {
    assertTrue(
      DsnUtil.urlContainsDsnHost(
        optionsWithDsn("http://publicKey:secretKey@localhost:8080/path/id?sample.rate=0.1"),
        "localhost:8080",
      )
    )
  }

  private fun optionsWithDsn(dsn: String?): SentryOptions = SentryOptions().also { it.dsn = dsn }
}

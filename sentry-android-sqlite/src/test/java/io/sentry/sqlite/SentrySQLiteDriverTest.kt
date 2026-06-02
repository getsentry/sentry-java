package io.sentry.sqlite

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteStatement
import io.sentry.IScopes
import io.sentry.Sentry
import io.sentry.SentryIntegrationPackageStorage
import io.sentry.SentryOptions
import io.sentry.SentryTracer
import io.sentry.SpanDataConvention
import io.sentry.TransactionContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.Before
import org.mockito.Mockito
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SentrySQLiteDriverTest {

  private class Fixture {

    val mockDriver = mock<SQLiteDriver>()
    val mockConnection = mock<SQLiteConnection>()

    fun getSut(fileName: String): SentrySQLiteDriver {
      whenever(mockDriver.open(fileName)).thenReturn(mockConnection)
      return SentrySQLiteDriver.create(mockDriver) as SentrySQLiteDriver
    }
  }

  private val fixture = Fixture()

  @Before
  fun setup() {
    SentryIntegrationPackageStorage.getInstance().clearStorage()
  }

  @Test
  fun `create registers SQLiteDriver integration`() {
    assertFalse(SentryIntegrationPackageStorage.getInstance().integrations.contains("SQLiteDriver"))
    SentrySQLiteDriver.create(fixture.mockDriver)
    assertTrue(SentryIntegrationPackageStorage.getInstance().integrations.contains("SQLiteDriver"))
  }

  @Test
  fun `create with non-wrapped driver returns SentrySQLiteDriver`() {
    val result = SentrySQLiteDriver.create(fixture.mockDriver)
    assertIs<SentrySQLiteDriver>(result)
  }

  @Test
  fun `create with already-wrapped driver returns same instance without re-wrapping`() {
    val wrapped = SentrySQLiteDriver.create(fixture.mockDriver)
    val doubleWrapped = SentrySQLiteDriver.create(wrapped)
    assertSame(wrapped, doubleWrapped)
  }

  @Test
  fun `open returns SentrySQLiteConnection wrapping delegate if wrapping succeeds`() {
    val driver = fixture.getSut("myapp.db")
    val connection = driver.open("myapp.db")
    assertIs<SentrySQLiteConnection>(connection)
  }

  @Test
  fun `open returns the unwrapped delegate if wrapping fails`() {
    val brokenScopes = mock<IScopes>()
    val validOptions = SentryOptions().apply { dsn = "https://key@sentry.io/proj" }
    whenever(brokenScopes.options)
      .thenThrow(RuntimeException("Sentry options unavailable"))
      .thenReturn(validOptions)

    Mockito.mockStatic(Sentry::class.java).use { mockedSentry ->
      mockedSentry.`when`<Any> { Sentry.getCurrentScopes() }.thenReturn(brokenScopes)

      val driver = fixture.getSut("myapp.db")
      val result = driver.open("myapp.db")

      assertSame(fixture.mockConnection, result)
      verify(fixture.mockDriver).open("myapp.db")
    }
  }

  @Test
  fun `all calls are propagated to the delegate`() {
    val sut = fixture.getSut("myapp.db")

    whenever(fixture.mockDriver.hasConnectionPool).thenReturn(true)
    assertTrue(sut.hasConnectionPool)

    whenever(fixture.mockDriver.hasConnectionPool).thenReturn(false)
    assertFalse(sut.hasConnectionPool)

    sut.open("myapp.db")
    verify(fixture.mockDriver).open("myapp.db")
  }

  // Smoke test ensuring all layers are properly wired up.
  @Test
  fun `full stack produces a span with correct metadata`() {
    val scopes = mock<IScopes>()
    val options = SentryOptions().apply { dsn = "https://key@sentry.io/proj" }
    whenever(scopes.options).thenReturn(options)
    val tracer = SentryTracer(TransactionContext("name", "op"), scopes)
    whenever(scopes.span).thenReturn(tracer)

    val mockStatement = mock<SQLiteStatement>()
    whenever(fixture.mockConnection.prepare("SELECT * FROM users")).thenReturn(mockStatement)
    whenever(mockStatement.step()).thenReturn(true, false)

    Mockito.mockStatic(Sentry::class.java).use { mockedSentry ->
      mockedSentry.`when`<Any> { Sentry.getCurrentScopes() }.thenReturn(scopes)

      val driver = fixture.getSut("/data/data/com.example/databases/myapp.db")
      val connection = driver.open("/data/data/com.example/databases/myapp.db")
      val statement = connection.prepare("SELECT * FROM users")

      assertIs<SentrySQLiteConnection>(connection)
      assertIs<SentrySQLiteStatement>(statement)

      statement.step()
      statement.step()

      val span = tracer.children.firstOrNull()
      assertNotNull(span)
      assertEquals("myapp.db", span.data[SpanDataConvention.DB_NAME_KEY])
    }
  }
}

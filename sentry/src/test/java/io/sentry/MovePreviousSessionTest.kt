package io.sentry

import io.sentry.cache.EnvelopeCache
import io.sentry.cache.IEnvelopeCache
import io.sentry.transport.NoOpEnvelopeCache
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class MovePreviousSessionTest {

  private class Fixture {
    val tempDir: Path = Files.createTempDirectory("sentry-move-session-test")
    val options =
      SentryOptions().apply {
        isDebug = true
        setLogger(SystemOutLogger())
      }
    val cache = mock<EnvelopeCache>()

    fun getSUT(
      cacheDirPath: String? = tempDir.toAbsolutePath().toFile().absolutePath,
      isEnableSessionTracking: Boolean = true,
      envelopeCache: IEnvelopeCache? = null,
    ): MovePreviousSession {
      options.cacheDirPath = cacheDirPath
      options.isEnableAutoSessionTracking = isEnableSessionTracking
      options.setEnvelopeDiskCache(envelopeCache ?: EnvelopeCache.create(options))
      return MovePreviousSession(options)
    }

    fun cleanup() {
      tempDir.toFile().deleteRecursively()
    }
  }

  private lateinit var fixture: Fixture

  @BeforeTest
  fun setup() {
    fixture = Fixture()
  }

  @AfterTest
  fun teardown() {
    fixture.cleanup()
  }

  @Test
  fun `when cache dir is null, logs and returns early`() {
    val sut = fixture.getSUT(cacheDirPath = null, envelopeCache = fixture.cache)

    sut.run()

    verify(fixture.cache, never()).movePreviousSession(any(), any())
    verify(fixture.cache, never()).flushPreviousSession()
  }

  @Test
  fun `when session tracking is disabled, logs and returns early`() {
    val sut = fixture.getSUT(isEnableSessionTracking = false, envelopeCache = fixture.cache)

    sut.run()

    verify(fixture.cache, never()).movePreviousSession(any(), any())
    verify(fixture.cache, never()).flushPreviousSession()
  }

  @Test
  fun `when envelope cache is not EnvelopeCache instance, does nothing`() {
    val sut = fixture.getSUT(envelopeCache = NoOpEnvelopeCache.getInstance())

    sut.run()

    verify(fixture.cache, never()).movePreviousSession(any(), any())
    verify(fixture.cache, never()).flushPreviousSession()
  }

  @Test
  fun `integration test with real EnvelopeCache`() {
    val sut = fixture.getSUT()

    // Create a current session file
    val currentSessionFile = EnvelopeCache.getCurrentSessionFile(fixture.options.cacheDirPath!!)
    val previousSessionFile = EnvelopeCache.getPreviousSessionFile(fixture.options.cacheDirPath!!)

    currentSessionFile.createNewFile()
    currentSessionFile.writeText("session content")

    assertTrue(currentSessionFile.exists())
    assertFalse(previousSessionFile.exists())

    sut.run()

    // Wait for flush to complete
    (fixture.options.envelopeDiskCache as EnvelopeCache).waitPreviousSessionFlush()

    // Current session file should have been moved to previous
    assertFalse(currentSessionFile.exists())
    assertTrue(previousSessionFile.exists())
    assert(previousSessionFile.readText() == "session content")

    fixture.cleanup()
  }

  @Test
  fun `integration test when current session file does not exist`() {
    val sut = fixture.getSUT()

    val currentSessionFile = EnvelopeCache.getCurrentSessionFile(fixture.options.cacheDirPath!!)
    val previousSessionFile = EnvelopeCache.getPreviousSessionFile(fixture.options.cacheDirPath!!)

    assertFalse(currentSessionFile.exists())
    assertFalse(previousSessionFile.exists())

    sut.run()

    (fixture.options.envelopeDiskCache as EnvelopeCache).waitPreviousSessionFlush()

    assertFalse(currentSessionFile.exists())
    assertFalse(previousSessionFile.exists())

    fixture.cleanup()
  }

  @Test
  fun `integration test when previous session file already exists`() {
    val sut = fixture.getSUT()

    val currentSessionFile = EnvelopeCache.getCurrentSessionFile(fixture.options.cacheDirPath!!)
    val previousSessionFile = EnvelopeCache.getPreviousSessionFile(fixture.options.cacheDirPath!!)

    currentSessionFile.createNewFile()
    currentSessionFile.writeText("current session")
    previousSessionFile.createNewFile()
    previousSessionFile.writeText("previous session")

    assertTrue(currentSessionFile.exists())
    assertTrue(previousSessionFile.exists())

    sut.run()

    (fixture.options.envelopeDiskCache as EnvelopeCache).waitPreviousSessionFlush()

    // Current session file should have been moved to previous
    assertFalse(currentSessionFile.exists())
    assertTrue(previousSessionFile.exists())
    assert(previousSessionFile.readText() == "current session")

    fixture.cleanup()
  }
}

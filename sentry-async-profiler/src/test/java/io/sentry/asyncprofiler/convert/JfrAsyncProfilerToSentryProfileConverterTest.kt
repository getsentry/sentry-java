package io.sentry.asyncprofiler.convert

import io.sentry.ILogger
import io.sentry.IProfileConverter
import io.sentry.IScope
import io.sentry.IScopes
import io.sentry.Sentry
import io.sentry.SentryOptions
import io.sentry.SentryStackTraceFactory
import io.sentry.TracesSampler
import io.sentry.asyncprofiler.provider.AsyncProfilerProfileConverterProvider
import io.sentry.protocol.profiling.SentryProfile
import io.sentry.test.DeferredExecutorService
import java.io.IOException
import kotlin.io.path.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever

class JfrAsyncProfilerToSentryProfileConverterTest {

  private val fixture = Fixture()

  private class Fixture {
    private val mockDsn = "http://key@localhost/proj"
    val executor = DeferredExecutorService()
    val mockedSentry = Mockito.mockStatic(Sentry::class.java)
    val mockLogger = mock<ILogger>()
    val mockTracesSampler = mock<TracesSampler>()
    val mockStackTraceFactory = mock<SentryStackTraceFactory>()

    val scopes: IScopes = mock()
    val scope: IScope = mock()

    val options =
      spy(SentryOptions()).apply {
        dsn = mockDsn
        profilesSampleRate = 1.0
        isDebug = true
        setLogger(mockLogger)
        // Set in-app packages for testing
        addInAppInclude("io.sentry")
        addInAppInclude("com.example")
      }

    init {
      whenever(mockTracesSampler.sampleSessionProfile(any())).thenReturn(true)
      // Setup default in-app behavior for stack trace factory
      whenever(mockStackTraceFactory.isInApp(any())).thenAnswer { invocation ->
        val className = invocation.getArgument<String>(0)
        className.startsWith("io.sentry") || className.startsWith("com.example")
      }
    }

    fun getSut(optionConfig: ((options: SentryOptions) -> Unit) = {}): IProfileConverter? {
      options.executorService = executor
      optionConfig(options)
      whenever(scopes.options).thenReturn(options)
      whenever(scope.options).thenReturn(options)
      return AsyncProfilerProfileConverterProvider().profileConverter
    }
  }

  @BeforeTest
  fun `set up`() {
    Sentry.setCurrentScopes(fixture.scopes)

    fixture.mockedSentry.`when`<IScopes> { Sentry.getCurrentScopes() }.thenReturn(fixture.scopes)
    fixture.mockedSentry.`when`<IScope> { Sentry.getGlobalScope() }.thenReturn(fixture.scope)

    // Ensure the global scope returns proper options for the static converter method
    whenever(fixture.scope.options).thenReturn(fixture.options)
  }

  @AfterTest
  fun clear() {
    Sentry.close()
    fixture.mockedSentry.close()
  }

  @Test
  fun `check number of samples for specific frame`() {
    val file = Path(loadFile("async_profiler_test_sample.jfr"))

    val sentryProfile = fixture.getSut()!!.convertFromFile(file)
    val tracingFilterFrame =
      sentryProfile.frames.filter {
        it.function == "slowFunction" &&
          it.module == "io.sentry.samples.console.Main"
      }

    val tracingFilterFrameIndexes = tracingFilterFrame.map { sentryProfile.frames.indexOf(it) }
    val tracingFilterStacks =
      sentryProfile.stacks.filter { it.any { inner -> tracingFilterFrameIndexes.contains(inner) } }
    val tracingFilterStackIds = tracingFilterStacks.map { sentryProfile.stacks.indexOf(it) }
    val tracingFilterSamples =
      sentryProfile.samples.filter { tracingFilterStackIds.contains(it.stackId) }

    // Sample size base on 101 samples/sec and 10 sec of profiling
    // So expected around 1010 samples (with some margin)
    assertTrue(tracingFilterSamples.count() >= 1000 && tracingFilterSamples.count() <= 1120, "Expected sample count between 1000 and 1120, but was ${tracingFilterSamples.count()}")
  }

  @Test
  fun `check number of samples for specific thread`() {
    val file = Path(loadFile("async_profiler_test_sample.jfr"))

    val sentryProfile = fixture.getSut()!!.convertFromFile(file)
    val mainThread = sentryProfile.threadMetadata.entries.firstOrNull { it.value.name == "main" }?.key

    val samples = sentryProfile.samples.filter { it.threadId == mainThread }

    // Sample size base on 101 samples/sec and 10 sec of profiling
    // So expected around 1010 samples (with some margin)
    assertTrue(samples.count() >= 1000 && samples.count() <= 1120, "Expected sample count between 1000 and 1120, but was ${samples.count()}")
  }

  @Test
  fun `check no duplicate frames`() {
    val file = Path(loadFile("async_profiler_test_sample.jfr"))
    val sentryProfile = fixture.getSut()!!.convertFromFile(file)

    val frameSet = sentryProfile.frames.toSet()

    assertEquals(frameSet.size, sentryProfile.frames.size)
  }

  @Test
  fun `convertFromFile with valid JFR returns populated SentryProfile`() {
    val file = Path(loadFile("async_profiler_test_sample.jfr"))

    val sentryProfile = fixture.getSut()!!.convertFromFile(file)

    assertNotNull(sentryProfile)
    assertValidSentryProfile(sentryProfile)
  }

  @Test
  fun `convertFromFile parses timestamps correctly`() {
    val file = Path(loadFile("async_profiler_test_sample.jfr"))

    val sentryProfile = fixture.getSut()!!.convertFromFile(file)

    val samples = sentryProfile.samples
    assertTrue(samples.isNotEmpty())

    // Verify timestamps are in seconds with proper decimal places
    samples.forEach { sample ->
      val timestamp = sample.timestamp
      assertTrue(timestamp > 0, "Timestamp should be positive")
      // No need to check exact decimal places as this depends on JFR precision
      assertTrue(
        timestamp < System.currentTimeMillis() / 1000.0 + 360,
        "Timestamp should be recent",
      )
    }

    if (samples.isNotEmpty()) {
      val minTimestamp = samples.minOf { it.timestamp }
      val maxTimestamp = samples.maxOf { it.timestamp }
      assertTrue(maxTimestamp >= minTimestamp, "Max timestamp should be >= min timestamp")
    }
  }

  @Test
  fun `convertFromFile extracts thread metadata correctly`() {
    val file = Path(loadFile("async_profiler_test_sample.jfr"))

    val sentryProfile = fixture.getSut()!!.convertFromFile(file)

    val threadMetadata = sentryProfile.threadMetadata
    val samples = sentryProfile.samples

    assertTrue(threadMetadata.isNotEmpty())

    // Verify thread IDs in samples match thread metadata keys
    val threadIdsFromSamples = samples.map { it.threadId }.toSet()
    threadIdsFromSamples.forEach { threadId ->
      assertTrue(
        threadMetadata.containsKey(threadId),
        "Thread metadata should contain thread ID: $threadId",
      )
    }

    // Verify thread metadata has proper values
    threadMetadata.forEach { (_, metadata) ->
      assertNotNull(metadata.name, "Thread name should not be null")
      assertEquals(0, metadata.priority, "Thread priority should be default (0)")
    }
  }

  @Test
  fun `converter processes frames with complete information`() {
    val file = Path(loadFile("async_profiler_test_sample.jfr"))

    val sentryProfile = fixture.getSut()!!.convertFromFile(file)

    val frames = sentryProfile.frames
    assertTrue(frames.isNotEmpty())

    // Find frames with complete information
    val completeFrames =
      frames.filter { frame ->
        frame.function != null &&
          frame.module != null &&
          frame.lineno != null &&
          frame.filename != null
      }

    assertTrue(completeFrames.isNotEmpty(), "Should have frames with complete information")
  }

  @Test
  fun `converter marks in-app frames correctly`() {
    val file = Path(loadFile("async_profiler_test_sample.jfr"))

    val sentryProfile = fixture.getSut()!!.convertFromFile(file)

    val frames = sentryProfile.frames

    // Verify system packages are marked as not in-app
    val systemFrames =
      frames.filter { frame ->
        frame.module?.let {
          it.startsWith("java.") || it.startsWith("sun.") || it.startsWith("jdk.")
        } ?: false
      }

    val inappSentryFrames =
      frames.filter { frame -> frame.module?.startsWith("io.sentry.") ?: false }

    val emptyModuleFrames = frames.filter { it.module.isNullOrEmpty() }

    // Verify system classes are not marked as in-app
    systemFrames.forEach { frame ->
      assertFalse(frame.isInApp ?: false, "System classes should not be marked as in app")
    }

    // Verify sentry classes are marked as in-app
    inappSentryFrames.forEach { frame ->
      assertTrue(frame.isInApp ?: false, "Sentry classes should be marked as in app")
    }

    // Verify empty class names are marked as not in-app
    emptyModuleFrames.forEach { frame ->
      assertFalse(frame.isInApp ?: true, "Empty module frame should not be in-app")
    }
  }

  @Test
  fun `converter filters native methods`() {
    val file = Path(loadFile("async_profiler_test_sample.jfr"))

    val sentryProfile = fixture.getSut()!!.convertFromFile(file)

    // Native methods should be filtered out during frame creation
    // Verify no frames have characteristics of native methods
    sentryProfile.frames.forEach { frame ->
      // Native methods would have been skipped, so no frame should indicate native
      assertTrue(
        frame.filename?.isNotEmpty() == true || frame?.module?.isNotEmpty() == true,
        "Frame should have some non-native information",
      )
    }
  }

  @Test(expected = IOException::class)
  fun `convertFromFile with non-existent file throws IOException`() {
    val nonExistentFile = Path("/non/existent/file.jfr")

    JfrAsyncProfilerToSentryProfileConverter.convertFromFileStatic(nonExistentFile)
  }

  private fun loadFile(path: String): String = javaClass.classLoader!!.getResource(path)!!.file

  private fun assertValidSentryProfile(profile: SentryProfile) {
    assertNotNull(profile.samples, "Samples should not be null")
    assertNotNull(profile.frames, "Frames should not be null")
    assertNotNull(profile.stacks, "Stacks should not be null")
    assertNotNull(profile.threadMetadata, "Thread metadata should not be null")
  }
}

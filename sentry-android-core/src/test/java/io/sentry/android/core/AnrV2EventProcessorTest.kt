package io.sentry.android.core

import android.app.ActivityManager
import android.app.ActivityManager.MemoryInfo
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.IpAddressUtils
import io.sentry.NoOpLogger
import io.sentry.SentryBaseEvent
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.SentryLevel.DEBUG
import io.sentry.SpanContext
import io.sentry.cache.PersistingOptionsObserver.DIST_FILENAME
import io.sentry.cache.PersistingOptionsObserver.ENVIRONMENT_FILENAME
import io.sentry.cache.PersistingOptionsObserver.OPTIONS_CACHE
import io.sentry.cache.PersistingOptionsObserver.PROGUARD_UUID_FILENAME
import io.sentry.cache.PersistingOptionsObserver.RELEASE_FILENAME
import io.sentry.cache.PersistingOptionsObserver.REPLAY_ERROR_SAMPLE_RATE_FILENAME
import io.sentry.cache.PersistingOptionsObserver.SDK_VERSION_FILENAME
import io.sentry.cache.PersistingScopeObserver
import io.sentry.cache.PersistingScopeObserver.BREADCRUMBS_FILENAME
import io.sentry.cache.PersistingScopeObserver.CONTEXTS_FILENAME
import io.sentry.cache.PersistingScopeObserver.EXTRAS_FILENAME
import io.sentry.cache.PersistingScopeObserver.FINGERPRINT_FILENAME
import io.sentry.cache.PersistingScopeObserver.LEVEL_FILENAME
import io.sentry.cache.PersistingScopeObserver.REPLAY_FILENAME
import io.sentry.cache.PersistingScopeObserver.REQUEST_FILENAME
import io.sentry.cache.PersistingScopeObserver.SCOPE_CACHE
import io.sentry.cache.PersistingScopeObserver.TAGS_FILENAME
import io.sentry.cache.PersistingScopeObserver.TRACE_FILENAME
import io.sentry.cache.PersistingScopeObserver.TRANSACTION_FILENAME
import io.sentry.cache.PersistingScopeObserver.USER_FILENAME
import io.sentry.cache.tape.QueueFile
import io.sentry.hints.AbnormalExit
import io.sentry.hints.Backfillable
import io.sentry.protocol.Browser
import io.sentry.protocol.Contexts
import io.sentry.protocol.DebugImage
import io.sentry.protocol.DebugMeta
import io.sentry.protocol.Device
import io.sentry.protocol.OperatingSystem
import io.sentry.protocol.Request
import io.sentry.protocol.Response
import io.sentry.protocol.SdkVersion
import io.sentry.protocol.SentryId
import io.sentry.protocol.SentryStackFrame
import io.sentry.protocol.SentryStackTrace
import io.sentry.protocol.SentryThread
import io.sentry.protocol.User
import io.sentry.util.HintUtils
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowActivityManager
import org.robolectric.shadows.ShadowBuild

@RunWith(AndroidJUnit4::class)
class AnrV2EventProcessorTest {
  @get:Rule val tmpDir = TemporaryFolder()

  class Fixture {
    companion object {
      const val REPLAY_ID = "64cf554cc8d74c6eafa3e08b7c984f6d"
    }

    val buildInfo = mock<BuildInfoProvider>()
    lateinit var context: Context
    val options = SentryAndroidOptions().apply { setLogger(NoOpLogger.getInstance()) }

    fun getSut(
      dir: TemporaryFolder,
      currentSdk: Int = Build.VERSION_CODES.LOLLIPOP,
      populateScopeCache: Boolean = false,
      populateOptionsCache: Boolean = false,
      replayErrorSampleRate: Double? = null,
      isSendDefaultPii: Boolean = true,
    ): AnrV2EventProcessor {
      options.cacheDirPath = dir.newFolder().absolutePath
      options.environment = "release"
      options.isSendDefaultPii = isSendDefaultPii
      options.addScopeObserver(PersistingScopeObserver(options))

      whenever(buildInfo.sdkInfoVersion).thenReturn(currentSdk)
      whenever(buildInfo.isEmulator).thenReturn(true)

      if (populateScopeCache) {
        persistScope(TRACE_FILENAME, SpanContext("ui.load"))
        persistScope(
          USER_FILENAME,
          User().apply {
            username = "bot"
            id = "bot@me.com"
          },
        )
        persistScope(TAGS_FILENAME, mapOf("one" to "two"))
        persistScope(
          BREADCRUMBS_FILENAME,
          listOf(Breadcrumb.debug("test"), Breadcrumb.navigation("from", "to")),
        )
        persistScope(EXTRAS_FILENAME, mapOf("key" to 123))
        persistScope(TRANSACTION_FILENAME, "TestActivity")
        persistScope(FINGERPRINT_FILENAME, listOf("finger", "print"))
        persistScope(LEVEL_FILENAME, SentryLevel.INFO)
        persistScope(
          CONTEXTS_FILENAME,
          Contexts().apply {
            setTrace(SpanContext("test"))
            setResponse(Response().apply { bodySize = 1024 })
            setBrowser(Browser().apply { name = "Google Chrome" })
          },
        )
        persistScope(
          REQUEST_FILENAME,
          Request().apply {
            url = "google.com"
            method = "GET"
          },
        )
        persistScope(REPLAY_FILENAME, SentryId(REPLAY_ID))
      }

      if (populateOptionsCache) {
        persistOptions(RELEASE_FILENAME, "io.sentry.samples@1.2.0+232")
        persistOptions(PROGUARD_UUID_FILENAME, "uuid")
        persistOptions(SDK_VERSION_FILENAME, SdkVersion("sentry.java.android", "6.15.0"))
        persistOptions(DIST_FILENAME, "232")
        persistOptions(ENVIRONMENT_FILENAME, "debug")
        persistOptions(TAGS_FILENAME, mapOf("option" to "tag"))
        replayErrorSampleRate?.let {
          persistOptions(REPLAY_ERROR_SAMPLE_RATE_FILENAME, it.toString())
        }
      }

      return AnrV2EventProcessor(context, options, buildInfo)
    }

    fun <T : Any> persistScope(filename: String, entity: T) {
      val dir = File(options.cacheDirPath, SCOPE_CACHE).also { it.mkdirs() }
      val file = File(dir, filename)
      if (filename == BREADCRUMBS_FILENAME) {
        val queueFile = QueueFile.Builder(file).build()
        (entity as List<Breadcrumb>).forEach { crumb ->
          val baos = ByteArrayOutputStream()
          options.serializer.serialize(crumb, baos.writer())
          queueFile.add(baos.toByteArray())
        }
      } else {
        options.serializer.serialize(entity, file.writer())
      }
    }

    fun <T : Any> persistOptions(filename: String, entity: T) {
      val dir = File(options.cacheDirPath, OPTIONS_CACHE).also { it.mkdirs() }
      val file = File(dir, filename)
      options.serializer.serialize(entity, file.writer())
    }

    fun mockOutDeviceInfo() {
      ShadowBuild.setManufacturer("Google")
      ShadowBuild.setBrand("Pixel")
      ShadowBuild.setModel("Pixel 3XL")

      val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
      val shadowActivityManager = Shadow.extract<ShadowActivityManager>(activityManager)
      shadowActivityManager.setMemoryInfo(MemoryInfo().apply { totalMem = 2048 })
    }
  }

  private val fixture = Fixture()

  @BeforeTest
  fun `set up`() {
    DeviceInfoUtil.resetInstance()
    fixture.context = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun `when event is not backfillable, does not enrich`() {
    val processed = processEvent(Hint())

    assertNull(processed.platform)
    assertNull(processed.exceptions)
    assertTrue(processed.contexts.isEmpty)
  }

  @Test
  fun `when backfillable event is not enrichable, sets different mechanism`() {
    val hint = HintUtils.createWithTypeCheckHint(BackfillableHint(shouldEnrich = false))

    val processed = processEvent(hint)

    assertEquals("HistoricalAppExitInfo", processed.exceptions!![0].mechanism!!.type)
  }

  @Test
  fun `when backfillable event is not enrichable, sets platform`() {
    val hint = HintUtils.createWithTypeCheckHint(BackfillableHint(shouldEnrich = false))

    val processed = processEvent(hint)

    assertEquals(SentryBaseEvent.DEFAULT_PLATFORM, processed.platform)
  }

  @Test
  fun `when backfillable event is not enrichable, sets OS`() {
    val hint = HintUtils.createWithTypeCheckHint(BackfillableHint(shouldEnrich = false))

    ShadowBuild.setVersionRelease("7.8.123")
    val processed = processEvent(hint)

    assertEquals("7.8.123", processed.contexts.operatingSystem!!.version)
    assertEquals("Android", processed.contexts.operatingSystem!!.name)
  }

  @Test
  fun `when backfillable event already has OS, sets Android as main OS and existing as secondary`() {
    val hint = HintUtils.createWithTypeCheckHint(BackfillableHint(shouldEnrich = false))

    val linuxOs = OperatingSystem().apply { name = " Linux " }
    val processed = processEvent(hint) { contexts.setOperatingSystem(linuxOs) }

    assertSame(linuxOs, processed.contexts["os_linux"])
    assertEquals("Android", processed.contexts.operatingSystem!!.name)
  }

  @Test
  fun `when backfillable event already has OS without name, sets Android as main OS and existing with generated name`() {
    val hint = HintUtils.createWithTypeCheckHint(BackfillableHint(shouldEnrich = false))

    val osNoName = OperatingSystem().apply { version = "1.0" }
    val processed = processEvent(hint) { contexts.setOperatingSystem(osNoName) }

    assertSame(osNoName, processed.contexts["os_1"])
    assertEquals("Android", processed.contexts.operatingSystem!!.name)
  }

  @Test
  @Config(qualifiers = "w360dp-h640dp-xxhdpi")
  fun `when backfillable event is not enrichable, sets device`() {
    val hint = HintUtils.createWithTypeCheckHint(BackfillableHint(shouldEnrich = false))

    fixture.mockOutDeviceInfo()

    val processed = processEvent(hint)

    val device = processed.contexts.device!!
    assertEquals("Google", device.manufacturer)
    assertEquals("Pixel", device.brand)
    assertEquals("Pixel", device.family)
    assertEquals("Pixel 3XL", device.model)
    assertEquals(true, device.isSimulator)
    assertEquals(2048, device.memorySize)
    assertEquals(1080, device.screenWidthPixels)
    assertEquals(1920, device.screenHeightPixels)
    assertEquals(3.0f, device.screenDensity)
    assertEquals(480, device.screenDpi)
  }

  @Test
  fun `when backfillable event is enrichable, still sets static data`() {
    val hint = HintUtils.createWithTypeCheckHint(BackfillableHint())

    val processed = processEvent(hint)

    assertNotNull(processed.platform)
    assertFalse(processed.contexts.isEmpty())
  }

  @Test
  fun `when backfillable event is enrichable, backfills serialized scope data`() {
    val hint = HintUtils.createWithTypeCheckHint(BackfillableHint())

    val processed = processEvent(hint, populateScopeCache = true)

    // user
    assertEquals("bot", processed.user!!.username)
    assertEquals("bot@me.com", processed.user!!.id)
    assertEquals("{{auto}}", processed.user!!.ipAddress)
    // trace
    assertEquals("ui.load", processed.contexts.trace!!.operation)
    // tags
    assertEquals("two", processed.tags!!["one"])
    // breadcrumbs
    assertEquals("test", processed.breadcrumbs!![0].message)
    assertEquals("debug", processed.breadcrumbs!![0].type)
    assertEquals("navigation", processed.breadcrumbs!![1].type)
    assertEquals("to", processed.breadcrumbs!![1].data["to"])
    assertEquals("from", processed.breadcrumbs!![1].data["from"])
    // extras
    assertEquals(123, processed.extras!!["key"])
    // transaction
    assertEquals("TestActivity", processed.transaction)
    // fingerprint
    assertEquals(listOf("finger", "print"), processed.fingerprints)
    // level
    assertEquals(SentryLevel.INFO, processed.level)
    // request
    assertEquals("google.com", processed.request!!.url)
    assertEquals("GET", processed.request!!.method)
    // contexts
    assertEquals(1024, processed.contexts.response!!.bodySize)
    assertEquals("Google Chrome", processed.contexts.browser!!.name)
  }

  @Test
  fun `when backfillable event is enrichable, does not backfill user ip`() {
    val hint = HintUtils.createWithTypeCheckHint(BackfillableHint())
    val processed = processEvent(hint, isSendDefaultPii = false, populateScopeCache = true)
    assertNull(processed.user!!.ipAddress)
  }

  @Test
  fun `when backfillable event is enrichable, backfills serialized options data`() {
    val hint = HintUtils.createWithTypeCheckHint(BackfillableHint())

    val processed = processEvent(hint, populateOptionsCache = true)

    // release
    assertEquals("io.sentry.samples@1.2.0+232", processed.release)
    // proguard uuid
    assertEquals(DebugImage.PROGUARD, processed.debugMeta!!.images!![0].type)
    assertEquals("uuid", processed.debugMeta!!.images!![0].uuid)
    // sdk version
    assertEquals("sentry.java.android", processed.sdk!!.name)
    assertEquals("6.15.0", processed.sdk!!.version)
    // dist
    assertEquals("232", processed.dist)
    // environment
    assertEquals("debug", processed.environment)
    // app
    // robolectric defaults
    assertEquals("io.sentry.android.core.test", processed.contexts.app!!.appIdentifier)
    assertEquals("io.sentry.android.core.test", processed.contexts.app!!.appName)
    assertEquals("1.2.0", processed.contexts.app!!.appVersion)
    assertEquals("232", processed.contexts.app!!.appBuild)
    assertEquals(true, processed.contexts.app!!.inForeground)
    // tags
    assertEquals("tag", processed.tags!!["option"])
  }

  @Test
  fun `if release is in wrong format, does not crash and leaves app version and build empty`() {
    val hint = HintUtils.createWithTypeCheckHint(BackfillableHint())
    val original = SentryEvent()

    val processor = fixture.getSut(tmpDir)
    fixture.persistOptions(RELEASE_FILENAME, "io.sentry.samples")

    val processed = processor.process(original, hint)

    assertEquals("io.sentry.samples", processed!!.release)
    assertNull(processed.contexts.app!!.appVersion)
    assertNull(processed.contexts.app!!.appBuild)
  }

  @Test
  fun `if environment is not persisted, uses environment from options`() {
    val hint = HintUtils.createWithTypeCheckHint(BackfillableHint())

    val processed = processEvent(hint)

    assertEquals("release", processed.environment)
  }

  @Test
  fun `if dist is not persisted, backfills it from release`() {
    val hint = HintUtils.createWithTypeCheckHint(BackfillableHint())
    val original = SentryEvent()

    val processor = fixture.getSut(tmpDir)
    fixture.persistOptions(RELEASE_FILENAME, "io.sentry.samples@1.2.0+232")

    val processed = processor.process(original, hint)

    assertEquals("232", processed!!.dist)
  }

  @Test
  fun `merges user`() {
    val hint = HintUtils.createWithTypeCheckHint(BackfillableHint())

    val processed = processEvent(hint, populateScopeCache = true)

    assertEquals("bot@me.com", processed.user!!.id)
    assertEquals("bot", processed.user!!.username)
    assertEquals(IpAddressUtils.DEFAULT_IP_ADDRESS, processed.user!!.ipAddress)
  }

  @Test
  fun `uses installation id for user, if it has no id`() {
    val hint = HintUtils.createWithTypeCheckHint(BackfillableHint())
    val original = SentryEvent()

    val processor = fixture.getSut(tmpDir)
    fixture.persistOptions(USER_FILENAME, User())

    val processed = processor.process(original, hint)

    assertEquals(Installation.deviceId, processed!!.user!!.id)
  }

  @Test
  fun `when event has some fields set, does not override them`() {
    val hint = HintUtils.createWithTypeCheckHint(BackfillableHint())

    val processed =
      processEvent(hint, populateScopeCache = true, populateOptionsCache = true) {
        contexts.setDevice(
          Device().apply {
            brand = "Pixel"
            model = "3XL"
            memorySize = 4096
          }
        )
        platform = "NotAndroid"

        transaction = "MainActivity"
        level = DEBUG
        breadcrumbs = listOf(Breadcrumb.debug("test"))

        environment = "debug"
        release = "io.sentry.samples@1.1.0+220"
        debugMeta =
          DebugMeta().apply {
            images =
              listOf(
                DebugImage().apply {
                  type = DebugImage.PROGUARD
                  uuid = "uuid1"
                }
              )
          }
        user =
          User().apply {
            id = "42"
            ipAddress = "2.4.8.16"
          }
      }

    assertEquals("NotAndroid", processed.platform)
    assertEquals("Pixel", processed.contexts.device!!.brand)
    assertEquals("3XL", processed.contexts.device!!.model)
    assertEquals(4096, processed.contexts.device!!.memorySize)

    assertEquals("MainActivity", processed.transaction)
    assertEquals(DEBUG, processed.level)
    assertEquals(3, processed.breadcrumbs!!.size)
    assertEquals("debug", processed.breadcrumbs!![0].type)
    assertEquals("debug", processed.breadcrumbs!![1].type)
    assertEquals("navigation", processed.breadcrumbs!![2].type)

    assertEquals("debug", processed.environment)
    assertEquals("io.sentry.samples@1.1.0+220", processed.release)
    assertEquals("220", processed.contexts.app!!.appBuild)
    assertEquals("1.1.0", processed.contexts.app!!.appVersion)
    assertEquals(2, processed.debugMeta!!.images!!.size)
    assertEquals("uuid1", processed.debugMeta!!.images!![0].uuid)
    assertEquals("uuid", processed.debugMeta!!.images!![1].uuid)

    assertEquals("42", processed.user!!.id)
    assertEquals("2.4.8.16", processed.user!!.ipAddress)
  }

  @Test
  fun `when proguard uuid is not persisted, does not add to debug meta`() {
    val hint = HintUtils.createWithTypeCheckHint(BackfillableHint())

    val processed = processEvent(hint, populateOptionsCache = false)

    // proguard uuid
    assertTrue(processed.debugMeta!!.images!!.isEmpty())
  }

  @Test
  fun `populates exception from main thread`() {
    val hint = HintUtils.createWithTypeCheckHint(AbnormalExitHint())
    val stacktrace =
      SentryStackTrace().apply {
        frames =
          listOf(
            SentryStackFrame().apply {
              lineno = 777
              module = "io.sentry.samples.MainActivity"
              function = "run"
            }
          )
      }

    val processed =
      processEvent(hint) {
        threads =
          listOf(
            SentryThread().apply {
              name = "main"
              id = 13
              this.stacktrace = stacktrace
            }
          )
      }

    val exception = processed.exceptions!!.first()
    assertEquals(13, exception.threadId)
    assertEquals("AppExitInfo", exception.mechanism!!.type)
    assertEquals("ANR", exception.value)
    assertEquals("ApplicationNotResponding", exception.type)
    assertEquals("io.sentry.android.core", exception.module)
    assertEquals(true, exception.stacktrace!!.snapshot)
    val frame = exception.stacktrace!!.frames!!.first()
    assertEquals(777, frame.lineno)
    assertEquals("run", frame.function)
    assertEquals("io.sentry.samples.MainActivity", frame.module)
  }

  @Test
  fun `populates exception without stacktrace when there is no main thread in threads`() {
    val hint = HintUtils.createWithTypeCheckHint(AbnormalExitHint())

    val processed = processEvent(hint) { threads = listOf(SentryThread()) }

    val exception = processed.exceptions!!.first()
    assertEquals("AppExitInfo", exception.mechanism!!.type)
    assertEquals("ANR", exception.value)
    assertEquals("ApplicationNotResponding", exception.type)
    assertEquals("io.sentry.android.core", exception.module)
    assertNull(exception.stacktrace)
  }

  @Test
  fun `adds Background to the message when mechanism is anr_background`() {
    val hint = HintUtils.createWithTypeCheckHint(AbnormalExitHint(mechanism = "anr_background"))

    val processed =
      processEvent(hint) {
        threads =
          listOf(
            SentryThread().apply {
              name = "main"
              stacktrace = SentryStackTrace()
            }
          )
      }

    val exception = processed.exceptions!!.first()
    assertEquals("Background ANR", exception.value)
  }

  @Test
  fun `does not add Background to the message when mechanism is anr_foreground`() {
    val hint = HintUtils.createWithTypeCheckHint(AbnormalExitHint(mechanism = "anr_foreground"))

    val processed =
      processEvent(hint) {
        threads =
          listOf(
            SentryThread().apply {
              name = "main"
              stacktrace = SentryStackTrace()
            }
          )
      }

    val exception = processed.exceptions!!.first()
    assertEquals("ANR", exception.value)
  }

  @Test
  fun `sets default fingerprint to distinguish between background and foreground ANRs`() {
    val backgroundHint =
      HintUtils.createWithTypeCheckHint(AbnormalExitHint(mechanism = "anr_background"))
    val processedBackground = processEvent(backgroundHint, populateScopeCache = false)
    assertEquals(listOf("{{ default }}", "background-anr"), processedBackground.fingerprints)

    val foregroundHint =
      HintUtils.createWithTypeCheckHint(AbnormalExitHint(mechanism = "anr_foreground"))
    val processedForeground = processEvent(foregroundHint, populateScopeCache = false)
    assertEquals(listOf("{{ default }}", "foreground-anr"), processedForeground.fingerprints)
  }

  @Test
  fun `sets replayId when replay folder exists`() {
    val hint = HintUtils.createWithTypeCheckHint(BackfillableHint())
    val processor = fixture.getSut(tmpDir, populateScopeCache = true)
    val replayFolder =
      File(fixture.options.cacheDirPath, "replay_${Fixture.REPLAY_ID}").also { it.mkdirs() }

    val processed = processor.process(SentryEvent(), hint)!!

    assertEquals(Fixture.REPLAY_ID, processed.contexts[Contexts.REPLAY_ID].toString())
  }

  @Test
  fun `does not set replayId when replay folder does not exist and no sample rate persisted`() {
    val hint = HintUtils.createWithTypeCheckHint(BackfillableHint())
    val processor = fixture.getSut(tmpDir, populateScopeCache = true)
    val replayId1 = SentryId()
    val replayId2 = SentryId()

    val replayFolder1 = File(fixture.options.cacheDirPath, "replay_$replayId1").also { it.mkdirs() }
    val replayFolder2 = File(fixture.options.cacheDirPath, "replay_$replayId2").also { it.mkdirs() }

    val processed = processor.process(SentryEvent(), hint)!!

    assertNull(processed.contexts[Contexts.REPLAY_ID])
  }

  @Test
  fun `does not set replayId when replay folder does not exist and not sampled`() {
    val hint = HintUtils.createWithTypeCheckHint(BackfillableHint())
    val processor =
      fixture.getSut(
        tmpDir,
        populateScopeCache = true,
        populateOptionsCache = true,
        replayErrorSampleRate = 0.0,
      )
    val replayId1 = SentryId()
    val replayId2 = SentryId()

    val replayFolder1 = File(fixture.options.cacheDirPath, "replay_$replayId1").also { it.mkdirs() }
    val replayFolder2 = File(fixture.options.cacheDirPath, "replay_$replayId2").also { it.mkdirs() }

    val processed = processor.process(SentryEvent(), hint)!!

    assertNull(processed.contexts[Contexts.REPLAY_ID])
  }

  @Test
  fun `set replayId of the last modified folder`() {
    val hint = HintUtils.createWithTypeCheckHint(BackfillableHint())
    val processor =
      fixture.getSut(
        tmpDir,
        populateScopeCache = true,
        populateOptionsCache = true,
        replayErrorSampleRate = 1.0,
      )
    val replayId1 = SentryId()
    val replayId2 = SentryId()

    val replayFolder1 = File(fixture.options.cacheDirPath, "replay_$replayId1").also { it.mkdirs() }
    val replayFolder2 = File(fixture.options.cacheDirPath, "replay_$replayId2").also { it.mkdirs() }
    replayFolder1.setLastModified(1000)
    replayFolder2.setLastModified(500)

    val processed = processor.process(SentryEvent(), hint)!!

    assertEquals(replayId1.toString(), processed.contexts[Contexts.REPLAY_ID].toString())
    assertEquals(
      replayId1.toString(),
      fixture.options
        .findPersistingScopeObserver()
        ?.read(fixture.options, REPLAY_FILENAME, String::class.java),
    )
  }

  private fun processEvent(
    hint: Hint,
    populateScopeCache: Boolean = false,
    populateOptionsCache: Boolean = false,
    isSendDefaultPii: Boolean = true,
    configureEvent: SentryEvent.() -> Unit = {},
  ): SentryEvent {
    val original = SentryEvent().apply(configureEvent)

    val processor =
      fixture.getSut(
        tmpDir,
        populateScopeCache = populateScopeCache,
        populateOptionsCache = populateOptionsCache,
        isSendDefaultPii = isSendDefaultPii,
      )
    return processor.process(original, hint)!!
  }

  internal class AbnormalExitHint(val mechanism: String? = null) : AbnormalExit, Backfillable {
    override fun mechanism(): String? = mechanism

    override fun ignoreCurrentThread(): Boolean = false

    override fun timestamp(): Long? = null

    override fun shouldEnrich(): Boolean = true
  }

  internal class BackfillableHint(private val shouldEnrich: Boolean = true) : Backfillable {
    override fun shouldEnrich(): Boolean = shouldEnrich
  }
}

package io.sentry.android.replay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat.JPEG
import android.graphics.Bitmap.Config.ARGB_8888
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.EventProcessor
import io.sentry.Hint
import io.sentry.Sentry
import io.sentry.SentryReplayEvent
import io.sentry.android.core.performance.AppStartMetrics
import io.sentry.android.replay.ReplayCache.Companion.ONGOING_SEGMENT
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_BIT_RATE
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_FRAME_RATE
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_HEIGHT
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_ID
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_REPLAY_TYPE
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_TIMESTAMP
import io.sentry.android.replay.ReplayCache.Companion.SEGMENT_KEY_WIDTH
import io.sentry.android.replay.util.ReplayShadowMediaCodec
import io.sentry.cache.PersistingOptionsObserver.ENVIRONMENT_FILENAME
import io.sentry.cache.PersistingOptionsObserver.OPTIONS_CACHE
import io.sentry.cache.PersistingOptionsObserver.RELEASE_FILENAME
import io.sentry.cache.PersistingScopeObserver.REPLAY_FILENAME
import io.sentry.cache.PersistingScopeObserver.SCOPE_CACHE
import io.sentry.protocol.SentryId
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilNotNull
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [30], shadows = [ReplayShadowMediaCodec::class])
class PreviousReplayIntegrationTest {
  @get:Rule val tmpDir = TemporaryFolder()

  private lateinit var context: Context

  @BeforeTest
  fun `set up`() {
    ReplayShadowMediaCodec.framesToEncode = 1
    Sentry.close()
    AppStartMetrics.getInstance().clear()
    context = ApplicationProvider.getApplicationContext()
  }

  // Replays recorded by older SDK versions don't store their metadata, so the previous replay is
  // backfilled from the options cache. That only works while ReplayIntegration reads the cache
  // before Sentry.init overwrites it with the current options.
  @Test
  fun `previous replay without stored metadata is backfilled from the previous run options`() {
    val cacheDir = tmpDir.newFolder().absolutePath
    val replayId = SentryId()
    val capturedReplay = AtomicReference<SentryReplayEvent>()

    initForTest(context) {
      it.dsn = "https://key@sentry.io/123"
      it.cacheDirPath = cacheDir
      it.release = "io.sentry.sample@2.0.0+2"
      it.environment = "current-env"
      it.addEventProcessor(
        object : EventProcessor {
          override fun process(event: SentryReplayEvent, hint: Hint): SentryReplayEvent {
            capturedReplay.set(event)
            return event
          }
        }
      )

      // have to do it after the cacheDir is set to options, because it adds a dsn hash after
      val optionsCache = File(it.cacheDirPath!!, OPTIONS_CACHE).also { dir -> dir.mkdirs() }
      File(optionsCache, RELEASE_FILENAME).writeText("\"io.sentry.sample@1.0.0+1\"")
      File(optionsCache, ENVIRONMENT_FILENAME).writeText("\"previous-env\"")

      val scopeCache = File(it.cacheDirPath!!, SCOPE_CACHE).also { dir -> dir.mkdirs() }
      File(scopeCache, REPLAY_FILENAME).writeText("\"$replayId\"")

      val replayFolder = File(it.cacheDirPath!!, "replay_$replayId").also { dir -> dir.mkdirs() }
      File(replayFolder, ONGOING_SEGMENT)
        .writeText(
          """
          $SEGMENT_KEY_HEIGHT=912
          $SEGMENT_KEY_WIDTH=416
          $SEGMENT_KEY_FRAME_RATE=1
          $SEGMENT_KEY_BIT_RATE=75000
          $SEGMENT_KEY_ID=0
          $SEGMENT_KEY_TIMESTAMP=2024-07-11T10:25:21.454Z
          $SEGMENT_KEY_REPLAY_TYPE=BUFFER
          """
            .trimIndent()
        )
      File(replayFolder, "1720693523997.jpg").outputStream().use { os ->
        Bitmap.createBitmap(1, 1, ARGB_8888).compress(JPEG, 80, os)
        os.flush()
      }
    }

    val replay = await.untilNotNull { capturedReplay.get() }
    assertEquals(replayId, replay.replayId)
    assertEquals("io.sentry.sample@1.0.0+1", replay.release)
    assertEquals("previous-env", replay.environment)
  }
}

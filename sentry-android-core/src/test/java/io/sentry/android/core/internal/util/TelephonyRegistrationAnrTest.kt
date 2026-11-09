package io.sentry.android.core.internal.util

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.sentry.IScopes
import io.sentry.android.replay.ReplayIntegration
import io.sentry.transport.CurrentDateProvider
import java.util.concurrent.CountDownLatch
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.concurrent.thread
import kotlin.test.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config

/** Cross-layer coverage of replay registration while Android telephony registration is pending. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.S])
class TelephonyRegistrationAnrTest {
  @Test
  fun `replay registration completes during telephony registration when sampling is disabled`() {
    val fixture = Fixture.create(sampleRate = 0.0)
    fixture.use {
      val completedBeforeRelease = fixture.releaseTelephonyAfterRegistration()

      fixture.replay.register(mock<IScopes>(), fixture.connection.options)
      fixture.registrationCompleted.countDown()

      assertThat(completedBeforeRelease.get(5, SECONDS)).isTrue()
    }
  }

  @Test
  fun `replay registration completes during telephony registration when sampling is enabled`() {
    val fixture = Fixture.create(sampleRate = 1.0)
    fixture.use {
      val completedBeforeRelease = fixture.releaseTelephonyAfterRegistration()

      fixture.replay.register(mock<IScopes>(), fixture.connection.options)
      fixture.registrationCompleted.countDown()

      assertThat(completedBeforeRelease.get(5, SECONDS)).isTrue()
    }
  }

  private class Fixture private constructor(sampleRate: Double) : AutoCloseable {
    val connection = ConnectionStatusProviderFixture.create()
    val registrationCompleted = CountDownLatch(1)
    val replay = ReplayIntegration(connection.context, CurrentDateProvider.getInstance())

    private val telephonyCall = PausedCall()

    init {
      connection.options.sessionReplay.sessionSampleRate = sampleRate
      connection.options.sessionReplay.onErrorSampleRate = sampleRate
      doAnswer {
          telephonyCall.pause()
          null
        }
        .whenever(connection.telephonyManager)
        .registerTelephonyCallback(any(), any())
      connection.runNextTask()
      telephonyCall.awaitEntry()
    }

    fun releaseTelephonyAfterRegistration(): FutureTask<Boolean> {
      val completed = FutureTask {
        try {
          registrationCompleted.await(2, SECONDS)
        } finally {
          telephonyCall.close()
        }
      }
      thread(name = "replay-registration-watchdog") { completed.run() }
      return completed
    }

    override fun close() {
      telephonyCall.close()
      replay.close()
      connection.close()
    }

    companion object {
      fun create(sampleRate: Double): Fixture = Fixture(sampleRate)
    }
  }
}

package io.sentry.android.core

import android.app.Activity
import android.util.SparseIntArray
import androidx.core.app.FrameMetricsAggregator
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.protocol.SentryId
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActivityFramesTrackerTest {

    private class Fixture {
        val aggregator = mock<FrameMetricsAggregator>()
        val activity = mock<Activity>()
        val sentryId = SentryId()

        fun getSut(): ActivityFramesTracker {
            return ActivityFramesTracker(aggregator)
        }
    }
    private val fixture = Fixture()

    @Test
    fun `sets total frames`() {
        val sut = fixture.getSut()
        val array = getArray()

        whenever(fixture.aggregator.remove(any())).thenReturn(array)

        sut.setMetrics(fixture.activity, fixture.sentryId)

        val metrics = sut.takeMetrics(fixture.sentryId)
        val totalFrames = metrics!!["frames_total"]

        assertEquals(totalFrames!!.value, 1f)
    }

    @Test
    fun `sets frozen frames`() {
        val sut = fixture.getSut()
        val array = getArray(frameTime = 705, numFrames = 5)

        whenever(fixture.aggregator.remove(any())).thenReturn(array)

        sut.setMetrics(fixture.activity, fixture.sentryId)

        val metrics = sut.takeMetrics(fixture.sentryId)
        val frozenFrames = metrics!!["frames_frozen"]

        assertEquals(frozenFrames!!.value, 5f)
    }

    @Test
    fun `sets slow frames`() {
        val sut = fixture.getSut()
        val array = getArray(frameTime = 20, numFrames = 5)

        whenever(fixture.aggregator.remove(any())).thenReturn(array)

        sut.setMetrics(fixture.activity, fixture.sentryId)

        val metrics = sut.takeMetrics(fixture.sentryId)
        val slowFrames = metrics!!["frames_slow"]

        assertEquals(slowFrames!!.value, 5f)
    }

    @Test
    fun `sets slow and frozen frames`() {
        val sut = fixture.getSut()
        val arrayAll = SparseIntArray()
        arrayAll.put(1, 100)
        arrayAll.put(20, 5)
        arrayAll.put(705, 6)
        val array = arrayOf(arrayAll)

        whenever(fixture.aggregator.remove(any())).thenReturn(array)

        sut.setMetrics(fixture.activity, fixture.sentryId)

        val metrics = sut.takeMetrics(fixture.sentryId)

        val totalFrames = metrics!!["frames_total"]
        assertEquals(totalFrames!!.value, 111f)

        val frozenFrames = metrics["frames_frozen"]
        assertEquals(frozenFrames!!.value, 6f)

        val slowFrames = metrics["frames_slow"]
        assertEquals(slowFrames!!.value, 5f)
    }

    private fun getArray(frameTime: Int = 1, numFrames: Int = 1): Array<SparseIntArray?> {
        val totalArray = SparseIntArray()
        totalArray.put(frameTime, numFrames)
        return arrayOf(totalArray)
    }
}

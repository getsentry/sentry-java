package io.sentry.android.core

import android.app.Activity
import android.util.SparseIntArray
import androidx.core.app.FrameMetricsAggregator
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.protocol.SentryId
import io.sentry.util.LoadClass
import org.junit.runner.RunWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class ActivityFramesTrackerTest {

    private class Fixture {
        val aggregator = mock<FrameMetricsAggregator>()
        val activity = mock<Activity>()
        val sentryId = SentryId()
        val loadClass = mock<LoadClass>()

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

    @Test
    fun `do not set metrics if values are zeroes`() {
        val sut = fixture.getSut()
        val arrayAll = SparseIntArray()
        arrayAll.put(0, 0)
        val array = arrayOf(arrayAll)

        whenever(fixture.aggregator.remove(any())).thenReturn(array)

        sut.setMetrics(fixture.activity, fixture.sentryId)

        val metrics = sut.takeMetrics(fixture.sentryId)

        assertNull(metrics)
    }

    @Test
    fun `addActivity does not throw if no AndroidX`() {
        whenever(fixture.loadClass.loadClass(any())).thenThrow(ClassNotFoundException())
        val sut = ActivityFramesTracker(fixture.loadClass)

        sut.addActivity(fixture.activity)
    }

    @Test
    fun `setMetrics does not throw if no AndroidX`() {
        whenever(fixture.loadClass.loadClass(any())).thenThrow(ClassNotFoundException())
        val sut = ActivityFramesTracker(fixture.loadClass)

        sut.setMetrics(fixture.activity, fixture.sentryId)
    }

    @Test
    fun `setMetrics does not throw if Activity is not added`() {
        whenever(fixture.aggregator.remove(any())).thenThrow(IllegalArgumentException())
        val sut = ActivityFramesTracker(fixture.loadClass)

        sut.setMetrics(fixture.activity, fixture.sentryId)
    }

    @Test
    fun `stop does not throw if no AndroidX`() {
        whenever(fixture.loadClass.loadClass(any())).thenThrow(ClassNotFoundException())
        val sut = ActivityFramesTracker(fixture.loadClass)

        sut.stop()
    }

    @Test
    fun `takeMetrics returns null if no AndroidX`() {
        whenever(fixture.loadClass.loadClass(any())).thenThrow(ClassNotFoundException())
        val sut = ActivityFramesTracker(fixture.loadClass)

        assertNull(sut.takeMetrics(fixture.sentryId))
    }

    private fun getArray(frameTime: Int = 1, numFrames: Int = 1): Array<SparseIntArray?> {
        val totalArray = SparseIntArray()
        totalArray.put(frameTime, numFrames)
        return arrayOf(totalArray)
    }
}

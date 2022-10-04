package io.sentry.android.core

import android.app.Activity
import android.util.SparseIntArray
import androidx.core.app.FrameMetricsAggregator
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.ILogger
import io.sentry.protocol.SentryId
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

        whenever(fixture.aggregator.metrics).thenReturn(emptyArray(), array)

        sut.addActivity(fixture.activity)
        sut.setMetrics(fixture.activity, fixture.sentryId)

        val metrics = sut.takeMetrics(fixture.sentryId)
        val totalFrames = metrics!!["frames_total"]

        assertEquals(totalFrames!!.value, 1f)
        assertEquals(totalFrames.unit, "none")
    }

    @Test
    fun `sets frozen frames`() {
        val sut = fixture.getSut()
        val arrayAtStart = getArray(frameTime = 705, numFrames = 5)
        val arrayAtEnd = getArray(frameTime = 705, numFrames = 10)

        whenever(fixture.aggregator.metrics).thenReturn(arrayAtStart, arrayAtEnd)

        sut.addActivity(fixture.activity)
        sut.setMetrics(fixture.activity, fixture.sentryId)

        val metrics = sut.takeMetrics(fixture.sentryId)
        val frozenFrames = metrics!!["frames_frozen"]

        assertEquals(frozenFrames!!.value, 5f)
        assertEquals(frozenFrames.unit, "none")
    }

    @Test
    fun `sets slow frames`() {
        val sut = fixture.getSut()
        val arrayAtStart = getArray(frameTime = 20, numFrames = 5)
        val arrayAtEnd = getArray(frameTime = 20, numFrames = 10)

        whenever(fixture.aggregator.metrics).thenReturn(arrayAtStart, arrayAtEnd)

        sut.addActivity(fixture.activity)
        sut.setMetrics(fixture.activity, fixture.sentryId)

        val metrics = sut.takeMetrics(fixture.sentryId)
        val slowFrames = metrics!!["frames_slow"]

        assertEquals(slowFrames!!.value, 5f)
        assertEquals(slowFrames.unit, "none")
    }

    @Test
    fun `sets slow and frozen frames`() {
        val sut = fixture.getSut()
        val arrayAll = SparseIntArray()
        arrayAll.put(1, 100)
        arrayAll.put(20, 5)
        arrayAll.put(705, 6)
        val array = arrayOf(arrayAll)

        whenever(fixture.aggregator.metrics).thenReturn(emptyArray(), array)

        sut.addActivity(fixture.activity)
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
    fun `sets slow and frozen frames even if start was null`() {
        val sut = fixture.getSut()
        val arrayAll = SparseIntArray()
        arrayAll.put(1, 100)
        arrayAll.put(20, 5)
        arrayAll.put(705, 6)
        val array = arrayOf(arrayAll)

        whenever(fixture.aggregator.metrics).thenReturn(null, array)

        sut.addActivity(fixture.activity)
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
    fun `different activities have separate counts - even when called out of order`() {
        val sut = fixture.getSut()
        val array0 = SparseIntArray().also {
            it.put(1, 100)
            it.put(20, 5)
            it.put(705, 6)
        }.let { arrayOf(it) }
        val array1 = SparseIntArray().also {
            it.put(1, 110)
            it.put(20, 6)
            it.put(705, 7)
        }.let { arrayOf(it) }
        val array2 = SparseIntArray().also {
            it.put(1, 115)
            it.put(20, 8)
            it.put(705, 9)
        }.let { arrayOf(it) }
        val array3 = SparseIntArray().also {
            it.put(1, 135)
            it.put(20, 11)
            it.put(705, 12)
        }.let { arrayOf(it) }

        val otherActivity = mock<Activity>()
        val otherSentryId = SentryId()

        whenever(fixture.aggregator.metrics).thenReturn(array0, array1, array2, array3)

        sut.addActivity(fixture.activity)
        sut.addActivity(otherActivity)
        sut.setMetrics(fixture.activity, fixture.sentryId)
        sut.setMetrics(otherActivity, otherSentryId)

        val metrics = sut.takeMetrics(fixture.sentryId)
        val otherMetrics = sut.takeMetrics(otherSentryId)

        val totalFrames = metrics!!["frames_total"]
        assertEquals(totalFrames!!.value, 21f) // 15 + 3 + 3

        val frozenFrames = metrics["frames_frozen"]
        assertEquals(frozenFrames!!.value, 3f)

        val slowFrames = metrics["frames_slow"]
        assertEquals(slowFrames!!.value, 3f)

        val totalFramesOther = otherMetrics!!["frames_total"]
        assertEquals(totalFramesOther!!.value, 35f) // 25 + 5 + 5

        val frozenFramesOther = otherMetrics["frames_frozen"]
        assertEquals(frozenFramesOther!!.value, 5f)

        val slowFramesOther = otherMetrics["frames_slow"]
        assertEquals(slowFramesOther!!.value, 5f)
    }

    @Test
    fun `same activity can be used again later on`() {
        val sut = fixture.getSut()
        val array0 = SparseIntArray().also {
            it.put(1, 100)
            it.put(20, 5)
            it.put(705, 6)
        }.let { arrayOf(it) }
        val array1 = SparseIntArray().also {
            it.put(1, 110)
            it.put(20, 6)
            it.put(705, 7)
        }.let { arrayOf(it) }
        val array2 = SparseIntArray().also {
            it.put(1, 115)
            it.put(20, 8)
            it.put(705, 9)
        }.let { arrayOf(it) }
        val array3 = SparseIntArray().also {
            it.put(1, 135)
            it.put(20, 11)
            it.put(705, 12)
        }.let { arrayOf(it) }
        val secondSentryId = SentryId()

        whenever(fixture.aggregator.metrics).thenReturn(array0, array1, array2, array3)

        sut.addActivity(fixture.activity)
        sut.setMetrics(fixture.activity, fixture.sentryId)
        sut.addActivity(fixture.activity)
        sut.setMetrics(fixture.activity, secondSentryId)

        val metrics = sut.takeMetrics(fixture.sentryId)
        val secondMetrics = sut.takeMetrics(secondSentryId)

        val totalFrames = metrics!!["frames_total"]
        assertEquals(totalFrames!!.value, 12f) // 10 + 1 + 1

        val frozenFrames = metrics["frames_frozen"]
        assertEquals(frozenFrames!!.value, 1f)

        val slowFrames = metrics["frames_slow"]
        assertEquals(slowFrames!!.value, 1f)

        val totalFramesSecond = secondMetrics!!["frames_total"]
        assertEquals(totalFramesSecond!!.value, 26f) // 20 + 3 + 3

        val frozenFramesSecond = secondMetrics["frames_frozen"]
        assertEquals(frozenFramesSecond!!.value, 3f)

        val slowFramesSecond = secondMetrics["frames_slow"]
        assertEquals(slowFramesSecond!!.value, 3f)
    }

    @Test
    fun `do not set metrics if values are zeroes`() {
        val sut = fixture.getSut()
        val arrayAll = SparseIntArray()
        arrayAll.put(0, 0)
        val array = arrayOf(arrayAll)

        whenever(fixture.aggregator.metrics).thenReturn(array)

        sut.addActivity(fixture.activity)
        sut.setMetrics(fixture.activity, fixture.sentryId)

        val metrics = sut.takeMetrics(fixture.sentryId)

        assertNull(metrics)
    }

    @Test
    fun `do not set metrics if values are null`() {
        val sut = fixture.getSut()

        whenever(fixture.aggregator.metrics).thenReturn(null)

        sut.addActivity(fixture.activity)
        sut.setMetrics(fixture.activity, fixture.sentryId)

        val metrics = sut.takeMetrics(fixture.sentryId)

        assertNull(metrics)
    }

    @Test
    fun `addActivity does not throw if no AndroidX`() {
        whenever(fixture.loadClass.isClassAvailable(any(), any<ILogger>())).thenReturn(false)
        val sut = ActivityFramesTracker(fixture.loadClass)

        sut.addActivity(fixture.activity)
    }

    @Test
    fun `setMetrics does not throw if no AndroidX`() {
        whenever(fixture.loadClass.isClassAvailable(any(), any<ILogger>())).thenReturn(false)
        val sut = ActivityFramesTracker(fixture.loadClass)

        sut.setMetrics(fixture.activity, fixture.sentryId)
    }

    @Test
    fun `addActivity and setMetrics combined do not throw if no AndroidX`() {
        whenever(fixture.loadClass.isClassAvailable(any(), any<ILogger>())).thenReturn(false)
        val sut = ActivityFramesTracker(fixture.loadClass)

        sut.addActivity(fixture.activity)
        sut.setMetrics(fixture.activity, fixture.sentryId)
    }

    @Test
    fun `setMetrics does not throw if Activity is not added`() {
        whenever(fixture.aggregator.metrics).thenThrow(IllegalArgumentException())
        val sut = ActivityFramesTracker(fixture.loadClass)

        sut.setMetrics(fixture.activity, fixture.sentryId)
    }

    @Test
    fun `stop does not throw if no AndroidX`() {
        whenever(fixture.loadClass.isClassAvailable(any(), any<ILogger>())).thenReturn(false)
        val sut = ActivityFramesTracker(fixture.loadClass)

        sut.stop()
    }

    @Test
    fun `stop resets frameMetricsAggregator`() {
        val sut = fixture.getSut()

        sut.stop()

        verify(fixture.aggregator).reset()
    }

    @Test
    fun `takeMetrics returns null if no AndroidX`() {
        whenever(fixture.loadClass.isClassAvailable(any(), any<ILogger>())).thenReturn(false)
        val sut = ActivityFramesTracker(fixture.loadClass)

        assertNull(sut.takeMetrics(fixture.sentryId))
    }

    private fun getArray(frameTime: Int = 1, numFrames: Int = 1): Array<SparseIntArray?> {
        val totalArray = SparseIntArray()
        totalArray.put(frameTime, numFrames)
        return arrayOf(totalArray)
    }
}

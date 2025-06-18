package io.sentry

import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals

class SentryNanotimeDateTest {
    @Test
    fun `doubleValue only offers ms precision`() {
        val date = SentryNanotimeDate(Date(1672742031123), 123456789)
        assertEquals(1672742031123000000L, date.nanoTimestamp())
    }

    @Test
    fun `laterDateNanosByDiff offers ns precision`() {
        val startDate = SentryNanotimeDate(Date(1672742031123), 456788)
        val finishDate = SentryNanotimeDate(Date(1672742031123), 456789)
        val dateInSeconds = startDate.laterDateNanosTimestampByDiff(finishDate)
        assertEquals(1672742031123000001L, dateInSeconds)
    }

    /**
     * Despite {@link SentryLongDate} being able to provide high precision, there's no way
     * to calculate a precise diff with {@link SentryNanotimeDate} as System.nanoTime() can
     * only be used to measure elapsed time - it is not a wall-clock time.
     */
    @Test
    fun `laterDateNanosByDiff with SentryLongDate gives ms precision`() {
        val startDate = SentryNanotimeDate(Date(1672742031123), 456789)
        val finishDate = SentryLongDate(61633553039)
        val dateInSeconds = startDate.laterDateNanosTimestampByDiff(finishDate)
        assertEquals(1672742031123000000L, dateInSeconds)
    }

    // compareTo()

    @Test
    fun `compareTo() with equal dates returns 0`() {
        val date1 = SentryNanotimeDate(Date(1672742031123), 456789)
        val date2 = SentryNanotimeDate(Date(1672742031123), 456789)
        assertEquals(0, date1.compareTo(date2))
    }

    @Test
    fun `compareTo() returns -1 for earlier ns`() {
        val date1 = SentryNanotimeDate(Date(1672742031123), 456788)
        val date2 = SentryNanotimeDate(Date(1672742031123), 456789)
        assertEquals(-1, date1.compareTo(date2))
    }

    @Test
    fun `compareTo() returns 1 for later ns`() {
        val date1 = SentryNanotimeDate(Date(1672742031123), 456789)
        val date2 = SentryNanotimeDate(Date(1672742031123), 456788)
        assertEquals(1, date1.compareTo(date2))
    }

    @Test
    fun `compareTo() returns -1 for earlier date`() {
        val date1 = SentryNanotimeDate(Date(1672742030123), 456789)
        val date2 = SentryNanotimeDate(Date(1672742031123), 456789)
        assertEquals(-1, date1.compareTo(date2))
    }

    @Test
    fun `compareTo() returns 1 for later date`() {
        val date1 = SentryNanotimeDate(Date(1672742031123), 456789)
        val date2 = SentryNanotimeDate(Date(1672742030123), 456789)
        assertEquals(1, date1.compareTo(date2))
    }
}

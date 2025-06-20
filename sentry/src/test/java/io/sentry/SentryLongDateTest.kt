package io.sentry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SentryLongDateTest {
  @Test
  fun `nanos() offers ns precision`() {
    val date = SentryLongDate(1672742031123456789)
    assertEquals(1672742031123456789L, date.nanoTimestamp())
  }

  @Test
  fun `laterDateNanosByDiff() offers ns precision`() {
    val date1 = SentryLongDate(1672742031123456788)
    val date2 = SentryLongDate(1672742031123456789)
    assertEquals(1672742031123456789L, date1.laterDateNanosTimestampByDiff(date2))
  }

  @Test
  fun `laterDateNanosByDiff() offers ns precision reverse`() {
    val date1 = SentryLongDate(1672742031123456789)
    val date2 = SentryLongDate(1672742031123456788)
    assertEquals(1672742031123456789L, date1.laterDateNanosTimestampByDiff(date2))
  }

  // compareTo()

  @Test
  fun `compareTo() with equal dates returns 0`() {
    val date1 = SentryLongDate(1672742031123456789)
    val date2 = SentryLongDate(1672742031123456789)
    assertEquals(0, 1672742031123456789.compareTo(1672742031123456789))
    assertEquals(0, date1.compareTo(date2))
  }

  @Test
  fun `compareTo() returns -1 for earlier date`() {
    val date1 = SentryLongDate(1672742031123456788)
    val date2 = SentryLongDate(1672742031123456789)
    assertEquals(-1, 1672742031123456788.compareTo(1672742031123456789))
    assertEquals(-1, date1.compareTo(date2))
  }

  @Test
  fun `compareTo() returns 1 for later date`() {
    val date1 = SentryLongDate(1672742031123456789)
    val date2 = SentryLongDate(1672742031123456788)
    assertEquals(1, 1672742031123456789.compareTo(1672742031123456788))
    assertEquals(1, date1.compareTo(date2))
  }

  // isBefore()
  @Test
  fun `isBefore() returns true if for an earlier date`() {
    val date1 = SentryLongDate(0)
    val date2 = SentryLongDate(1)
    assertTrue(date1.isBefore(date2))
  }

  @Test
  fun `isBefore() returns false for the same date`() {
    val date1 = SentryLongDate(1)
    val date2 = SentryLongDate(1)
    assertFalse(date1.isBefore(date2))
  }

  @Test
  fun `isBefore() returns false for a later date`() {
    val date1 = SentryLongDate(2)
    val date2 = SentryLongDate(1)
    assertFalse(date1.isBefore(date2))
  }

  // isAfter()
  @Test
  fun `isAfter() returns true if for a later date`() {
    val date1 = SentryLongDate(2)
    val date2 = SentryLongDate(1)
    assertTrue(date1.isAfter(date2))
  }

  @Test
  fun `isAfter() returns false for the same date`() {
    val date1 = SentryLongDate(1)
    val date2 = SentryLongDate(1)
    assertFalse(date1.isAfter(date2))
  }

  @Test
  fun `isAfter() returns false for a sooner date`() {
    val date1 = SentryLongDate(1)
    val date2 = SentryLongDate(2)
    assertFalse(date1.isAfter(date2))
  }
}

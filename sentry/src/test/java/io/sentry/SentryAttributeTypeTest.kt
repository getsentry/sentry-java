package io.sentry

import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertEquals

class SentryAttributeTypeTest {

  @Test
  fun `inferFrom returns BOOLEAN for Boolean`() {
    assertEquals(SentryAttributeType.BOOLEAN, SentryAttributeType.inferFrom(true))
    assertEquals(SentryAttributeType.BOOLEAN, SentryAttributeType.inferFrom(false))
  }

  @Test
  fun `inferFrom returns INTEGER for Integer`() {
    assertEquals(SentryAttributeType.INTEGER, SentryAttributeType.inferFrom(42))
  }

  @Test
  fun `inferFrom returns INTEGER for Long`() {
    assertEquals(SentryAttributeType.INTEGER, SentryAttributeType.inferFrom(42L))
  }

  @Test
  fun `inferFrom returns INTEGER for Short`() {
    assertEquals(SentryAttributeType.INTEGER, SentryAttributeType.inferFrom(42.toShort()))
  }

  @Test
  fun `inferFrom returns INTEGER for Byte`() {
    assertEquals(SentryAttributeType.INTEGER, SentryAttributeType.inferFrom(42.toByte()))
  }

  @Test
  fun `inferFrom returns INTEGER for BigInteger`() {
    assertEquals(SentryAttributeType.INTEGER, SentryAttributeType.inferFrom(BigInteger.valueOf(42)))
  }

  @Test
  fun `inferFrom returns INTEGER for AtomicInteger`() {
    assertEquals(SentryAttributeType.INTEGER, SentryAttributeType.inferFrom(AtomicInteger(42)))
  }

  @Test
  fun `inferFrom returns INTEGER for AtomicLong`() {
    assertEquals(SentryAttributeType.INTEGER, SentryAttributeType.inferFrom(AtomicLong(42)))
  }

  @Test
  fun `inferFrom returns DOUBLE for Double`() {
    assertEquals(SentryAttributeType.DOUBLE, SentryAttributeType.inferFrom(3.14))
  }

  @Test
  fun `inferFrom returns DOUBLE for Float`() {
    assertEquals(SentryAttributeType.DOUBLE, SentryAttributeType.inferFrom(3.14f))
  }

  @Test
  fun `inferFrom returns DOUBLE for BigDecimal`() {
    assertEquals(
      SentryAttributeType.DOUBLE,
      SentryAttributeType.inferFrom(BigDecimal.valueOf(3.14)),
    )
  }

  @Test
  fun `inferFrom returns STRING for String`() {
    assertEquals(SentryAttributeType.STRING, SentryAttributeType.inferFrom("hello"))
  }

  @Test
  fun `inferFrom returns STRING for null`() {
    assertEquals(SentryAttributeType.STRING, SentryAttributeType.inferFrom(null))
  }

  @Test
  fun `inferFrom returns ARRAY for List of Strings`() {
    assertEquals(SentryAttributeType.ARRAY, SentryAttributeType.inferFrom(listOf("a", "b")))
  }

  @Test
  fun `inferFrom returns ARRAY for List of Integers`() {
    assertEquals(SentryAttributeType.ARRAY, SentryAttributeType.inferFrom(listOf(1, 2, 3)))
  }

  @Test
  fun `inferFrom returns ARRAY for Set of Booleans`() {
    assertEquals(SentryAttributeType.ARRAY, SentryAttributeType.inferFrom(setOf(true, false)))
  }

  @Test
  fun `inferFrom returns ARRAY for String array`() {
    assertEquals(SentryAttributeType.ARRAY, SentryAttributeType.inferFrom(arrayOf("a", "b")))
  }

  @Test
  fun `inferFrom returns ARRAY for int array`() {
    assertEquals(SentryAttributeType.ARRAY, SentryAttributeType.inferFrom(intArrayOf(1, 2)))
  }

  @Test
  fun `inferFrom returns ARRAY for empty list`() {
    assertEquals(SentryAttributeType.ARRAY, SentryAttributeType.inferFrom(emptyList<String>()))
  }

  @Test
  fun `inferFrom returns ARRAY for mixed-type list`() {
    assertEquals(SentryAttributeType.ARRAY, SentryAttributeType.inferFrom(listOf("a", 1, true)))
  }
}

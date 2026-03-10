package io.sentry.spring7.cache

import io.sentry.IScopes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager

class SentryCacheManagerWrapperTest {

  private val scopes: IScopes = mock()
  private val delegate: CacheManager = mock()

  @Test
  fun `getCache wraps returned cache in SentryCacheWrapper`() {
    val cache = mock<Cache>()
    whenever(delegate.getCache("test")).thenReturn(cache)

    val wrapper = SentryCacheManagerWrapper(delegate, scopes)
    val result = wrapper.getCache("test")

    assertTrue(result is SentryCacheWrapper)
  }

  @Test
  fun `getCache returns null when delegate returns null`() {
    whenever(delegate.getCache("missing")).thenReturn(null)

    val wrapper = SentryCacheManagerWrapper(delegate, scopes)
    val result = wrapper.getCache("missing")

    assertNull(result)
  }

  @Test
  fun `getCache does not double-wrap SentryCacheWrapper`() {
    val innerCache = mock<Cache>()
    val alreadyWrapped = SentryCacheWrapper(innerCache, scopes)
    whenever(delegate.getCache("test")).thenReturn(alreadyWrapped)

    val wrapper = SentryCacheManagerWrapper(delegate, scopes)
    val result = wrapper.getCache("test")

    assertSame(alreadyWrapped, result)
  }

  @Test
  fun `getCacheNames delegates to underlying cache manager`() {
    whenever(delegate.cacheNames).thenReturn(listOf("cache1", "cache2"))

    val wrapper = SentryCacheManagerWrapper(delegate, scopes)
    val result = wrapper.cacheNames

    assertEquals(listOf("cache1", "cache2"), result)
  }
}

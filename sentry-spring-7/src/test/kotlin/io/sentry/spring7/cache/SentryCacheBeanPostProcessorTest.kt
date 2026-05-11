package io.sentry.spring7.cache

import io.sentry.IScopes
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.mockito.kotlin.mock
import org.springframework.cache.CacheManager

class SentryCacheBeanPostProcessorTest {

  private val scopes: IScopes = mock()

  @Test
  fun `wraps CacheManager beans in SentryCacheManagerWrapper`() {
    val cacheManager = mock<CacheManager>()
    val processor = SentryCacheBeanPostProcessor()

    val result = processor.postProcessAfterInitialization(cacheManager, "cacheManager")

    assertTrue(result is SentryCacheManagerWrapper)
  }

  @Test
  fun `does not double-wrap SentryCacheManagerWrapper`() {
    val delegate = mock<CacheManager>()
    val alreadyWrapped = SentryCacheManagerWrapper(delegate, scopes)
    val processor = SentryCacheBeanPostProcessor()

    val result = processor.postProcessAfterInitialization(alreadyWrapped, "cacheManager")

    assertSame(alreadyWrapped, result)
  }

  @Test
  fun `does not wrap non-CacheManager beans`() {
    val someBean = "not a cache manager"
    val processor = SentryCacheBeanPostProcessor()

    val result = processor.postProcessAfterInitialization(someBean, "someBean")

    assertSame(someBean, result)
  }
}

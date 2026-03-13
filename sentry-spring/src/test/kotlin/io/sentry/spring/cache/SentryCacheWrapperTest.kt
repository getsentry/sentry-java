package io.sentry.spring.cache

import io.sentry.IScopes
import io.sentry.SentryOptions
import io.sentry.SentryTracer
import io.sentry.SpanDataConvention
import io.sentry.SpanStatus
import io.sentry.TransactionContext
import java.util.concurrent.Callable
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.cache.Cache

class SentryCacheWrapperTest {

  private lateinit var scopes: IScopes
  private lateinit var delegate: Cache
  private lateinit var options: SentryOptions

  @BeforeTest
  fun setup() {
    scopes = mock()
    delegate = mock()
    options = SentryOptions().apply { isEnableCacheTracing = true }
    whenever(scopes.options).thenReturn(options)
    whenever(delegate.name).thenReturn("testCache")
  }

  private fun createTransaction(): SentryTracer {
    val tx = SentryTracer(TransactionContext("tx", "op"), scopes)
    whenever(scopes.span).thenReturn(tx)
    return tx
  }

  // -- get(Object key) --

  @Test
  fun `get with ValueWrapper creates span with cache hit true on hit`() {
    val tx = createTransaction()
    val wrapper = SentryCacheWrapper(delegate, scopes)
    val valueWrapper = mock<Cache.ValueWrapper>()
    whenever(delegate.get("myKey")).thenReturn(valueWrapper)

    val result = wrapper.get("myKey")

    assertEquals(valueWrapper, result)
    assertEquals(1, tx.spans.size)
    val span = tx.spans.first()
    assertEquals("cache.get", span.operation)
    assertEquals("myKey", span.description)
    assertEquals(SpanStatus.OK, span.status)
    assertEquals(true, span.getData(SpanDataConvention.CACHE_HIT_KEY))
    assertEquals(listOf("myKey"), span.getData(SpanDataConvention.CACHE_KEY_KEY))
    assertEquals("auto.cache.spring", span.spanContext.origin)
  }

  @Test
  fun `get with ValueWrapper creates span with cache hit false on miss`() {
    val tx = createTransaction()
    val wrapper = SentryCacheWrapper(delegate, scopes)
    whenever(delegate.get("myKey")).thenReturn(null)

    val result = wrapper.get("myKey")

    assertNull(result)
    assertEquals(1, tx.spans.size)
    assertEquals(false, tx.spans.first().getData(SpanDataConvention.CACHE_HIT_KEY))
  }

  // -- get(Object key, Class<T>) --

  @Test
  fun `get with type creates span with cache hit true on hit`() {
    val tx = createTransaction()
    val wrapper = SentryCacheWrapper(delegate, scopes)
    whenever(delegate.get("myKey", String::class.java)).thenReturn("value")

    val result = wrapper.get("myKey", String::class.java)

    assertEquals("value", result)
    assertEquals(1, tx.spans.size)
    assertEquals(true, tx.spans.first().getData(SpanDataConvention.CACHE_HIT_KEY))
  }

  @Test
  fun `get with type creates span with cache hit false on miss`() {
    val tx = createTransaction()
    val wrapper = SentryCacheWrapper(delegate, scopes)
    whenever(delegate.get("myKey", String::class.java)).thenReturn(null)

    val result = wrapper.get("myKey", String::class.java)

    assertNull(result)
    assertEquals(1, tx.spans.size)
    assertEquals(false, tx.spans.first().getData(SpanDataConvention.CACHE_HIT_KEY))
  }

  // -- get(Object key, Callable<T>) --

  @Test
  fun `get with callable creates span with cache hit true on hit`() {
    val tx = createTransaction()
    val wrapper = SentryCacheWrapper(delegate, scopes)
    // Simulate cache hit: delegate returns value without invoking the loader
    whenever(delegate.get(eq("myKey"), any<Callable<String>>())).thenReturn("cached")

    val result = wrapper.get("myKey", Callable { "loaded" })

    assertEquals("cached", result)
    assertEquals(1, tx.spans.size)
    assertEquals(true, tx.spans.first().getData(SpanDataConvention.CACHE_HIT_KEY))
  }

  @Test
  fun `get with callable creates span with cache hit false on miss`() {
    val tx = createTransaction()
    val wrapper = SentryCacheWrapper(delegate, scopes)
    // Simulate cache miss: delegate invokes the loader callable
    whenever(delegate.get(eq("myKey"), any<Callable<String>>())).thenAnswer { invocation ->
      val loader = invocation.getArgument<Callable<String>>(1)
      loader.call()
    }

    val result = wrapper.get("myKey", Callable { "loaded" })

    assertEquals("loaded", result)
    assertEquals(1, tx.spans.size)
    assertEquals(false, tx.spans.first().getData(SpanDataConvention.CACHE_HIT_KEY))
  }

  // -- put --

  @Test
  fun `put creates cache put span`() {
    val tx = createTransaction()
    val wrapper = SentryCacheWrapper(delegate, scopes)

    wrapper.put("myKey", "myValue")

    verify(delegate).put("myKey", "myValue")
    assertEquals(1, tx.spans.size)
    val span = tx.spans.first()
    assertEquals("cache.put", span.operation)
    assertEquals(SpanStatus.OK, span.status)
    assertEquals(listOf("myKey"), span.getData(SpanDataConvention.CACHE_KEY_KEY))
  }

  // -- putIfAbsent --

  @Test
  fun `putIfAbsent delegates without creating span`() {
    val tx = createTransaction()
    val wrapper = SentryCacheWrapper(delegate, scopes)
    whenever(delegate.putIfAbsent("myKey", "myValue")).thenReturn(null)

    wrapper.putIfAbsent("myKey", "myValue")

    verify(delegate).putIfAbsent("myKey", "myValue")
    assertEquals(0, tx.spans.size)
  }

  // -- evict --

  @Test
  fun `evict creates cache remove span`() {
    val tx = createTransaction()
    val wrapper = SentryCacheWrapper(delegate, scopes)

    wrapper.evict("myKey")

    verify(delegate).evict("myKey")
    assertEquals(1, tx.spans.size)
    val span = tx.spans.first()
    assertEquals("cache.remove", span.operation)
    assertEquals(SpanStatus.OK, span.status)
  }

  // -- evictIfPresent --

  @Test
  fun `evictIfPresent creates cache remove span`() {
    val tx = createTransaction()
    val wrapper = SentryCacheWrapper(delegate, scopes)
    whenever(delegate.evictIfPresent("myKey")).thenReturn(true)

    val result = wrapper.evictIfPresent("myKey")

    assertTrue(result)
    assertEquals(1, tx.spans.size)
    assertEquals("cache.remove", tx.spans.first().operation)
  }

  // -- clear --

  @Test
  fun `clear creates cache flush span`() {
    val tx = createTransaction()
    val wrapper = SentryCacheWrapper(delegate, scopes)

    wrapper.clear()

    verify(delegate).clear()
    assertEquals(1, tx.spans.size)
    val span = tx.spans.first()
    assertEquals("cache.flush", span.operation)
    assertEquals(SpanStatus.OK, span.status)
    assertNull(span.getData(SpanDataConvention.CACHE_KEY_KEY))
  }

  // -- invalidate --

  @Test
  fun `invalidate creates cache flush span`() {
    val tx = createTransaction()
    val wrapper = SentryCacheWrapper(delegate, scopes)
    whenever(delegate.invalidate()).thenReturn(true)

    val result = wrapper.invalidate()

    assertTrue(result)
    assertEquals(1, tx.spans.size)
    assertEquals("cache.flush", tx.spans.first().operation)
  }

  // -- no span when no active transaction --

  @Test
  fun `does not create span when there is no active transaction`() {
    whenever(scopes.span).thenReturn(null)
    val wrapper = SentryCacheWrapper(delegate, scopes)
    whenever(delegate.get("myKey")).thenReturn(null)

    wrapper.get("myKey")

    verify(delegate).get("myKey")
  }

  // -- no span when option is disabled --

  @Test
  fun `does not create span when enableCacheTracing is false`() {
    options.isEnableCacheTracing = false
    val tx = createTransaction()
    val wrapper = SentryCacheWrapper(delegate, scopes)
    whenever(delegate.get("myKey")).thenReturn(null)

    wrapper.get("myKey")

    verify(delegate).get("myKey")
    assertEquals(0, tx.spans.size)
  }

  // -- error handling --

  @Test
  fun `sets error status and throwable on exception`() {
    val tx = createTransaction()
    val wrapper = SentryCacheWrapper(delegate, scopes)
    val exception = RuntimeException("cache error")
    whenever(delegate.get("myKey")).thenThrow(exception)

    assertFailsWith<RuntimeException> { wrapper.get("myKey") }

    assertEquals(1, tx.spans.size)
    val span = tx.spans.first()
    assertEquals(SpanStatus.INTERNAL_ERROR, span.status)
    assertEquals(exception, span.throwable)
  }

  // -- delegation --

  @Test
  fun `getName delegates to underlying cache`() {
    val wrapper = SentryCacheWrapper(delegate, scopes)
    assertEquals("testCache", wrapper.name)
  }

  @Test
  fun `getNativeCache delegates to underlying cache`() {
    val nativeCache = Object()
    whenever(delegate.nativeCache).thenReturn(nativeCache)
    val wrapper = SentryCacheWrapper(delegate, scopes)

    assertEquals(nativeCache, wrapper.nativeCache)
  }
}

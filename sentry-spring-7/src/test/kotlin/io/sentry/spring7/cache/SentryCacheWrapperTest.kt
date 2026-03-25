package io.sentry.spring7.cache

import io.sentry.IScopes
import io.sentry.SentryOptions
import io.sentry.SentryTracer
import io.sentry.SpanDataConvention
import io.sentry.SpanStatus
import io.sentry.TransactionContext
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.function.Supplier
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
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
    assertEquals(true, span.getData(SpanDataConvention.CACHE_HIT))
    assertNull(span.getData(SpanDataConvention.CACHE_WRITE))
    assertEquals(listOf("myKey"), span.getData(SpanDataConvention.CACHE_KEY))
    assertEquals("auto.cache.spring", span.spanContext.origin)
    assertEquals("get", span.getData(SpanDataConvention.CACHE_OPERATION))
  }

  @Test
  fun `get with ValueWrapper creates span with cache hit false on miss`() {
    val tx = createTransaction()
    val wrapper = SentryCacheWrapper(delegate, scopes)
    whenever(delegate.get("myKey")).thenReturn(null)

    val result = wrapper.get("myKey")

    assertNull(result)
    assertEquals(1, tx.spans.size)
    assertEquals(false, tx.spans.first().getData(SpanDataConvention.CACHE_HIT))
    assertEquals(listOf("myKey"), tx.spans.first().getData(SpanDataConvention.CACHE_KEY))
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
    assertEquals(true, tx.spans.first().getData(SpanDataConvention.CACHE_HIT))
    assertEquals(listOf("myKey"), tx.spans.first().getData(SpanDataConvention.CACHE_KEY))
  }

  @Test
  fun `get with type creates span with cache hit false on miss`() {
    val tx = createTransaction()
    val wrapper = SentryCacheWrapper(delegate, scopes)
    whenever(delegate.get("myKey", String::class.java)).thenReturn(null)

    val result = wrapper.get("myKey", String::class.java)

    assertNull(result)
    assertEquals(1, tx.spans.size)
    assertEquals(false, tx.spans.first().getData(SpanDataConvention.CACHE_HIT))
    assertEquals(listOf("myKey"), tx.spans.first().getData(SpanDataConvention.CACHE_KEY))
  }

  @Test
  fun `get with type sets error status and throwable on exception`() {
    val tx = createTransaction()
    val wrapper = SentryCacheWrapper(delegate, scopes)
    val exception = RuntimeException("cache error")
    whenever(delegate.get("myKey", String::class.java)).thenThrow(exception)

    assertFailsWith<RuntimeException> { wrapper.get("myKey", String::class.java) }

    assertEquals(1, tx.spans.size)
    val span = tx.spans.first()
    assertEquals(SpanStatus.INTERNAL_ERROR, span.status)
    assertEquals(exception, span.throwable)
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
    assertEquals(true, tx.spans.first().getData(SpanDataConvention.CACHE_HIT))
    assertEquals(false, tx.spans.first().getData(SpanDataConvention.CACHE_WRITE))
    assertEquals(listOf("myKey"), tx.spans.first().getData(SpanDataConvention.CACHE_KEY))
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
    assertEquals(false, tx.spans.first().getData(SpanDataConvention.CACHE_HIT))
    assertEquals(true, tx.spans.first().getData(SpanDataConvention.CACHE_WRITE))
    assertEquals(listOf("myKey"), tx.spans.first().getData(SpanDataConvention.CACHE_KEY))
  }

  // -- retrieve(Object key) --

  @Test
  fun `retrieve creates span with cache hit true when future resolves with value`() {
    val tx = createTransaction()
    val wrapper = SentryCacheWrapper(delegate, scopes)
    whenever(delegate.retrieve("myKey")).thenReturn(CompletableFuture.completedFuture("value"))

    val result = wrapper.retrieve("myKey")

    assertEquals("value", result!!.get())
    assertEquals(1, tx.spans.size)
    val span = tx.spans.first()
    assertEquals("cache.retrieve", span.operation)
    assertEquals("myKey", span.description)
    assertEquals(SpanStatus.OK, span.status)
    assertEquals(true, span.getData(SpanDataConvention.CACHE_HIT))
    assertNull(span.getData(SpanDataConvention.CACHE_WRITE))
    assertEquals("retrieve", span.getData(SpanDataConvention.CACHE_OPERATION))
    assertTrue(span.isFinished)
  }

  @Test
  fun `retrieve creates span with cache hit false when future resolves with null`() {
    val tx = createTransaction()
    val wrapper = SentryCacheWrapper(delegate, scopes)
    whenever(delegate.retrieve("myKey")).thenReturn(CompletableFuture.completedFuture(null))

    val result = wrapper.retrieve("myKey")

    assertNull(result!!.get())
    assertEquals(1, tx.spans.size)
    assertEquals(false, tx.spans.first().getData(SpanDataConvention.CACHE_HIT))
    assertEquals(listOf("myKey"), tx.spans.first().getData(SpanDataConvention.CACHE_KEY))
    assertTrue(tx.spans.first().isFinished)
  }

  @Test
  fun `retrieve creates span with cache hit false when delegate returns null`() {
    val tx = createTransaction()
    val wrapper = SentryCacheWrapper(delegate, scopes)
    whenever(delegate.retrieve("myKey")).thenReturn(null)

    val result = wrapper.retrieve("myKey")

    assertNull(result)
    assertEquals(1, tx.spans.size)
    val span = tx.spans.first()
    assertEquals(false, span.getData(SpanDataConvention.CACHE_HIT))
    assertEquals(listOf("myKey"), span.getData(SpanDataConvention.CACHE_KEY))
    assertEquals(SpanStatus.OK, span.status)
    assertTrue(span.isFinished)
  }

  @Test
  fun `retrieve sets error status when future completes exceptionally`() {
    val tx = createTransaction()
    val wrapper = SentryCacheWrapper(delegate, scopes)
    val exception = RuntimeException("async cache error")
    whenever(delegate.retrieve("myKey"))
      .thenReturn(CompletableFuture<Any>().also { it.completeExceptionally(exception) })

    val result = wrapper.retrieve("myKey")

    assertFailsWith<Exception> { result!!.get() }
    assertEquals(1, tx.spans.size)
    val span = tx.spans.first()
    assertEquals(SpanStatus.INTERNAL_ERROR, span.status)
    assertEquals(exception, span.throwable)
    assertTrue(span.isFinished)
  }

  @Test
  fun `retrieve sets error status when delegate throws synchronously`() {
    val tx = createTransaction()
    val wrapper = SentryCacheWrapper(delegate, scopes)
    val exception = RuntimeException("sync error")
    whenever(delegate.retrieve("myKey")).thenThrow(exception)

    assertFailsWith<RuntimeException> { wrapper.retrieve("myKey") }

    assertEquals(1, tx.spans.size)
    val span = tx.spans.first()
    assertEquals(SpanStatus.INTERNAL_ERROR, span.status)
    assertEquals(exception, span.throwable)
    assertTrue(span.isFinished)
  }

  @Test
  fun `retrieve does not create span when tracing is disabled`() {
    options.isEnableCacheTracing = false
    val tx = createTransaction()
    val wrapper = SentryCacheWrapper(delegate, scopes)
    whenever(delegate.retrieve("myKey")).thenReturn(CompletableFuture.completedFuture("value"))

    wrapper.retrieve("myKey")

    verify(delegate).retrieve("myKey")
    assertEquals(0, tx.spans.size)
  }

  // -- retrieve(Object key, Supplier<CompletableFuture<T>>) --

  @Test
  fun `retrieve with loader creates span with cache hit true when loader not invoked`() {
    val tx = createTransaction()
    val wrapper = SentryCacheWrapper(delegate, scopes)
    // Simulate cache hit: delegate returns value without invoking the loader
    whenever(delegate.retrieve(eq("myKey"), any<Supplier<CompletableFuture<String>>>()))
      .thenReturn(CompletableFuture.completedFuture("cached"))

    val result = wrapper.retrieve("myKey") { CompletableFuture.completedFuture("loaded") }

    assertEquals("cached", result.get())
    assertEquals(1, tx.spans.size)
    assertEquals(true, tx.spans.first().getData(SpanDataConvention.CACHE_HIT))
    assertEquals(false, tx.spans.first().getData(SpanDataConvention.CACHE_WRITE))
    assertEquals(listOf("myKey"), tx.spans.first().getData(SpanDataConvention.CACHE_KEY))
    assertTrue(tx.spans.first().isFinished)
  }

  @Test
  fun `retrieve with loader creates span with cache hit false when loader invoked`() {
    val tx = createTransaction()
    val wrapper = SentryCacheWrapper(delegate, scopes)
    // Simulate cache miss: delegate invokes the loader supplier
    whenever(delegate.retrieve(eq("myKey"), any<Supplier<CompletableFuture<String>>>()))
      .thenAnswer { invocation ->
        val loader = invocation.getArgument<Supplier<CompletableFuture<String>>>(1)
        loader.get()
      }

    val result = wrapper.retrieve("myKey") { CompletableFuture.completedFuture("loaded") }

    assertEquals("loaded", result.get())
    assertEquals(1, tx.spans.size)
    assertEquals(false, tx.spans.first().getData(SpanDataConvention.CACHE_HIT))
    assertEquals(true, tx.spans.first().getData(SpanDataConvention.CACHE_WRITE))
    assertEquals(listOf("myKey"), tx.spans.first().getData(SpanDataConvention.CACHE_KEY))
    assertTrue(tx.spans.first().isFinished)
  }

  @Test
  fun `retrieve with loader sets error status when future completes exceptionally`() {
    val tx = createTransaction()
    val wrapper = SentryCacheWrapper(delegate, scopes)
    val exception = RuntimeException("async loader error")
    whenever(delegate.retrieve(eq("myKey"), any<Supplier<CompletableFuture<String>>>()))
      .thenReturn(CompletableFuture<String>().also { it.completeExceptionally(exception) })

    val result = wrapper.retrieve("myKey") { CompletableFuture.completedFuture("loaded") }

    assertFailsWith<Exception> { result.get() }
    assertEquals(1, tx.spans.size)
    val span = tx.spans.first()
    assertEquals(SpanStatus.INTERNAL_ERROR, span.status)
    assertEquals(exception, span.throwable)
    assertTrue(span.isFinished)
  }

  @Test
  fun `retrieve with loader does not create span when tracing is disabled`() {
    options.isEnableCacheTracing = false
    val tx = createTransaction()
    val wrapper = SentryCacheWrapper(delegate, scopes)
    whenever(delegate.retrieve(eq("myKey"), any<Supplier<CompletableFuture<String>>>()))
      .thenReturn(CompletableFuture.completedFuture("cached"))

    wrapper.retrieve("myKey") { CompletableFuture.completedFuture("loaded") }

    verify(delegate).retrieve(eq("myKey"), any<Supplier<CompletableFuture<String>>>())
    assertEquals(0, tx.spans.size)
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
    assertEquals(true, span.getData(SpanDataConvention.CACHE_WRITE))
    assertEquals(listOf("myKey"), span.getData(SpanDataConvention.CACHE_KEY))
    assertEquals("put", span.getData(SpanDataConvention.CACHE_OPERATION))
  }

  // -- putIfAbsent --

  @Test
  fun `putIfAbsent creates cache put span`() {
    val tx = createTransaction()
    val wrapper = SentryCacheWrapper(delegate, scopes)
    whenever(delegate.putIfAbsent("myKey", "myValue")).thenReturn(null)

    val result = wrapper.putIfAbsent("myKey", "myValue")

    assertNull(result)
    verify(delegate).putIfAbsent("myKey", "myValue")
    assertEquals(1, tx.spans.size)
    val span = tx.spans.first()
    assertEquals("cache.putIfAbsent", span.operation)
    assertEquals(SpanStatus.OK, span.status)
    assertEquals(true, span.getData(SpanDataConvention.CACHE_WRITE))
    assertEquals(listOf("myKey"), span.getData(SpanDataConvention.CACHE_KEY))
    assertEquals("putIfAbsent", span.getData(SpanDataConvention.CACHE_OPERATION))
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
    assertEquals("cache.evict", span.operation)
    assertEquals(SpanStatus.OK, span.status)
    assertEquals(true, span.getData(SpanDataConvention.CACHE_WRITE))
    assertEquals("evict", span.getData(SpanDataConvention.CACHE_OPERATION))
    assertEquals(listOf("myKey"), span.getData(SpanDataConvention.CACHE_KEY))
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
    assertEquals("cache.evictIfPresent", tx.spans.first().operation)
    assertEquals(true, tx.spans.first().getData(SpanDataConvention.CACHE_WRITE))
    assertEquals("evictIfPresent", tx.spans.first().getData(SpanDataConvention.CACHE_OPERATION))
    assertEquals(listOf("myKey"), tx.spans.first().getData(SpanDataConvention.CACHE_KEY))
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
    assertEquals("cache.clear", span.operation)
    assertEquals(SpanStatus.OK, span.status)
    assertEquals(true, span.getData(SpanDataConvention.CACHE_WRITE))
    assertNull(span.getData(SpanDataConvention.CACHE_KEY))
    assertEquals("clear", span.getData(SpanDataConvention.CACHE_OPERATION))
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
    assertEquals("cache.invalidate", tx.spans.first().operation)
    assertEquals(true, tx.spans.first().getData(SpanDataConvention.CACHE_WRITE))
    assertEquals("invalidate", tx.spans.first().getData(SpanDataConvention.CACHE_OPERATION))
  }

  @Test
  fun `invalidate sets cache write false when cache had no mappings`() {
    val tx = createTransaction()
    val wrapper = SentryCacheWrapper(delegate, scopes)
    whenever(delegate.invalidate()).thenReturn(false)

    val result = wrapper.invalidate()

    assertFalse(result)
    assertEquals(1, tx.spans.size)
    assertEquals("cache.invalidate", tx.spans.first().operation)
    assertEquals(false, tx.spans.first().getData(SpanDataConvention.CACHE_WRITE))
    assertEquals("invalidate", tx.spans.first().getData(SpanDataConvention.CACHE_OPERATION))
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

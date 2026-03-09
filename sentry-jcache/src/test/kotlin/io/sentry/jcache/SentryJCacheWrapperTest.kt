package io.sentry.jcache

import io.sentry.IScopes
import io.sentry.SentryOptions
import io.sentry.SentryTracer
import io.sentry.SpanDataConvention
import io.sentry.SpanStatus
import io.sentry.TransactionContext
import javax.cache.Cache
import javax.cache.CacheManager
import javax.cache.configuration.CacheEntryListenerConfiguration
import javax.cache.configuration.Configuration
import javax.cache.integration.CompletionListener
import javax.cache.processor.EntryProcessor
import javax.cache.processor.EntryProcessorResult
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SentryJCacheWrapperTest {

  private lateinit var scopes: IScopes
  private lateinit var delegate: Cache<String, String>
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

  // -- get(K key) --

  @Test
  fun `get creates span with cache hit true on hit`() {
    val tx = createTransaction()
    val wrapper = SentryJCacheWrapper(delegate, scopes)
    whenever(delegate.get("myKey")).thenReturn("value")

    val result = wrapper.get("myKey")

    assertEquals("value", result)
    assertEquals(1, tx.spans.size)
    val span = tx.spans.first()
    assertEquals("cache.get", span.operation)
    assertEquals("myKey", span.description)
    assertEquals(SpanStatus.OK, span.status)
    assertEquals(true, span.getData(SpanDataConvention.CACHE_HIT_KEY))
    assertEquals(listOf("myKey"), span.getData(SpanDataConvention.CACHE_KEY_KEY))
    assertEquals("auto.cache.jcache", span.spanContext.origin)
  }

  @Test
  fun `get creates span with cache hit false on miss`() {
    val tx = createTransaction()
    val wrapper = SentryJCacheWrapper(delegate, scopes)
    whenever(delegate.get("myKey")).thenReturn(null)

    val result = wrapper.get("myKey")

    assertNull(result)
    assertEquals(1, tx.spans.size)
    assertEquals(false, tx.spans.first().getData(SpanDataConvention.CACHE_HIT_KEY))
  }

  // -- getAll --

  @Test
  fun `getAll creates span with cache hit true when results exist`() {
    val tx = createTransaction()
    val wrapper = SentryJCacheWrapper(delegate, scopes)
    val keys = setOf("k1", "k2")
    whenever(delegate.getAll(keys)).thenReturn(mapOf("k1" to "v1"))

    val result = wrapper.getAll(keys)

    assertEquals(mapOf("k1" to "v1"), result)
    assertEquals(1, tx.spans.size)
    val span = tx.spans.first()
    assertEquals("cache.get", span.operation)
    assertEquals("testCache", span.description)
    assertEquals(true, span.getData(SpanDataConvention.CACHE_HIT_KEY))
    val cacheKeys = span.getData(SpanDataConvention.CACHE_KEY_KEY) as List<*>
    assertTrue(cacheKeys.containsAll(listOf("k1", "k2")))
  }

  @Test
  fun `getAll creates span with cache hit false when empty`() {
    val tx = createTransaction()
    val wrapper = SentryJCacheWrapper(delegate, scopes)
    val keys = setOf("k1")
    whenever(delegate.getAll(keys)).thenReturn(emptyMap())

    wrapper.getAll(keys)

    assertEquals(false, tx.spans.first().getData(SpanDataConvention.CACHE_HIT_KEY))
  }

  // -- put --

  @Test
  fun `put creates cache put span`() {
    val tx = createTransaction()
    val wrapper = SentryJCacheWrapper(delegate, scopes)

    wrapper.put("myKey", "myValue")

    verify(delegate).put("myKey", "myValue")
    assertEquals(1, tx.spans.size)
    val span = tx.spans.first()
    assertEquals("cache.put", span.operation)
    assertEquals(SpanStatus.OK, span.status)
    assertEquals(listOf("myKey"), span.getData(SpanDataConvention.CACHE_KEY_KEY))
  }

  // -- getAndPut --

  @Test
  fun `getAndPut creates cache put span`() {
    val tx = createTransaction()
    val wrapper = SentryJCacheWrapper(delegate, scopes)
    whenever(delegate.getAndPut("myKey", "newValue")).thenReturn("oldValue")

    val result = wrapper.getAndPut("myKey", "newValue")

    assertEquals("oldValue", result)
    assertEquals(1, tx.spans.size)
    assertEquals("cache.put", tx.spans.first().operation)
  }

  // -- putAll --

  @Test
  fun `putAll creates cache put span with all keys`() {
    val tx = createTransaction()
    val wrapper = SentryJCacheWrapper(delegate, scopes)
    val entries = mapOf("k1" to "v1", "k2" to "v2")

    wrapper.putAll(entries)

    verify(delegate).putAll(entries)
    assertEquals(1, tx.spans.size)
    val span = tx.spans.first()
    assertEquals("cache.put", span.operation)
    assertEquals("testCache", span.description)
    val cacheKeys = span.getData(SpanDataConvention.CACHE_KEY_KEY) as List<*>
    assertTrue(cacheKeys.containsAll(listOf("k1", "k2")))
  }

  // -- putIfAbsent --

  @Test
  fun `putIfAbsent delegates without creating span`() {
    val tx = createTransaction()
    val wrapper = SentryJCacheWrapper(delegate, scopes)
    whenever(delegate.putIfAbsent("myKey", "myValue")).thenReturn(true)

    val result = wrapper.putIfAbsent("myKey", "myValue")

    assertTrue(result)
    verify(delegate).putIfAbsent("myKey", "myValue")
    assertEquals(0, tx.spans.size)
  }

  // -- replace(K, V, V) --

  @Test
  fun `replace with old value creates cache put span`() {
    val tx = createTransaction()
    val wrapper = SentryJCacheWrapper(delegate, scopes)
    whenever(delegate.replace("myKey", "old", "new")).thenReturn(true)

    val result = wrapper.replace("myKey", "old", "new")

    assertTrue(result)
    assertEquals(1, tx.spans.size)
    assertEquals("cache.put", tx.spans.first().operation)
  }

  // -- replace(K, V) --

  @Test
  fun `replace creates cache put span`() {
    val tx = createTransaction()
    val wrapper = SentryJCacheWrapper(delegate, scopes)
    whenever(delegate.replace("myKey", "value")).thenReturn(true)

    val result = wrapper.replace("myKey", "value")

    assertTrue(result)
    assertEquals(1, tx.spans.size)
    assertEquals("cache.put", tx.spans.first().operation)
  }

  // -- getAndReplace --

  @Test
  fun `getAndReplace creates cache put span`() {
    val tx = createTransaction()
    val wrapper = SentryJCacheWrapper(delegate, scopes)
    whenever(delegate.getAndReplace("myKey", "newValue")).thenReturn("oldValue")

    val result = wrapper.getAndReplace("myKey", "newValue")

    assertEquals("oldValue", result)
    assertEquals(1, tx.spans.size)
    assertEquals("cache.put", tx.spans.first().operation)
  }

  // -- remove(K) --

  @Test
  fun `remove creates cache remove span`() {
    val tx = createTransaction()
    val wrapper = SentryJCacheWrapper(delegate, scopes)
    whenever(delegate.remove("myKey")).thenReturn(true)

    val result = wrapper.remove("myKey")

    assertTrue(result)
    assertEquals(1, tx.spans.size)
    val span = tx.spans.first()
    assertEquals("cache.remove", span.operation)
    assertEquals(SpanStatus.OK, span.status)
  }

  // -- remove(K, V) --

  @Test
  fun `remove with value creates cache remove span`() {
    val tx = createTransaction()
    val wrapper = SentryJCacheWrapper(delegate, scopes)
    whenever(delegate.remove("myKey", "myValue")).thenReturn(true)

    val result = wrapper.remove("myKey", "myValue")

    assertTrue(result)
    assertEquals(1, tx.spans.size)
    assertEquals("cache.remove", tx.spans.first().operation)
  }

  // -- getAndRemove --

  @Test
  fun `getAndRemove creates cache remove span`() {
    val tx = createTransaction()
    val wrapper = SentryJCacheWrapper(delegate, scopes)
    whenever(delegate.getAndRemove("myKey")).thenReturn("value")

    val result = wrapper.getAndRemove("myKey")

    assertEquals("value", result)
    assertEquals(1, tx.spans.size)
    assertEquals("cache.remove", tx.spans.first().operation)
  }

  // -- removeAll(Set) --

  @Test
  fun `removeAll with keys creates cache remove span`() {
    val tx = createTransaction()
    val wrapper = SentryJCacheWrapper(delegate, scopes)
    val keys = setOf("k1", "k2")

    wrapper.removeAll(keys)

    verify(delegate).removeAll(keys)
    assertEquals(1, tx.spans.size)
    val span = tx.spans.first()
    assertEquals("cache.remove", span.operation)
    assertEquals("testCache", span.description)
  }

  // -- removeAll() --

  @Test
  fun `removeAll creates cache remove span`() {
    val tx = createTransaction()
    val wrapper = SentryJCacheWrapper(delegate, scopes)

    wrapper.removeAll()

    verify(delegate).removeAll()
    assertEquals(1, tx.spans.size)
    assertEquals("cache.remove", tx.spans.first().operation)
  }

  // -- clear --

  @Test
  fun `clear creates cache flush span`() {
    val tx = createTransaction()
    val wrapper = SentryJCacheWrapper(delegate, scopes)

    wrapper.clear()

    verify(delegate).clear()
    assertEquals(1, tx.spans.size)
    val span = tx.spans.first()
    assertEquals("cache.flush", span.operation)
    assertEquals(SpanStatus.OK, span.status)
    assertNull(span.getData(SpanDataConvention.CACHE_KEY_KEY))
  }

  // -- invoke --

  @Test
  fun `invoke creates cache get span`() {
    val tx = createTransaction()
    val wrapper = SentryJCacheWrapper(delegate, scopes)
    val processor = mock<EntryProcessor<String, String, String>>()
    whenever(delegate.invoke("myKey", processor)).thenReturn("result")

    val result = wrapper.invoke("myKey", processor)

    assertEquals("result", result)
    assertEquals(1, tx.spans.size)
    assertEquals("cache.get", tx.spans.first().operation)
  }

  // -- invokeAll --

  @Test
  fun `invokeAll creates cache get span`() {
    val tx = createTransaction()
    val wrapper = SentryJCacheWrapper(delegate, scopes)
    val processor = mock<EntryProcessor<String, String, String>>()
    val keys = setOf("k1", "k2")
    val resultMap = mock<Map<String, EntryProcessorResult<String>>>()
    whenever(delegate.invokeAll(keys, processor)).thenReturn(resultMap)

    val result = wrapper.invokeAll(keys, processor)

    assertEquals(resultMap, result)
    assertEquals(1, tx.spans.size)
    assertEquals("cache.get", tx.spans.first().operation)
  }

  // -- passthrough operations --

  @Test
  fun `containsKey delegates without creating span`() {
    val tx = createTransaction()
    val wrapper = SentryJCacheWrapper(delegate, scopes)
    whenever(delegate.containsKey("myKey")).thenReturn(true)

    assertTrue(wrapper.containsKey("myKey"))
    assertEquals(0, tx.spans.size)
  }

  @Test
  fun `getName delegates to underlying cache`() {
    val wrapper = SentryJCacheWrapper(delegate, scopes)
    assertEquals("testCache", wrapper.name)
  }

  @Test
  fun `getCacheManager delegates to underlying cache`() {
    val manager = mock<CacheManager>()
    whenever(delegate.cacheManager).thenReturn(manager)
    val wrapper = SentryJCacheWrapper(delegate, scopes)
    assertEquals(manager, wrapper.cacheManager)
  }

  @Test
  fun `isClosed delegates to underlying cache`() {
    whenever(delegate.isClosed).thenReturn(false)
    val wrapper = SentryJCacheWrapper(delegate, scopes)
    assertFalse(wrapper.isClosed)
  }

  @Test
  fun `close delegates to underlying cache`() {
    val wrapper = SentryJCacheWrapper(delegate, scopes)
    wrapper.close()
    verify(delegate).close()
  }

  @Test
  fun `registerCacheEntryListener delegates to underlying cache`() {
    val wrapper = SentryJCacheWrapper(delegate, scopes)
    val config = mock<CacheEntryListenerConfiguration<String, String>>()
    wrapper.registerCacheEntryListener(config)
    verify(delegate).registerCacheEntryListener(config)
  }

  @Test
  fun `deregisterCacheEntryListener delegates to underlying cache`() {
    val wrapper = SentryJCacheWrapper(delegate, scopes)
    val config = mock<CacheEntryListenerConfiguration<String, String>>()
    wrapper.deregisterCacheEntryListener(config)
    verify(delegate).deregisterCacheEntryListener(config)
  }

  @Test
  fun `iterator delegates to underlying cache`() {
    val iter = mock<MutableIterator<Cache.Entry<String, String>>>()
    whenever(delegate.iterator()).thenReturn(iter)
    val wrapper = SentryJCacheWrapper(delegate, scopes)
    assertEquals(iter, wrapper.iterator())
  }

  @Test
  fun `loadAll delegates to underlying cache`() {
    val wrapper = SentryJCacheWrapper(delegate, scopes)
    val keys = setOf("k1")
    val listener = mock<CompletionListener>()
    wrapper.loadAll(keys, true, listener)
    verify(delegate).loadAll(keys, true, listener)
  }

  @Test
  fun `getConfiguration delegates to underlying cache`() {
    val config = mock<Configuration<String, String>>()
    whenever(
        delegate.getConfiguration(Configuration::class.java as Class<Configuration<String, String>>)
      )
      .thenReturn(config)
    val wrapper = SentryJCacheWrapper(delegate, scopes)
    assertEquals(
      config,
      wrapper.getConfiguration(Configuration::class.java as Class<Configuration<String, String>>),
    )
  }

  @Test
  fun `unwrap delegates to underlying cache`() {
    whenever(delegate.unwrap(String::class.java)).thenReturn("unwrapped")
    val wrapper = SentryJCacheWrapper(delegate, scopes)
    assertEquals("unwrapped", wrapper.unwrap(String::class.java))
  }

  // -- no span when no active transaction --

  @Test
  fun `does not create span when there is no active transaction`() {
    whenever(scopes.span).thenReturn(null)
    val wrapper = SentryJCacheWrapper(delegate, scopes)
    whenever(delegate.get("myKey")).thenReturn(null)

    wrapper.get("myKey")

    verify(delegate).get("myKey")
  }

  // -- no span when option is disabled --

  @Test
  fun `does not create span when enableCacheTracing is false`() {
    options.isEnableCacheTracing = false
    val tx = createTransaction()
    val wrapper = SentryJCacheWrapper(delegate, scopes)
    whenever(delegate.get("myKey")).thenReturn(null)

    wrapper.get("myKey")

    verify(delegate).get("myKey")
    assertEquals(0, tx.spans.size)
  }

  // -- error handling --

  @Test
  fun `sets error status and throwable on exception`() {
    val tx = createTransaction()
    val wrapper = SentryJCacheWrapper(delegate, scopes)
    val exception = RuntimeException("cache error")
    whenever(delegate.get("myKey")).thenThrow(exception)

    assertFailsWith<RuntimeException> { wrapper.get("myKey") }

    assertEquals(1, tx.spans.size)
    val span = tx.spans.first()
    assertEquals(SpanStatus.INTERNAL_ERROR, span.status)
    assertEquals(exception, span.throwable)
  }
}

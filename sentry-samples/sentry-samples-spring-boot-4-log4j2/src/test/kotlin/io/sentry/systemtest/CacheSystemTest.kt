package io.sentry.systemtest

import io.sentry.systemtest.util.TestHelper
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.Before

class CacheSystemTest {
  lateinit var testHelper: TestHelper

  @Before
  fun setup() {
    testHelper = TestHelper("http://localhost:8080")
    testHelper.reset()
  }

  @Test
  fun `cache put and get produce spans`() {
    val restClient = testHelper.restClient

    // Save a todo (triggers @CachePut -> cache.put span)
    val todo = Todo(1L, "test-todo", false)
    restClient.saveCachedTodo(todo)
    assertEquals(200, restClient.lastKnownStatusCode)

    testHelper.ensureTransactionReceived { transaction, _ ->
      testHelper.doesTransactionContainSpanWithOp(transaction, "cache.put")
    }

    testHelper.reset()

    // Get the todo (triggers @Cacheable -> cache.get span, should be a hit)
    restClient.getCachedTodo(1L)
    assertEquals(200, restClient.lastKnownStatusCode)

    testHelper.ensureTransactionReceived { transaction, _ ->
      testHelper.doesTransactionContainSpanWithOp(transaction, "cache.get")
    }
  }

  @Test
  fun `cache evict produces span`() {
    val restClient = testHelper.restClient

    restClient.deleteCachedTodo(1L)

    testHelper.ensureTransactionReceived { transaction, _ ->
      testHelper.doesTransactionContainSpanWithOp(transaction, "cache.evict")
    }
  }
}

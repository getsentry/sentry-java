package io.sentry
import org.junit.Assert.assertThrows
import java.util.NoSuchElementException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DisabledQueueTest {

    @Test
    fun `size starts empty`() {
        val queue = DisabledQueue<Int>()
        assertEquals(0, queue.size, "Size should always be zero.")
    }

    @Test
    fun `add does not add elements`() {
        val queue = DisabledQueue<Int>()
        assertFalse(queue.add(1), "add should always return false.")
        assertEquals(0, queue.size, "Size should still be zero after attempting to add an element.")
    }

    @Test
    fun `isEmpty returns true when created`() {
        val queue = DisabledQueue<Int>()
        assertTrue(queue.isEmpty(), "isEmpty should always return true.")
    }

    @Test
    fun `isEmpty always returns true if add function was called`() {
        val queue = DisabledQueue<Int>()
        queue.add(1)

        assertTrue(queue.isEmpty(), "isEmpty should always return true.")
    }

    @Test
    fun `offer does not add elements`() {
        val queue = DisabledQueue<Int>()
        assertFalse(queue.offer(1), "offer should always return false.")
        assertEquals(0, queue.size, "Size should still be zero after attempting to offer an element.")
    }

    @Test
    fun `poll returns null`() {
        val queue = DisabledQueue<Int>()
        queue.add(1)
        assertNull(queue.poll(), "poll should always return null.")
    }

    @Test
    fun `peek returns null`() {
        val queue = DisabledQueue<Int>()
        queue.add(1)

        assertNull(queue.peek(), "peek should always return null.")
    }

    @Test
    fun `element returns null`() {
        val queue = DisabledQueue<Int>()
        assertNull(queue.element(), "element should always return null.")
    }

    @Test
    fun `remove throws NoSuchElementException`() {
        val queue = DisabledQueue<Int>()
        assertThrows(NoSuchElementException::class.java) { queue.remove() }
    }

    @Test
    fun `clear does nothing`() {
        val queue = DisabledQueue<Int>()
        queue.clear() // Should not throw an exception
        assertEquals(0, queue.size, "Size should remain zero after clear.")
    }

    @Test
    fun `iterator has no elements`() {
        val queue = DisabledQueue<Int>()
        val iterator = queue.iterator()
        assertFalse(iterator.hasNext(), "Iterator should have no elements.")
        assertThrows(NoSuchElementException::class.java) { iterator.next() }
    }

    @Test
    fun `iterator remove throws IllegalStateException`() {
        val queue = DisabledQueue<Int>()
        val iterator = queue.iterator()
        assertThrows(IllegalStateException::class.java) { iterator.remove() }
    }
}

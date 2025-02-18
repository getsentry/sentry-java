/*
 * Adapted from: https://github.com/square/tape/tree/445cd3fd0a7b3ec48c9ea3e0e86663fe6d3735d8/tape/src/test/java/com/squareup/tape2
 *
 *  Copyright (C) 2010 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sentry.cache.tape

import io.sentry.cache.tape.ObjectQueue.Converter
import io.sentry.cache.tape.QueueFile.Builder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.io.OutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class ObjectQueueTest {
    enum class QueueFactory {
        FILE {
            override fun <T> create(queueFile: QueueFile, converter: Converter<T>): ObjectQueue<T> {
                return ObjectQueue.create(queueFile, converter)
            }
        };

        abstract fun <T> create(queueFile: QueueFile, converter: Converter<T>): ObjectQueue<T>
    }

    @get:Rule
    val folder = TemporaryFolder()
    private lateinit var queue: ObjectQueue<String>

    @Before
    fun setUp() {
        val parent = folder.root
        val file = File(parent, "object-queue")
        val queueFile = Builder(file).build()
        queue = QueueFactory.FILE.create(queueFile, StringConverter())

        queue.add("one")
        queue.add("two")
        queue.add("three")
    }

    @Test
    fun size() {
        assertEquals(queue.size(), 3)
    }

    @Test
    fun peek() {
        assertEquals(queue.peek(), "one")
    }

    @Test
    fun peekMultiple() {
        assertEquals(queue.peek(2), listOf("one", "two"))
    }

    @Test
    fun peekMaxCanExceedQueueDepth() {
        assertEquals(queue.peek(6), listOf("one", "two", "three"))
    }

    @Test
    fun asList() {
        assertEquals(queue.asList(), listOf("one", "two", "three"))
    }

    @Test
    fun remove() {
        queue.remove()

        assertEquals(queue.asList(), listOf("two", "three"))
    }

    @Test
    fun removeMultiple() {
        queue.remove(2)

        assertEquals(queue.asList(), listOf("three"))
    }

    @Test
    fun clear() {
        queue.clear()

        assertEquals(queue.size(), 0)
    }

    @Test
    fun isEmpty() {
        assertFalse(queue.isEmpty)

        queue.clear()

        assertTrue(queue.isEmpty)
    }

    @Test
    fun testIterator() {
        val saw: MutableList<String> = ArrayList()
        for (pojo in queue) {
            saw.add(pojo)
        }
        assertEquals(saw, listOf("one", "two", "three"))
    }

    @Test
    fun testIteratorNextThrowsWhenEmpty() {
        queue.clear()
        val iterator: Iterator<String> = queue.iterator()

        try {
            iterator.next()
            fail()
        } catch (ignored: NoSuchElementException) {
        }
    }

    @Test
    fun testIteratorNextThrowsWhenExhausted() {
        val iterator: Iterator<String> = queue.iterator()
        iterator.next()
        iterator.next()
        iterator.next()

        try {
            iterator.next()
            fail()
        } catch (ignored: NoSuchElementException) {
        }
    }

    @Test
    fun testIteratorRemove() {
        val iterator = queue.iterator()

        iterator.next()
        iterator.remove()
        assertEquals(queue.asList(), listOf("two", "three"))

        iterator.next()
        iterator.remove()
        assertEquals(queue.asList(), listOf("three"))
    }

    @Test
    fun testIteratorRemoveDisallowsConcurrentModification() {
        val iterator = queue.iterator()
        iterator.next()
        queue.remove()

        try {
            iterator.remove()
            fail()
        } catch (ignored: ConcurrentModificationException) {
        }
    }

    @Test
    fun testIteratorHasNextDisallowsConcurrentModification() {
        val iterator: Iterator<String> = queue.iterator()
        iterator.next()
        queue.remove()

        try {
            iterator.hasNext()
            fail()
        } catch (ignored: ConcurrentModificationException) {
        }
    }

    @Test
    fun testIteratorDisallowsConcurrentModificationWithClear() {
        val iterator: Iterator<String> = queue.iterator()
        iterator.next()
        queue.clear()

        try {
            iterator.hasNext()
            fail()
        } catch (ignored: ConcurrentModificationException) {
        }
    }

    @Test
    fun testIteratorOnlyRemovesFromHead() {
        val iterator = queue.iterator()
        iterator.next()
        iterator.next()

        try {
            iterator.remove()
            fail()
        } catch (ex: UnsupportedOperationException) {
            assertEquals(ex.message, "Removal is only permitted from the head.")
        }
    }

    @Test
    fun iteratorThrowsIOException() {
        val parent = folder.root
        val file = File(parent, "object-queue")
        val queueFile = Builder(file).build()
        val queue = ObjectQueue.create(queueFile, object : Converter<Any> {
            override fun from(bytes: ByteArray): String {
                throw IOException()
            }

            override fun toStream(o: Any, bytes: OutputStream) {
            }
        })
        queue.add(Any())
        val iterator = queue.iterator()
        try {
            iterator.next()
            fail()
        } catch (ioe: Exception) {
            assertTrue(ioe is IOException)
        }
    }

    internal class StringConverter : Converter<String> {
        override fun from(bytes: ByteArray): String {
            return String(bytes, charset("UTF-8"))
        }

        override fun toStream(s: String, os: OutputStream) {
            os.write(s.toByteArray(charset("UTF-8")))
        }
    }
}

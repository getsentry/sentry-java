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

import io.sentry.cache.tape.QueueFile.Builder
import io.sentry.cache.tape.QueueFile.Element
import okio.BufferedSource
import okio.Okio
import org.junit.Assert
import org.junit.Assert.assertArrayEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.ArrayDeque
import java.util.Queue
import java.util.logging.Logger
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Tests for QueueFile.
 *
 * @author Bob Lee (bob@squareup.com)
 */
class QueueFileTest {
    private val headerLength = 32

    @get:Rule
    val folder = TemporaryFolder()
    private lateinit var file: File

    private fun newQueueFile(raf: RandomAccessFile): QueueFile {
        return QueueFile(this.file, raf, true, -1)
    }

    private fun newQueueFile(zero: Boolean = true, size: Int = -1): QueueFile {
        return Builder(file).zero(zero).size(size).build()
    }

    @Before
    fun setUp() {
        val parent = folder.root
        file = File(parent, "queue-file")
    }

    @Test
    fun testAddOneElement() {
        // This test ensures that we update 'first' correctly.
        var queue = newQueueFile()
        val expected = values[253]
        queue.add(expected)
        assertArrayEquals(queue.peek(), expected)
        queue.close()
        queue = newQueueFile()
        assertArrayEquals(queue.peek(), expected)
    }

    @Test
    fun testClearErases() {
        val queue = newQueueFile()
        val expected = values[253]
        queue.add(expected)

        // Confirm that the data was in the file before we cleared.
        val data = ByteArray(expected!!.size)
        queue.raf.seek(headerLength.toLong() + Element.HEADER_LENGTH)
        queue.raf.readFully(data, 0, expected.size)
        assertArrayEquals(data, expected)

        queue.clear()

        // Should have been erased.
        queue.raf.seek(headerLength.toLong() + Element.HEADER_LENGTH)
        queue.raf.readFully(data, 0, expected.size)
        assertArrayEquals(data, ByteArray(expected.size))
    }

    @Test
    fun testClearDoesNotCorrupt() {
        var queue = newQueueFile()
        val stuff = values[253]
        queue.add(stuff)
        queue.clear()

        queue = newQueueFile()
        assertTrue(queue.isEmpty)
        assertNull(queue.peek())

        queue.add(values[25])
        assertArrayEquals(queue.peek(), values[25])
    }

    @Test
    fun removeErasesEagerly() {
        val queue = newQueueFile()

        val firstStuff = values[127]
        queue.add(firstStuff)

        val secondStuff = values[253]
        queue.add(secondStuff)

        // Confirm that first stuff was in the file before we remove.
        val data = ByteArray(firstStuff!!.size)
        queue.raf.seek((headerLength + Element.HEADER_LENGTH).toLong())
        queue.raf.readFully(data, 0, firstStuff.size)
        assertArrayEquals(data, firstStuff)

        queue.remove()

        // Next record is intact
        assertArrayEquals(queue.peek(), secondStuff)

        // First should have been erased.
        queue.raf.seek((headerLength + Element.HEADER_LENGTH).toLong())
        queue.raf.readFully(data, 0, firstStuff.size)
        assertArrayEquals(data, ByteArray(firstStuff.size))
    }

    @Test
    fun testZeroSizeInHeaderThrows() {
        val emptyFile = RandomAccessFile(file, "rwd")
        emptyFile.setLength(QueueFile.INITIAL_LENGTH.toLong())
        emptyFile.channel.force(true)
        emptyFile.close()

        try {
            newQueueFile()
            fail("Should have thrown about bad header length")
        } catch (ex: IOException) {
            assertEquals(ex.message, "File is corrupt; length stored in header (0) is invalid.")
        }
    }

    @Test
    fun testSizeLessThanHeaderThrows() {
        val emptyFile = RandomAccessFile(file, "rwd")
        emptyFile.setLength(QueueFile.INITIAL_LENGTH.toLong())
        emptyFile.writeInt(-0x7fffffff)
        emptyFile.writeLong((headerLength - 1).toLong())
        emptyFile.channel.force(true)
        emptyFile.close()

        try {
            newQueueFile()
            fail()
        } catch (ex: IOException) {
            assertEquals(ex.message, "File is corrupt; length stored in header (31) is invalid.")
        }
    }

    @Test
    fun testNegativeSizeInHeaderThrows() {
        val emptyFile = RandomAccessFile(file, "rwd")
        emptyFile.seek(0)
        emptyFile.writeInt(-2147483648)
        emptyFile.setLength(QueueFile.INITIAL_LENGTH.toLong())
        emptyFile.channel.force(true)
        emptyFile.close()

        try {
            newQueueFile()
            fail("Should have thrown about bad header length")
        } catch (ex: IOException) {
            assertEquals(ex.message, "File is corrupt; length stored in header (0) is invalid.")
        }
    }

    @Test
    fun removeMultipleDoesNotCorrupt() {
        var queue = newQueueFile()
        for (i in 0..9) {
            queue.add(values[i])
        }

        queue.remove(1)
        assertEquals(queue.size(), 9)
        assertArrayEquals(queue.peek(), values[1])

        queue.remove(3)
        queue = newQueueFile()
        assertEquals(queue.size(), 6)
        assertArrayEquals(queue.peek(), values[4])

        queue.remove(6)
        assertTrue(queue.isEmpty)
        assertNull(queue.peek())
    }

    @Test
    fun removeDoesNotCorrupt() {
        var queue = newQueueFile()

        queue.add(values[127])
        val secondStuff = values[253]
        queue.add(secondStuff)
        queue.remove()

        queue = newQueueFile()
        assertArrayEquals(queue.peek(), secondStuff)
    }

    @Test
    fun removeFromEmptyFileThrows() {
        val queue = newQueueFile()

        try {
            queue.remove()
            fail("Should have thrown about removing from empty file.")
        } catch (ignored: NoSuchElementException) {
        }
    }

    @Test
    fun removeZeroFromEmptyFileDoesNothing() {
        val queue = newQueueFile()
        queue.remove(0)
        assertTrue(queue.isEmpty)
    }

    @Test
    fun removeNegativeNumberOfElementsThrows() {
        val queue = newQueueFile()
        queue.add(values[127])

        try {
            queue.remove(-1)
            fail("Should have thrown about removing negative number of elements.")
        } catch (ex: IllegalArgumentException) {
            assertEquals(ex.message, "Cannot remove negative (-1) number of elements.")
        }
    }

    @Test
    fun removeZeroElementsDoesNothing() {
        val queue = newQueueFile()
        queue.add(values[127])

        queue.remove(0)
        assertEquals(queue.size(), 1)
    }

    @Test
    fun removeBeyondQueueSizeElementsThrows() {
        val queue = newQueueFile()
        queue.add(values[127])

        try {
            queue.remove(10)
            fail("Should have thrown about removing too many elements.")
        } catch (ex: IllegalArgumentException) {
            assertEquals(ex.message, "Cannot remove more elements (10) than present in queue (1).")
        }
    }

    @Test
    fun removingBigDamnBlocksErasesEffectively() {
        val bigBoy = ByteArray(7000)
        var i = 0
        while (i < 7000) {
            System.arraycopy(values[100], 0, bigBoy, i, values[100]!!.size)
            i += 100
        }

        val queue = newQueueFile()

        queue.add(bigBoy)
        val secondStuff = values[123]
        queue.add(secondStuff)

        // Confirm that bigBoy was in the file before we remove.
        val data = ByteArray(bigBoy.size)
        queue.raf.seek((headerLength + Element.HEADER_LENGTH).toLong())
        queue.raf.readFully(data, 0, bigBoy.size)
        assertArrayEquals(data, bigBoy)

        queue.remove()

        // Next record is intact
        assertArrayEquals(queue.peek(), secondStuff)

        // First should have been erased.
        queue.raf.seek((headerLength + Element.HEADER_LENGTH).toLong())
        queue.raf.readFully(data, 0, bigBoy.size)
        assertArrayEquals(data, ByteArray(bigBoy.size))
    }

    @Test
    fun testAddAndRemoveElements() {
        val start = System.nanoTime()

        val expected: Queue<ByteArray?> = ArrayDeque()

        for (round in 0..4) {
            val queue = newQueueFile()
            for (i in 0 until N) {
                queue.add(values[i])
                expected.add(values[i])
            }

            // Leave N elements in round N, 15 total for 5 rounds. Removing all the
            // elements would be like starting with an empty queue.
            for (i in 0 until N - round - 1) {
                assertArrayEquals(queue.peek(), expected.remove())
                queue.remove()
            }
            queue.close()
        }

        // Remove and validate remaining 15 elements.
        val queue = newQueueFile()
        assertEquals(queue.size(), 15)
        assertEquals(queue.size(), expected.size)
        while (!expected.isEmpty()) {
            assertArrayEquals(queue.peek(), expected.remove())
            queue.remove()
        }
        queue.close()

        // length() returns 0, but I checked the size w/ 'ls', and it is correct.
        // assertEquals(65536, file.length());
        logger.info("Ran in " + ((System.nanoTime() - start) / 1000000) + "ms.")
    }

    @Test
    fun testFailedAdd() {
        var queueFile = newQueueFile()
        queueFile.add(values[253])
        queueFile.close()

        val braf = BrokenRandomAccessFile(file, "rwd")
        queueFile = newQueueFile(braf)

        try {
            queueFile.add(values[252])
            Assert.fail()
        } catch (e: IOException) { /* expected */
        }

        braf.rejectCommit = false

        // Allow a subsequent add to succeed.
        queueFile.add(values[251])

        queueFile.close()

        queueFile = newQueueFile()
        assertEquals(queueFile.size(), 2)
        assertArrayEquals(queueFile.peek(), values[253])
        queueFile.remove()
        assertArrayEquals(queueFile.peek(), values[251])
    }

    @Test
    fun testFailedRemoval() {
        var queueFile = newQueueFile()
        queueFile.add(values[253])
        queueFile.close()

        val braf = BrokenRandomAccessFile(file, "rwd")
        queueFile = newQueueFile(braf)

        try {
            queueFile.remove()
            fail()
        } catch (e: IOException) { /* expected */
        }

        queueFile.close()

        queueFile = newQueueFile()
        assertEquals(queueFile.size(), 1)
        assertArrayEquals(queueFile.peek(), values[253])

        queueFile.add(values[99])
        queueFile.remove()
        assertArrayEquals(queueFile.peek(), values[99])
    }

    @Test
    fun testFailedExpansion() {
        var queueFile = newQueueFile()
        queueFile.add(values[253])
        queueFile.close()

        val braf = BrokenRandomAccessFile(file, "rwd")
        queueFile = newQueueFile(braf)

        try {
            // This should trigger an expansion which should fail.
            queueFile.add(ByteArray(8000))
            fail()
        } catch (e: IOException) { /* expected */
        }

        queueFile.close()

        queueFile = newQueueFile()
        assertEquals(queueFile.size(), 1)
        assertArrayEquals(queueFile.peek(), values[253])
        assertEquals(queueFile.fileLength, 4096)

        queueFile.add(values[99])
        queueFile.remove()
        assertArrayEquals(queueFile.peek(), values[99])
    }

    @Test
    fun removingElementZeroesData() {
        val queueFile = newQueueFile(true)
        queueFile.add(values[4])
        queueFile.remove()
        queueFile.close()

        val source: BufferedSource = Okio.buffer(Okio.source(file))
        source.skip(headerLength.toLong())
        source.skip(Element.HEADER_LENGTH.toLong())
        assertEquals(source.readByteString(4).hex(), "00000000")
    }

    @Test
    fun removingElementDoesNotZeroData() {
        val queueFile = newQueueFile(false)
        queueFile.add(values[4])
        queueFile.remove()
        queueFile.close()

        val source: BufferedSource = Okio.buffer(Okio.source(file))
        source.skip(headerLength.toLong())
        source.skip(Element.HEADER_LENGTH.toLong())
        assertEquals(source.readByteString(4).hex(), "04030201")

        source.close()
    }

    /**
     * Exercise a bug where opening a queue whose first or last element's header
     * was non contiguous throws an [java.io.EOFException].
     */
    @Test
    fun testReadHeadersFromNonContiguousQueueWorks() {
        val queueFile = newQueueFile()

        // Fill the queue up to `length - 2` (i.e. remainingBytes() == 2).
        for (i in 0..14) {
            queueFile.add(values[N - 1])
        }
        queueFile.add(values[219])

        // Remove first item so we have room to add another one without growing the file.
        queueFile.remove()

        // Add any element element and close the queue.
        queueFile.add(values[6])
        val queueSize = queueFile.size()
        queueFile.close()

        // File should not be corrupted.
        val queueFile2 = newQueueFile()
        assertEquals(queueFile2.size(), queueSize)
    }

    @Test
    fun testIterator() {
        val data = values[10]

        for (i in 0..9) {
            val queueFile = newQueueFile()
            for (j in 0 until i) {
                queueFile.add(data)
            }

            var saw = 0
            for (element in queueFile) {
                assertArrayEquals(element, data)
                saw++
            }
            assertEquals(saw, i)
            queueFile.close()
            file!!.delete()
        }
    }

    @Test
    fun testIteratorNextThrowsWhenEmpty() {
        val queueFile = newQueueFile()

        val iterator: Iterator<ByteArray> = queueFile.iterator()

        try {
            iterator.next()
            fail()
        } catch (ignored: NoSuchElementException) {
        }
    }

    @Test
    fun testIteratorNextThrowsWhenExhausted() {
        val queueFile = newQueueFile()
        queueFile.add(values[0])

        val iterator: Iterator<ByteArray> = queueFile.iterator()
        iterator.next()

        try {
            iterator.next()
            fail()
        } catch (ignored: NoSuchElementException) {
        }
    }

    @Test
    fun testIteratorRemove() {
        val queueFile = newQueueFile()
        for (i in 0..14) {
            queueFile.add(values[i])
        }

        val iterator = queueFile.iterator()
        while (iterator.hasNext()) {
            iterator.next()
            iterator.remove()
        }

        assertTrue(queueFile.isEmpty)
    }

    @Test
    fun testIteratorRemoveDisallowsConcurrentModification() {
        val queueFile = newQueueFile()
        for (i in 0..14) {
            queueFile.add(values[i])
        }

        val iterator = queueFile.iterator()
        iterator.next()
        queueFile.remove()
        try {
            iterator.remove()
            fail()
        } catch (ignored: ConcurrentModificationException) {
        }
    }

    @Test
    fun testIteratorHasNextDisallowsConcurrentModification() {
        val queueFile = newQueueFile()
        for (i in 0..14) {
            queueFile.add(values[i])
        }

        val iterator: Iterator<ByteArray> = queueFile.iterator()
        iterator.next()
        queueFile.remove()
        try {
            iterator.hasNext()
            fail()
        } catch (ignored: ConcurrentModificationException) {
        }
    }

    @Test
    fun testIteratorDisallowsConcurrentModificationWithClear() {
        val queueFile = newQueueFile()
        for (i in 0..14) {
            queueFile.add(values[i])
        }

        val iterator: Iterator<ByteArray> = queueFile.iterator()
        iterator.next()
        queueFile.clear()
        try {
            iterator.hasNext()
            fail()
        } catch (ignored: ConcurrentModificationException) {
        }
    }

    @Test
    fun testIteratorOnlyRemovesFromHead() {
        val queueFile = newQueueFile()
        for (i in 0..14) {
            queueFile.add(values[i])
        }

        val iterator = queueFile.iterator()
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
        var queueFile = newQueueFile()
        queueFile.add(values[253])
        queueFile.close()

        class BrokenRandomAccessFile(file: File?, mode: String?) : RandomAccessFile(file, mode) {
            var fail: Boolean = false

            override fun write(b: ByteArray, off: Int, len: Int) {
                if (fail) {
                    throw IOException()
                }
                super.write(b, off, len)
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (fail) {
                    throw IOException()
                }
                return super.read(b, off, len)
            }
        }

        val braf = BrokenRandomAccessFile(file, "rwd")
        queueFile = newQueueFile(braf)
        val iterator = queueFile.iterator()

        braf.fail = true
        try {
            iterator.next()
            fail()
        } catch (ioe: Exception) {
            assertTrue(ioe is IOException)
        }

        braf.fail = false
        iterator.next()

        braf.fail = true
        try {
            iterator.remove()
            fail()
        } catch (ioe: Exception) {
            assertTrue(ioe is IOException)
        }
    }

    @Test
    fun queueToString() {
        val queueFile = newQueueFile()
        for (i in 0..14) {
            queueFile.add(values[i])
        }

        assertTrue(
            queueFile.toString().contains(
                "zero=true, length=4096," +
                    " size=15," +
                    " first=Element[position=32, length=0], last=Element[position=179, length=14]}"
            )
        )
    }

    @Test
    fun `wraps elements around when size is specified`() {
        val queue = newQueueFile(size = 2)

        for (i in 0 until 3) {
            queue.add(values[i])
        }

        // Confirm that first element now is values[1] in the file after wrapping
        assertArrayEquals(queue.peek(), values[1])
        queue.remove()

        // Confirm that first element now is values[2] in the file after wrapping
        assertArrayEquals(queue.peek(), values[2])
    }

    /**
     * A RandomAccessFile that can break when you go to write the COMMITTED
     * status.
     */
    internal class BrokenRandomAccessFile(file: File?, mode: String?) : RandomAccessFile(file, mode) {
        var rejectCommit: Boolean = true
        override fun write(b: ByteArray, off: Int, len: Int) {
            if (rejectCommit && filePointer == 0L) {
                throw IOException("No commit for you!")
            }
            super.write(b, off, len)
        }
    }

    companion object {
        private val logger: Logger = Logger.getLogger(
            QueueFileTest::class.java.name
        )

        /**
         * Takes up 33401 bytes in the queue (N*(N+1)/2+4*N). Picked 254 instead of 255 so that the number
         * of bytes isn't a multiple of 4.
         */
        private const val N = 254
        private val values = Array(N) { i ->
            val value = ByteArray(i)
            // Example: values[3] = { 3, 2, 1 }
            for (ii in 0 until i) value[ii] = (i - ii).toByte()
            value
        }
    }
}

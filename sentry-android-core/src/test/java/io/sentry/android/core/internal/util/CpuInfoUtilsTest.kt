package io.sentry.android.core.internal.util

import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class CpuInfoUtilsTest {
    private lateinit var cpuDirs: File
    private lateinit var ciu: CpuInfoUtils

    private fun populateCpuFiles(values: List<String>) =
        values.mapIndexed { i, v ->
            val cpuMaxFreqFile = File(cpuDirs, "cpu$i${File.separator}${CpuInfoUtils.CPUINFO_MAX_FREQ_PATH}")
            cpuMaxFreqFile.parentFile?.mkdirs()
            cpuMaxFreqFile.writeText(v)
            cpuMaxFreqFile
        }

    @BeforeTest
    fun `set up`() {
        val tmpFolder = TemporaryFolder()
        tmpFolder.create()
        cpuDirs = tmpFolder.newFolder("test")
        ciu =
            spy(CpuInfoUtils.getInstance()) {
                whenever(it.systemCpuPath).thenReturn(cpuDirs.absolutePath)
            }
    }

    @AfterTest
    fun clear() {
        cpuDirs.deleteRecursively()
        ciu.clear()
    }

    @Test
    fun `readMaxFrequencies reads Khz and returns Mhz`() {
        val expected = listOf(0, 1, 2, 3)
        populateCpuFiles(listOf("0", "1000", "2000", "3000"))
        // The order given by readFiles() is not guaranteed to be sorted, so we compare in this way
        assert(expected.containsAll(ciu.readMaxFrequencies()))
        assert(ciu.readMaxFrequencies().containsAll(expected))
    }

    @Test
    fun `readMaxFrequencies returns empty list for non existent or invalid files`() {
        // Empty list if no cpu file exists
        assert(emptyList<String>() == ciu.readMaxFrequencies())
        val files = populateCpuFiles(listOf("1000", "2000", "3000"))
        files.forEach {
            it.setReadable(false)
        }
        // Empty list for unreadable files
        assert(emptyList<String>() == ciu.readMaxFrequencies())
    }

    @Test
    fun `readMaxFrequencies skips invalid values`() {
        val expected = listOf(2, 3)
        populateCpuFiles(listOf("invalid", "2000", "3000", "another"))

        // The order given by readFiles() is not guaranteed to be sorted, so we compare in this way
        assert(expected.containsAll(ciu.readMaxFrequencies()))
        assert(ciu.readMaxFrequencies().containsAll(expected))
    }

    @Test
    fun `readMaxFrequencies caches values if they are valid`() {
        // First call with invalid data
        ciu.readMaxFrequencies()
        val expected = listOf(0, 1, 2, 3)
        populateCpuFiles(listOf("0", "1000", "2000", "3000"))

        // Second and third call with valid data will be read only once
        // The order given by readFiles() is not guaranteed to be sorted, so we compare in this way
        assert(expected.containsAll(ciu.readMaxFrequencies()))
        assert(ciu.readMaxFrequencies().containsAll(expected))

        verify(ciu, times(2)).systemCpuPath
    }
}

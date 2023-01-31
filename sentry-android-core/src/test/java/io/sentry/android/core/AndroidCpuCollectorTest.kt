package io.sentry.android.core

import android.os.Build
import io.sentry.ILogger
import io.sentry.PerformanceCollectionData
import io.sentry.test.getCtor
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AndroidCpuCollectorTest {

    private val className = "io.sentry.android.core.AndroidCpuCollector"
    private val ctorTypes = arrayOf(ILogger::class.java, BuildInfoProvider::class.java)
    private val fixture = Fixture()

    private class Fixture {
        private val mockBuildInfoProvider = mock<BuildInfoProvider>()
        init {
            whenever(mockBuildInfoProvider.sdkInfoVersion).thenReturn(Build.VERSION_CODES.LOLLIPOP)
        }
        fun getSut(buildInfoProvider: BuildInfoProvider = mockBuildInfoProvider) =
            AndroidCpuCollector(mock(), buildInfoProvider)
    }

    @Test
    fun `when null param is provided, invalid argument is thrown`() {
        val ctor = className.getCtor(ctorTypes)

        assertFailsWith<IllegalArgumentException> {
            ctor.newInstance(arrayOf(null, mock<BuildInfoProvider>()))
        }
        assertFailsWith<IllegalArgumentException> {
            ctor.newInstance(arrayOf(mock<ILogger>(), null))
        }
    }

    @Test
    fun `collect works only after setup`() {
        val data = PerformanceCollectionData()
        fixture.getSut().collect(data)
        data.commitData()
        assertNull(data.cpuData)
    }

    @Test
    fun `when collect cpu is collected`() {
        val data = PerformanceCollectionData()
        val collector = fixture.getSut()
        collector.setup()
        collector.collect(data)
        data.commitData()
        val cpuData = data.cpuData
        assertNotNull(cpuData)
        assertNotEquals(0.0, cpuData.cpuUsagePercentage)
        assertNotEquals(0, cpuData.timestampMillis)
    }

    @Test
    fun `collector works only on api 21+`() {
        val data = PerformanceCollectionData()
        val mockBuildInfoProvider = mock<BuildInfoProvider>()
        whenever(mockBuildInfoProvider.sdkInfoVersion).thenReturn(Build.VERSION_CODES.KITKAT)
        val collector = fixture.getSut(mockBuildInfoProvider)
        collector.setup()
        collector.collect(data)
        data.commitData()
        assertNull(data.cpuData)
    }
}

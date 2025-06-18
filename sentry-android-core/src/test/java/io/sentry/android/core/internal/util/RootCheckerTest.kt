package io.sentry.android.core.internal.util

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import io.sentry.ILogger
import io.sentry.android.core.BuildInfoProvider
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RootCheckerTest {
    private class Fixture {
        val context = mock<Context>()
        val logger = mock<ILogger>()
        val buildInfoProvider = mock<BuildInfoProvider>()
        val packageManager = mock<PackageManager>()
        val runtime = mock<Runtime>()

        fun getSut(
            tags: String? = "abc",
            rootFiles: Array<String> = arrayOf(),
            rootPackages: Array<String> = arrayOf(),
        ): RootChecker {
            whenever(buildInfoProvider.buildTags).thenReturn(tags)
            whenever(context.packageManager).thenReturn(packageManager)

            return RootChecker(
                context,
                buildInfoProvider,
                logger,
                rootFiles,
                rootPackages,
                runtime,
            )
        }
    }

    private val fixture = Fixture()

    @Test
    fun `When build tags contains test-keys, device is rooted`() {
        assertTrue(fixture.getSut("test-keys").isDeviceRooted)
    }

    @Test
    fun `When build tags do not contain test-keys, device is not rooted`() {
        assertFalse(fixture.getSut().isDeviceRooted)
    }

    @Test
    fun `When build tags is null, device is not rooted`() {
        assertFalse(fixture.getSut(null).isDeviceRooted)
    }

    @Test
    fun `When root files exist, device is rooted`() {
        val file = Files.createTempFile("here", "test").toFile()
        val rootFiles = arrayOf(file.absolutePath)

        assertTrue(file.exists())
        assertTrue(fixture.getSut(rootFiles = rootFiles).isDeviceRooted)

        file.delete()
        assertFalse(file.exists())
    }

    @Test
    fun `When root files do not exist, device is not rooted`() {
        val file = File("test")
        val rootFiles = arrayOf(file.absolutePath)

        assertFalse(file.exists())

        assertFalse(fixture.getSut(rootFiles = rootFiles).isDeviceRooted)
    }

    @Test
    fun `When root packages exist, device is rooted`() {
        val rootPackages = arrayOf("com.devadvance.rootcloak")
        val packageInfo = mock<PackageInfo>()

        whenever(fixture.packageManager.getPackageInfo(eq("com.devadvance.rootcloak"), any<Int>())).thenReturn(packageInfo)
        whenever(
            fixture.packageManager.getPackageInfo(eq("com.devadvance.rootcloak"), any<PackageManager.PackageInfoFlags>()),
        ).thenReturn(packageInfo)

        assertTrue(fixture.getSut(rootPackages = rootPackages).isDeviceRooted)
    }

    @Test
    fun `When root packages do not exist, device is not rooted`() {
        val rootPackages = arrayOf("com.devadvance.rootcloak")

        whenever(
            fixture.packageManager.getPackageInfo(eq("com.devadvance.rootcloak"), any<Int>()),
        ).thenThrow(PackageManager.NameNotFoundException())
        whenever(
            fixture.packageManager.getPackageInfo(eq("com.devadvance.rootcloak"), any<PackageManager.PackageInfoFlags>()),
        ).thenThrow(PackageManager.NameNotFoundException())

        assertFalse(fixture.getSut(rootPackages = rootPackages).isDeviceRooted)
    }

    @Test
    fun `When runtime returns a valid su message, device is rooted`() {
        val process = mock<Process>()

        whenever(process.inputStream).thenReturn("hi".byteInputStream(Charsets.UTF_8))
        whenever(fixture.runtime.exec(eq(arrayOf("/system/xbin/which", "su")))).thenReturn(process)

        assertTrue(fixture.getSut().isDeviceRooted)
        verify(process).destroy()
    }

    @Test
    fun `When inputStream returns null, device is not rooted`() {
        val process = mock<Process>()
        val inputStream = ByteArrayInputStream(ByteArray(0))

        whenever(process.inputStream).thenReturn(inputStream)
        whenever(fixture.runtime.exec(eq(arrayOf("/system/xbin/which", "su")))).thenReturn(process)

        assertFalse(fixture.getSut().isDeviceRooted)
    }

    @Test
    fun `When inputStream throws IOException, device is not rooted`() {
        val process = mock<Process>()
        val inputStream = mock<InputStream>()

        whenever(inputStream.read(any(), any(), any())).thenThrow(IOException())
        whenever(process.inputStream).thenReturn(inputStream)
        whenever(fixture.runtime.exec(eq(arrayOf("/system/xbin/which", "su")))).thenReturn(process)

        assertFalse(fixture.getSut().isDeviceRooted)
    }

    @Test
    fun `When runtime throws IOException, device is not rooted`() {
        whenever(fixture.runtime.exec(eq(arrayOf("/system/xbin/which", "su")))).thenThrow(IOException())

        assertFalse(fixture.getSut().isDeviceRooted)
    }
}

package io.sentry.cache

import io.sentry.Breadcrumb
import io.sentry.DateUtils
import io.sentry.ISentryExecutorService
import io.sentry.JsonDeserializer
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.SpanContext
import io.sentry.SpanId
import io.sentry.cache.PersistingScopeObserver.BREADCRUMBS_FILENAME
import io.sentry.cache.PersistingScopeObserver.CONTEXTS_FILENAME
import io.sentry.cache.PersistingScopeObserver.EXTRAS_FILENAME
import io.sentry.cache.PersistingScopeObserver.FINGERPRINT_FILENAME
import io.sentry.cache.PersistingScopeObserver.LEVEL_FILENAME
import io.sentry.cache.PersistingScopeObserver.REQUEST_FILENAME
import io.sentry.cache.PersistingScopeObserver.TAGS_FILENAME
import io.sentry.cache.PersistingScopeObserver.TRACE_FILENAME
import io.sentry.cache.PersistingScopeObserver.TRANSACTION_FILENAME
import io.sentry.cache.PersistingScopeObserver.USER_FILENAME
import io.sentry.protocol.App
import io.sentry.protocol.Browser
import io.sentry.protocol.Contexts
import io.sentry.protocol.Device
import io.sentry.protocol.Device.DeviceOrientation.PORTRAIT
import io.sentry.protocol.Gpu
import io.sentry.protocol.OperatingSystem
import io.sentry.protocol.Request
import io.sentry.protocol.SentryId
import io.sentry.protocol.User
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.kotlin.mock
import java.util.concurrent.Callable
import java.util.concurrent.Future
import kotlin.test.Test
import kotlin.test.assertEquals

class StoreScopeValue<T>(private val store: PersistingScopeObserver.(T) -> Unit) {
    operator fun invoke(value: T, observer: PersistingScopeObserver) {
        observer.store(value)
    }
}

class DeleteScopeValue(private val delete: PersistingScopeObserver.() -> Unit) {
    operator fun invoke(observer: PersistingScopeObserver) {
        observer.delete()
    }
}

@RunWith(Parameterized::class)
class PersistingScopeObserverTest<T, R>(
    private val entity: T,
    private val store: StoreScopeValue<T>,
    private val filename: String,
    private val delete: DeleteScopeValue,
    private val deletedEntity: T?,
    private val elementDeserializer: JsonDeserializer<R>?
) {

    @get:Rule
    val tmpDir = TemporaryFolder()

    class Fixture {

        private val mockExecutorService = object : ISentryExecutorService {
            override fun submit(runnable: Runnable): Future<*> {
                runnable.run()
                return mock()
            }

            override fun <T> submit(callable: Callable<T>): Future<T> = mock()
            override fun schedule(runnable: Runnable, delayMillis: Long): Future<*> = mock()
            override fun close(timeoutMillis: Long) {}
        }

        val options = SentryOptions()

        fun getSut(cacheDir: TemporaryFolder): PersistingScopeObserver {
            options.run {
                executorService = mockExecutorService
                cacheDirPath = cacheDir.newFolder().absolutePath
            }
            return PersistingScopeObserver(options)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `store and delete scope value`() {
        val sut = fixture.getSut(tmpDir)
        store(entity, sut)

        val persisted = read()
        assertEquals(entity, persisted)

        delete(sut)
        val persistedAfterDeletion = read()
        assertEquals(deletedEntity, persistedAfterDeletion)
    }

    private fun read(): T? = PersistingScopeObserver.read(
        fixture.options,
        filename,
        entity!!::class.java,
        elementDeserializer
    )

    companion object {

        private fun user(): Array<Any?> = arrayOf(
            User().apply {
                email = "user@user.com"
                id = "c4d61c1b-c144-431e-868f-37a46be5e5f2"
                ipAddress = "192.168.0.1"
            },
            StoreScopeValue<User> { setUser(it) },
            USER_FILENAME,
            DeleteScopeValue { setUser(null) },
            null,
            null
        )

        private fun breadcrumbs(): Array<Any?> = arrayOf(
            listOf(
                Breadcrumb.navigation("one", "two"),
                Breadcrumb.userInteraction("click", "viewId", "viewClass")
            ),
            StoreScopeValue<List<Breadcrumb>> { setBreadcrumbs(it) },
            BREADCRUMBS_FILENAME,
            DeleteScopeValue { setBreadcrumbs(emptyList()) },
            emptyList<Breadcrumb>(),
            Breadcrumb.Deserializer()
        )

        private fun tags(): Array<Any?> = arrayOf(
            mapOf(
                "one" to "two",
                "tag" to "none"
            ),
            StoreScopeValue<Map<String, String>> { setTags(it) },
            TAGS_FILENAME,
            DeleteScopeValue { setTags(emptyMap()) },
            emptyMap<String, String>(),
            null
        )

        private fun extras(): Array<Any?> = arrayOf(
            mapOf(
                "one" to listOf("thing1", "thing2"),
                "two" to 2,
                "three" to 3.2
            ),
            StoreScopeValue<Map<String, Any>> { setExtras(it) },
            EXTRAS_FILENAME,
            DeleteScopeValue { setExtras(emptyMap()) },
            emptyMap<String, Any>(),
            null
        )

        private fun request(): Array<Any?> = arrayOf(
            Request().apply {
                url = "https://google.com"
                method = "GET"
                queryString = "search"
                cookies = "d84f4cfc-5310-4818-ad4f-3f8d22ceaca8"
                fragment = "fragment"
                bodySize = 1000
            },
            StoreScopeValue<Request> { setRequest(it) },
            REQUEST_FILENAME,
            DeleteScopeValue { setRequest(null) },
            null,
            null
        )

        private fun fingerprint(): Array<Any?> = arrayOf(
            listOf("finger", "print"),
            StoreScopeValue<List<String>> { setFingerprint(it) },
            FINGERPRINT_FILENAME,
            DeleteScopeValue { setFingerprint(emptyList()) },
            emptyList<String>(),
            null
        )

        private fun level(): Array<Any?> = arrayOf(
            SentryLevel.WARNING,
            StoreScopeValue<SentryLevel> { setLevel(it) },
            LEVEL_FILENAME,
            DeleteScopeValue { setLevel(null) },
            null,
            null
        )

        private fun transaction(): Array<Any?> = arrayOf(
            "MainActivity",
            StoreScopeValue<String> { setTransaction(it) },
            TRANSACTION_FILENAME,
            DeleteScopeValue { setTransaction(null) },
            null,
            null
        )

        private fun trace(): Array<Any?> = arrayOf(
            SpanContext(SentryId(), SpanId(), "ui.load", null, null),
            StoreScopeValue<SpanContext> { setTrace(it) },
            TRACE_FILENAME,
            DeleteScopeValue { setTrace(null) },
            null,
            null
        )

        private fun contexts(): Array<Any?> = arrayOf(
            Contexts().apply {
                setApp(App().apply {
                    appBuild = "1"
                    appIdentifier = "io.sentry.sample"
                    appName = "sample"
                    appStartTime = DateUtils.getCurrentDateTime()
                    buildType = "debug"
                    appVersion = "2021"
                })
                setBrowser(Browser().apply {
                    name = "Chrome"
                })
                setDevice(Device().apply {
                    name = "Pixel 3XL"
                    manufacturer = "Google"
                    brand = "Pixel"
                    family = "Pixels"
                    model = "3XL"
                    isCharging = true
                    isOnline = true
                    orientation = PORTRAIT
                    isSimulator = false
                    memorySize = 4096
                    freeMemory = 2048
                    usableMemory = 1536
                    isLowMemory = false
                    storageSize = 64000
                    freeStorage = 32000
                    screenWidthPixels = 1080
                    screenHeightPixels = 1920
                    screenDpi = 446
                    connectionType = "wifi"
                    batteryTemperature = 37.0f
                    batteryLevel = 92.0f
                    locale = "en-US"
                })
                setGpu(Gpu().apply {
                    vendorName = "GeForce"
                    memorySize = 1000
                })
                setOperatingSystem(OperatingSystem().apply {
                    isRooted = true
                    build = "2021.123_alpha"
                    name = "Android"
                    version = "12"
                })
            },
            StoreScopeValue<Contexts> { setContexts(it) },
            CONTEXTS_FILENAME,
            DeleteScopeValue { setContexts(Contexts()) },
            Contexts(),
            null
        )

        @JvmStatic
        @Parameterized.Parameters(name = "{2}")
        fun data(): Collection<Array<Any?>> {
            return listOf(
                user(),
                breadcrumbs(),
                tags(),
                extras(),
                request(),
                fingerprint(),
                level(),
                transaction(),
                trace(),
                contexts()
            )
        }
    }
}

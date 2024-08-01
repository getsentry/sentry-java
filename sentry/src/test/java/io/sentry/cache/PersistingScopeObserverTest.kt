package io.sentry.cache

import io.sentry.Breadcrumb
import io.sentry.DateUtils
import io.sentry.JsonDeserializer
import io.sentry.Scope
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.SpanContext
import io.sentry.SpanId
import io.sentry.cache.PersistingScopeObserver.BREADCRUMBS_FILENAME
import io.sentry.cache.PersistingScopeObserver.CONTEXTS_FILENAME
import io.sentry.cache.PersistingScopeObserver.EXTRAS_FILENAME
import io.sentry.cache.PersistingScopeObserver.FINGERPRINT_FILENAME
import io.sentry.cache.PersistingScopeObserver.LEVEL_FILENAME
import io.sentry.cache.PersistingScopeObserver.REPLAY_FILENAME
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
import io.sentry.test.ImmediateExecutorService
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.test.Test
import kotlin.test.assertEquals

class StoreScopeValue<T>(private val store: PersistingScopeObserver.(T, Scope) -> Unit) {
    operator fun invoke(value: T, observer: PersistingScopeObserver, scope: Scope) {
        observer.store(value, scope)
    }
}

class DeleteScopeValue(private val delete: PersistingScopeObserver.(Scope) -> Unit) {
    operator fun invoke(observer: PersistingScopeObserver, scope: Scope) {
        observer.delete(scope)
    }
}

class DeletedEntityProvider<T>(private val provider: (Scope) -> T?) {
    operator fun invoke(scope: Scope): T? {
        return provider(scope)
    }
}

@RunWith(Parameterized::class)
class PersistingScopeObserverTest<T, R>(
    private val entity: T,
    private val store: StoreScopeValue<T>,
    private val filename: String,
    private val delete: DeleteScopeValue,
    private val deletedEntity: DeletedEntityProvider<T>,
    private val elementDeserializer: JsonDeserializer<R>?
) {

    @get:Rule
    val tmpDir = TemporaryFolder()

    class Fixture {

        val options = SentryOptions()
        val scope = Scope(options)

        fun getSut(cacheDir: TemporaryFolder): PersistingScopeObserver {
            options.run {
                executorService = ImmediateExecutorService()
                cacheDirPath = cacheDir.newFolder().absolutePath
            }
            return PersistingScopeObserver(options)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `store and delete scope value`() {
        val sut = fixture.getSut(tmpDir)
        store(entity, sut, fixture.scope)

        val persisted = read()
        assertEquals(entity, persisted)

        delete(sut, fixture.scope)
        val persistedAfterDeletion = read()
        assertEquals(deletedEntity(fixture.scope), persistedAfterDeletion)
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
            StoreScopeValue<User> { user, _ -> setUser(user) },
            USER_FILENAME,
            DeleteScopeValue { setUser(null) },
            DeletedEntityProvider { null },
            null
        )

        private fun breadcrumbs(): Array<Any?> = arrayOf(
            listOf(
                Breadcrumb.navigation("one", "two"),
                Breadcrumb.userInteraction("click", "viewId", "viewClass")
            ),
            StoreScopeValue<List<Breadcrumb>> { breadcrumbs, _ -> setBreadcrumbs(breadcrumbs) },
            BREADCRUMBS_FILENAME,
            DeleteScopeValue { setBreadcrumbs(emptyList()) },
            DeletedEntityProvider { emptyList<Breadcrumb>() },
            Breadcrumb.Deserializer()
        )

        private fun tags(): Array<Any?> = arrayOf(
            mapOf(
                "one" to "two",
                "tag" to "none"
            ),
            StoreScopeValue<Map<String, String>> { tags, _ -> setTags(tags) },
            TAGS_FILENAME,
            DeleteScopeValue { setTags(emptyMap()) },
            DeletedEntityProvider { emptyMap<String, String>() },
            null
        )

        private fun extras(): Array<Any?> = arrayOf(
            mapOf(
                "one" to listOf("thing1", "thing2"),
                "two" to 2,
                "three" to 3.2
            ),
            StoreScopeValue<Map<String, Any>> { extras, _ -> setExtras(extras) },
            EXTRAS_FILENAME,
            DeleteScopeValue { setExtras(emptyMap()) },
            DeletedEntityProvider { emptyMap<String, Any>() },
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
            StoreScopeValue<Request> { request, _ -> setRequest(request) },
            REQUEST_FILENAME,
            DeleteScopeValue { setRequest(null) },
            DeletedEntityProvider { null },
            null
        )

        private fun fingerprint(): Array<Any?> = arrayOf(
            listOf("finger", "print"),
            StoreScopeValue<List<String>> { fingerprint, _ -> setFingerprint(fingerprint) },
            FINGERPRINT_FILENAME,
            DeleteScopeValue { setFingerprint(emptyList()) },
            DeletedEntityProvider { emptyList<String>() },
            null
        )

        private fun level(): Array<Any?> = arrayOf(
            SentryLevel.WARNING,
            StoreScopeValue<SentryLevel> { level, _ -> setLevel(level) },
            LEVEL_FILENAME,
            DeleteScopeValue { setLevel(null) },
            DeletedEntityProvider { null },
            null
        )

        private fun transaction(): Array<Any?> = arrayOf(
            "MainActivity",
            StoreScopeValue<String> { transaction, _ -> setTransaction(transaction) },
            TRANSACTION_FILENAME,
            DeleteScopeValue { setTransaction(null) },
            DeletedEntityProvider { null },
            null
        )

        private fun trace(): Array<Any?> = arrayOf(
            SpanContext(SentryId(), SpanId(), "ui.load", null, null),
            StoreScopeValue<SpanContext> { trace, scope -> setTrace(trace, scope) },
            TRACE_FILENAME,
            DeleteScopeValue { scope -> setTrace(null, scope) },
            DeletedEntityProvider { scope -> scope.propagationContext.toSpanContext() },
            null
        )

        private fun contexts(): Array<Any?> = arrayOf(
            Contexts().apply {
                setApp(
                    App().apply {
                        appBuild = "1"
                        appIdentifier = "io.sentry.sample"
                        appName = "sample"
                        appStartTime = DateUtils.getCurrentDateTime()
                        buildType = "debug"
                        appVersion = "2021"
                    }
                )
                setBrowser(
                    Browser().apply {
                        name = "Chrome"
                    }
                )
                setDevice(
                    Device().apply {
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
                    }
                )
                setGpu(
                    Gpu().apply {
                        vendorName = "GeForce"
                        memorySize = 1000
                    }
                )
                setOperatingSystem(
                    OperatingSystem().apply {
                        isRooted = true
                        build = "2021.123_alpha"
                        name = "Android"
                        version = "12"
                    }
                )
            },
            StoreScopeValue<Contexts> { contexts, _ -> setContexts(contexts) },
            CONTEXTS_FILENAME,
            DeleteScopeValue { setContexts(Contexts()) },
            DeletedEntityProvider { Contexts() },
            null
        )

        private fun replayId(): Array<Any?> = arrayOf(
            "64cf554cc8d74c6eafa3e08b7c984f6d",
            StoreScopeValue<String> { replayId, _ -> setReplayId(SentryId(replayId)) },
            REPLAY_FILENAME,
            DeleteScopeValue { setReplayId(SentryId.EMPTY_ID) },
            DeletedEntityProvider { SentryId.EMPTY_ID.toString() },
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
                contexts(),
                replayId()
            )
        }
    }
}

package io.sentry.cache

import io.sentry.SentryOptions
import io.sentry.cache.PersistingOptionsObserver.DIST_FILENAME
import io.sentry.cache.PersistingOptionsObserver.ENVIRONMENT_FILENAME
import io.sentry.cache.PersistingOptionsObserver.PROGUARD_UUID_FILENAME
import io.sentry.cache.PersistingOptionsObserver.RELEASE_FILENAME
import io.sentry.cache.PersistingOptionsObserver.REPLAY_ERROR_SAMPLE_RATE_FILENAME
import io.sentry.cache.PersistingOptionsObserver.SDK_VERSION_FILENAME
import io.sentry.cache.PersistingOptionsObserver.TAGS_FILENAME
import io.sentry.protocol.SdkVersion
import io.sentry.test.ImmediateExecutorService
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.test.Test
import kotlin.test.assertEquals

class StoreOptionsValue<T>(private val store: PersistingOptionsObserver.(T) -> Unit) {
    operator fun invoke(value: T, observer: PersistingOptionsObserver) {
        observer.store(value)
    }
}

class DeleteOptionsValue(private val delete: PersistingOptionsObserver.() -> Unit) {
    operator fun invoke(observer: PersistingOptionsObserver) {
        observer.delete()
    }
}

class ReadOptionsValue<T>(private val read: (options: SentryOptions) -> T) {
    operator fun invoke(options: SentryOptions) = read(options)
}

@RunWith(Parameterized::class)
class PersistingOptionsObserverTest<T>(
    private val entity: T,
    private val store: StoreOptionsValue<T>,
    private val filename: String,
    private val delete: DeleteOptionsValue,
    private val deletedEntity: T?,
    private val read: ReadOptionsValue<T>?
) {

    @get:Rule
    val tmpDir = TemporaryFolder()

    class Fixture {

        val options = SentryOptions()

        fun getSut(cacheDir: TemporaryFolder): PersistingOptionsObserver {
            options.run {
                executorService = ImmediateExecutorService()
                cacheDirPath = cacheDir.newFolder().absolutePath
            }
            return PersistingOptionsObserver(options)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `store and delete scope value`() {
        val sut = fixture.getSut(tmpDir)
        store(entity, sut)

        val persisted = read?.invoke(fixture.options) ?: read()
        assertEquals(entity, persisted)

        delete(sut)
        val persistedAfterDeletion = read()
        assertEquals(deletedEntity, persistedAfterDeletion)
    }

    private fun read(): T? = PersistingOptionsObserver.read(
        fixture.options,
        filename,
        entity!!::class.java
    )

    companion object {

        private fun release(): Array<Any?> = arrayOf(
            "io.sentry.sample@1.1.0+23",
            StoreOptionsValue<String> { setRelease(it) },
            RELEASE_FILENAME,
            DeleteOptionsValue { setRelease(null) },
            null,
            null
        )

        private fun proguardUuid(): Array<Any?> = arrayOf(
            "8a258c81-641d-4e54-b06e-a0f56b1ee2ef",
            StoreOptionsValue<String> { setProguardUuid(it) },
            PROGUARD_UUID_FILENAME,
            DeleteOptionsValue { setProguardUuid(null) },
            null,
            null
        )

        private fun sdkVersion(): Array<Any?> = arrayOf(
            SdkVersion("sentry.java.android", "6.13.0"),
            StoreOptionsValue<SdkVersion> { setSdkVersion(it) },
            SDK_VERSION_FILENAME,
            DeleteOptionsValue { setSdkVersion(null) },
            null,
            null
        )

        private fun dist(): Array<Any?> = arrayOf(
            "223",
            StoreOptionsValue<String> { setDist(it) },
            DIST_FILENAME,
            DeleteOptionsValue { setDist(null) },
            null,
            null
        )

        private fun environment(): Array<Any?> = arrayOf(
            "debug",
            StoreOptionsValue<String> { setEnvironment(it) },
            ENVIRONMENT_FILENAME,
            DeleteOptionsValue { setEnvironment(null) },
            null,
            null
        )

        private fun tags(): Array<Any?> = arrayOf(
            mapOf(
                "one" to "two",
                "tag" to "none"
            ),
            StoreOptionsValue<Map<String, String>> { setTags(it) },
            TAGS_FILENAME,
            DeleteOptionsValue { setTags(emptyMap()) },
            emptyMap<String, String>(),
            null
        )

        private fun replaysErrorSampleRate(): Array<Any?> = arrayOf(
            0.5,
            StoreOptionsValue<Double> { setReplayErrorSampleRate(it) },
            REPLAY_ERROR_SAMPLE_RATE_FILENAME,
            DeleteOptionsValue { setReplayErrorSampleRate(null) },
            null,
            ReadOptionsValue {
                PersistingOptionsObserver.read(
                    it,
                    REPLAY_ERROR_SAMPLE_RATE_FILENAME,
                    String::class.java
                )!!.toDouble()
            }
        )

        @JvmStatic
        @Parameterized.Parameters(name = "{2}")
        fun data(): Collection<Array<Any?>> {
            return listOf(
                release(),
                proguardUuid(),
                dist(),
                environment(),
                sdkVersion(),
                tags(),
                replaysErrorSampleRate()
            )
        }
    }
}

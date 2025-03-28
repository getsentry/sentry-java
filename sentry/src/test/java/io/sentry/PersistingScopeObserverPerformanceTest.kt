package io.sentry

import io.sentry.cache.PersistingScopeObserver
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PersistingScopeObserverPerformanceTest {

    @get:Rule
    val tmpDir = TemporaryFolder()

    class Fixture {

        val options = SentryOptions()
        val scope = Scope(options)

        fun getSut(cacheDir: TemporaryFolder): PersistingScopeObserver {
            options.run {
                executorService = SentryExecutorService()
                cacheDirPath = cacheDir.newFolder().absolutePath
            }
            return PersistingScopeObserver(options)
        }
    }

    private val fixture = Fixture()

    @Test
    fun test() {
        val sut = fixture.getSut(tmpDir)

        var oldDuration = 0L
        var newDuration = 0L

        val crumb = Breadcrumb.http("https://example.com", "GET", 200)

        for (i in 0..100) {
            val oldStart = System.currentTimeMillis()
            for (j in 0..100) {
                sut.addBreadcrumb(crumb)
            }
            oldDuration += System.currentTimeMillis() - oldStart

            val newStart = System.currentTimeMillis()
            for (j in 0..100) {
//                sut.addBreadcrumbNew(crumb)
            }
            newDuration += System.currentTimeMillis() - newStart
        }

        println("It took old: ${oldDuration}ms, new: ${newDuration}ms")
    }
}

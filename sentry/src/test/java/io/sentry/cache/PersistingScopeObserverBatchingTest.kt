package io.sentry.cache

import com.google.common.truth.Truth.assertThat
import io.sentry.Breadcrumb
import io.sentry.ISentryExecutorService
import io.sentry.SentryOptions
import io.sentry.cache.PersistingScopeObserver.BREADCRUMBS_FILENAME
import io.sentry.cache.PersistingScopeObserver.TRANSACTION_FILENAME
import io.sentry.test.DeferredExecutorService
import kotlin.test.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class PersistingScopeObserverBatchingTest {
  @get:Rule val tmpDir = TemporaryFolder()

  private val options = SentryOptions()

  private fun getSut(executor: ISentryExecutorService): PersistingScopeObserver {
    options.executorService = executor
    options.cacheDirPath = tmpDir.newFolder().absolutePath
    return PersistingScopeObserver(options)
  }

  private fun PersistingScopeObserver.readTransaction(): String? =
    read(options, TRANSACTION_FILENAME, String::class.java)

  @Suppress("UNCHECKED_CAST")
  private fun PersistingScopeObserver.readBreadcrumbs(): List<Breadcrumb> =
    read(options, BREADCRUMBS_FILENAME, List::class.java) as List<Breadcrumb>

  @Test
  fun `defers writes until the flush runs`() {
    val executor = DeferredExecutorService()
    val sut = getSut(executor)

    sut.setTransaction("MainActivity")
    assertThat(sut.readTransaction()).isNull()

    executor.runAll()
    assertThat(sut.readTransaction()).isEqualTo("MainActivity")
  }

  @Test
  fun `coalesces repeated writes keeping only the latest value`() {
    val executor = DeferredExecutorService()
    val sut = getSut(executor)

    sut.setTransaction("A")
    sut.setTransaction("B")
    sut.setTransaction("C")
    executor.runAll()

    assertThat(sut.readTransaction()).isEqualTo("C")
  }

  @Test
  fun `batches breadcrumbs and persists all of them`() {
    val executor = DeferredExecutorService()
    val sut = getSut(executor)

    sut.addBreadcrumb(Breadcrumb().apply { message = "one" })
    sut.addBreadcrumb(Breadcrumb().apply { message = "two" })
    assertThat(sut.readBreadcrumbs()).isEmpty()

    executor.runAll()
    assertThat(sut.readBreadcrumbs().map { it.message }).containsExactly("one", "two").inOrder()
  }

  @Test
  fun `clearing breadcrumbs drops earlier pending adds but keeps later ones`() {
    val executor = DeferredExecutorService()
    val sut = getSut(executor)

    sut.addBreadcrumb(Breadcrumb().apply { message = "dropped" })
    sut.setBreadcrumbs(emptyList())
    sut.addBreadcrumb(Breadcrumb().apply { message = "kept" })
    executor.runAll()

    assertThat(sut.readBreadcrumbs().map { it.message }).containsExactly("kept")
  }

  @Test
  fun `resetCache keeps pending mutations from the current process`() {
    val executor = DeferredExecutorService()
    val sut = getSut(executor)

    sut.setTransaction("SetDuringInit")
    sut.resetCache()
    executor.runAll()

    assertThat(sut.readTransaction()).isEqualTo("SetDuringInit")
  }

  @Test
  fun `flush writes pending state synchronously`() {
    val executor = DeferredExecutorService()
    val sut = getSut(executor)

    sut.setTransaction("Sync")
    sut.flush()

    assertThat(sut.readTransaction()).isEqualTo("Sync")
  }
}

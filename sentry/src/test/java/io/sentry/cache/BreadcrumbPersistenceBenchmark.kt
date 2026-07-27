package io.sentry.cache

import io.sentry.Breadcrumb
import io.sentry.NoOpLogger
import io.sentry.SentryOptions
import io.sentry.cache.tape.ObjectQueue
import io.sentry.cache.tape.QueueFile
import java.io.BufferedWriter
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.file.Files
import kotlin.system.measureNanoTime
import kotlin.test.Ignore
import kotlin.test.Test

/**
 * Throwaway A/B harness comparing the JSONL append log against the tape QueueFile it replaces, each
 * configured the way it is (or was) configured in production: tape with buffered writes plus one
 * fsync per flush, JSONL with buffered writes and no fsync.
 *
 * Also measures tape with the fsync removed, which isolates how much of the difference is the fsync
 * versus the ring-buffer bookkeeping — the two are easy to conflate.
 *
 * Ignored by default; drop the annotation and run `./gradlew :sentry:test
 * --tests="*BreadcrumbPersistenceBenchmark*" -i` to see the numbers.
 */
@Ignore("manual benchmark, not a correctness test")
class BreadcrumbPersistenceBenchmark {

  private val options =
    SentryOptions().apply {
      setLogger(NoOpLogger.getInstance())
      maxBreadcrumbs = 100
    }

  private enum class Impl {
    /** tape as PersistingScopeObserver configured it: buffered writes, one fsync per flush. */
    TAPE,
    /** tape with the per-flush fsync removed, to separate fsync cost from bookkeeping cost. */
    TAPE_NO_FSYNC,
    /** the JSONL append log, buffered and never fsync'd. */
    JSONL,
  }

  @Test
  fun `compare steady-state single-crumb flushes`() {
    val flushes = 2_000
    report("steady state ($flushes flushes x 1 crumb)", flushes, 1)
  }

  @Test
  fun `compare startup-style batched flushes`() {
    val flushes = 100
    report("startup ($flushes flushes x 20 crumbs)", flushes, 20)
  }

  @Test
  fun `compare reads of a full log`() {
    val reads = 100
    reportRead("read ($reads reads of ${options.maxBreadcrumbs} crumbs)", reads)
  }

  private fun crumbs(n: Int) = (1..n).map { Breadcrumb.debug("breadcrumb number $it") }

  private fun tapeQueue(file: File): ObjectQueue<Breadcrumb> {
    val queueFile =
      QueueFile.Builder(file).size(options.maxBreadcrumbs).synchronousWrites(false).build()
    return ObjectQueue.create(
      queueFile,
      object : ObjectQueue.Converter<Breadcrumb> {
        override fun from(source: ByteArray): Breadcrumb? =
          InputStreamReader(ByteArrayInputStream(source), Charsets.UTF_8).use {
            options.serializer.deserialize(it, Breadcrumb::class.java)
          }

        override fun toStream(value: Breadcrumb, sink: OutputStream) {
          BufferedWriter(OutputStreamWriter(sink, Charsets.UTF_8)).use {
            options.serializer.serialize(value, it)
          }
        }
      },
    )
  }

  private fun writeRun(impl: Impl, flushes: Int, perFlush: Int): Long {
    val batch = crumbs(perFlush)
    return when (impl) {
      Impl.TAPE,
      Impl.TAPE_NO_FSYNC -> {
        val queue = tapeQueue(tmpFile())
        val fsync = impl == Impl.TAPE
        measureNanoTime {
            repeat(flushes) {
              batch.forEach { queue.add(it) }
              if (fsync) queue.sync()
            }
          }
          .also { queue.close() }
      }
      Impl.JSONL -> {
        val log = BreadcrumbAppendLog(options, tmpFile())
        measureNanoTime { repeat(flushes) { log.append(batch) } }
      }
    }
  }

  private fun readRun(impl: Impl, reads: Int): Long {
    val stored = crumbs(options.maxBreadcrumbs)
    return when (impl) {
      Impl.TAPE,
      Impl.TAPE_NO_FSYNC -> {
        val queue = tapeQueue(tmpFile())
        stored.forEach { queue.add(it) }
        queue.sync()
        measureNanoTime { repeat(reads) { queue.asList() } }.also { queue.close() }
      }
      Impl.JSONL -> {
        val log = BreadcrumbAppendLog(options, tmpFile())
        log.append(stored)
        measureNanoTime { repeat(reads) { log.read() } }
      }
    }
  }

  private fun tmpFile(): File =
    Files.createTempDirectory("bench").toFile().let { File(it, "breadcrumbs.json") }

  private fun report(label: String, flushes: Int, perFlush: Int) =
    print(label) { writeRun(it, flushes, perFlush) }

  private fun reportRead(label: String, reads: Int) = print(label) { readRun(it, reads) }

  private fun print(label: String, run: (Impl) -> Long) {
    // warm up the JIT and page cache before measuring
    repeat(3) { Impl.entries.forEach { impl -> run(impl) } }
    val best = Impl.entries.associateWith { impl -> (1..5).minOf { run(impl) } / 1_000_000.0 }
    val summary =
      Impl.entries.joinToString("  ") { "%s=%.2fms".format(it.name.lowercase(), best.getValue(it)) }
    println(
      "%-40s %s   jsonl vs tape=%.2fx  jsonl vs tape_no_fsync=%.2fx"
        .format(
          label,
          summary,
          best.getValue(Impl.TAPE) / best.getValue(Impl.JSONL),
          best.getValue(Impl.TAPE_NO_FSYNC) / best.getValue(Impl.JSONL),
        )
    )
  }
}

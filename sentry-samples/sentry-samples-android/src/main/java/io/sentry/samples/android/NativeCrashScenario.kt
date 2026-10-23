package io.sentry.samples.android

import io.sentry.Sentry
import java.util.UUID

enum class NativeCrashScenario(
  val id: String,
  val displayName: String,
  private val crash: () -> Unit,
) {
  BASELINE("baseline", "Native Crash", NativeSample::crash),
  OUT_OF_MEMORY("out_of_memory", "Out of memory", NativeSample::crashOutOfMemory),
  LOCKED_HEAP("locked_heap", "Allocator lock", NativeSample::crashLockedHeap),
  CORRUPTED_HEAP("corrupted_heap", "Heap corruption", NativeSample::crashCorruptedHeap),
  NO_FILE_DESCRIPTORS(
    "no_file_descriptors",
    "FD exhaustion",
    NativeSample::crashNoFileDescriptors,
  ),
  STACK_CORRUPTION(
    "stack_corruption",
    "Stack corruption",
    NativeSample::crashStackCorruption,
  ),
  STACK_OVERFLOW("stack_overflow", "Stack overflow", NativeSample::crashStackOverflow),
  CONCURRENT("concurrent", "Concurrent crashes", NativeSample::crashConcurrent),
  MANY_THREADS("many_threads", "Many Threads", NativeSample::crashManyThreads);

  fun run(runId: String = UUID.randomUUID().toString()) {
    Sentry.setTag("sample.scenario", id)
    Sentry.setTag("sample.run", runId)
    NativeSample.prepareCrash(id, runId)
    crash()
  }
}

package io.sentry.android.core.hang

import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.Callable
import java.util.concurrent.Delayed
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * A [ScheduledExecutorService] that runs tasks only when the test calls [runPending] or [runTick],
 * on the test thread.
 *
 * It keeps the parts of the real contract that the recorder relies on: [execute] throws
 * [RejectedExecutionException] after [shutdown], tasks submitted before [shutdown] still run, a
 * task that throws does not stop the executor (the exception goes to [taskFailures], where the real
 * executor keeps it in the task's future), and a periodic task that throws is cancelled silently.
 */
class FakeScheduledExecutorService : ScheduledExecutorService, AbstractExecutorService() {
  private val pending = ArrayDeque<Runnable>()
  private val periodicTasks = mutableListOf<PeriodicTask>()
  private var shutdown = false
  val taskFailures = mutableListOf<Throwable>()

  val activePeriodicTasks: List<PeriodicTask>
    get() = periodicTasks.filter { !it.isCancelled }

  fun runPending() {
    while (pending.isNotEmpty()) {
      try {
        pending.removeFirst().run()
      } catch (t: Throwable) {
        taskFailures.add(t)
      }
    }
  }

  /**
   * Runs one round of each active periodic task, then the pending tasks. A tick goes first because
   * on a real executor a tick can be due before a task that was just queued.
   */
  fun runTick() {
    for (task in activePeriodicTasks) {
      try {
        task.command.run()
      } catch (_: Throwable) {
        task.cancel(false)
      }
    }
    runPending()
  }

  override fun execute(command: Runnable) {
    if (shutdown) {
      throw RejectedExecutionException("shut down")
    }
    pending.addLast(command)
  }

  override fun scheduleWithFixedDelay(
    command: Runnable,
    initialDelay: Long,
    delay: Long,
    unit: TimeUnit,
  ): ScheduledFuture<*> {
    if (shutdown) {
      throw RejectedExecutionException("shut down")
    }
    return PeriodicTask(command, unit.toMillis(delay)).also { periodicTasks.add(it) }
  }

  override fun scheduleAtFixedRate(
    command: Runnable,
    initialDelay: Long,
    period: Long,
    unit: TimeUnit,
  ): ScheduledFuture<*> = throw UnsupportedOperationException()

  override fun schedule(command: Runnable, delay: Long, unit: TimeUnit): ScheduledFuture<*> =
    throw UnsupportedOperationException()

  override fun <V : Any?> schedule(
    callable: Callable<V>,
    delay: Long,
    unit: TimeUnit,
  ): ScheduledFuture<V> = throw UnsupportedOperationException()

  override fun shutdown() {
    shutdown = true
    periodicTasks.forEach { it.cancel(false) }
  }

  override fun shutdownNow(): List<Runnable> {
    shutdown()
    return pending.toList().also { pending.clear() }
  }

  override fun isShutdown(): Boolean = shutdown

  override fun isTerminated(): Boolean = shutdown && pending.isEmpty()

  override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = isTerminated

  class PeriodicTask(val command: Runnable, val delayMs: Long) : ScheduledFuture<Unit> {
    private var cancelled = false

    override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
      cancelled = true
      return true
    }

    override fun isCancelled(): Boolean = cancelled

    override fun isDone(): Boolean = cancelled

    override fun get() = throw UnsupportedOperationException()

    override fun get(timeout: Long, unit: TimeUnit) = throw UnsupportedOperationException()

    override fun getDelay(unit: TimeUnit): Long = unit.convert(delayMs, TimeUnit.MILLISECONDS)

    override fun compareTo(other: Delayed): Int =
      getDelay(TimeUnit.MILLISECONDS).compareTo(other.getDelay(TimeUnit.MILLISECONDS))
  }
}

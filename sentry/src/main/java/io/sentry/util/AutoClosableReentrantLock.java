package io.sentry.util;

import io.sentry.ISentryLifecycleToken;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.locks.ReentrantLock;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * Hands out an {@link ISentryLifecycleToken} from {@link #acquire()} for use with
 * try-with-resources (replacing {@code synchronized} blocks).
 *
 * <p>The underlying {@link ReentrantLock} is created lazily on the first {@link #acquire()}. Many
 * SDK objects hold a lock but never contend on it (especially during {@code SentryAndroid.init}),
 * so the eager allocation of a {@link ReentrantLock} (and its {@code AbstractQueuedSynchronizer})
 * was pure GC and main-thread overhead. We keep a {@link ReentrantLock} rather than reverting to
 * {@code synchronized} to stay friendly to virtual threads (Loom), see #3715.
 *
 * <p>{@link #acquire()} returns this instance as the token, so the steady-state acquire/close path
 * allocates nothing. Reentrant acquires stay balanced because try-with-resources calls {@link
 * #close()} exactly once per acquire.
 */
@ApiStatus.Internal
public final class AutoClosableReentrantLock implements ISentryLifecycleToken {

  private static final @NotNull AtomicReferenceFieldUpdater<
          AutoClosableReentrantLock, ReentrantLock>
      LOCK_UPDATER =
          AtomicReferenceFieldUpdater.newUpdater(
              AutoClosableReentrantLock.class, ReentrantLock.class, "lock");

  private volatile @Nullable ReentrantLock lock;

  public @NotNull ISentryLifecycleToken acquire() {
    getOrCreateLock().lock();
    return this;
  }

  @Override
  public void close() {
    Objects.requireNonNull(lock, "close() called before acquire()").unlock();
  }

  private @NotNull ReentrantLock getOrCreateLock() {
    final @Nullable ReentrantLock existing = lock;
    if (existing != null) {
      return existing;
    }
    final @NotNull ReentrantLock candidate = new ReentrantLock();
    if (LOCK_UPDATER.compareAndSet(this, null, candidate)) {
      return candidate;
    }
    // The CAS can only fail because another thread installed its lock first, and the field is
    // never reset, so all callers end up contending on that same instance.
    return Objects.requireNonNull(lock, "lock must have been set by the winning thread");
  }

  @TestOnly
  boolean isLocked() {
    final @Nullable ReentrantLock current = lock;
    return current != null && current.isLocked();
  }

  @TestOnly
  boolean isLockAllocated() {
    return lock != null;
  }
}

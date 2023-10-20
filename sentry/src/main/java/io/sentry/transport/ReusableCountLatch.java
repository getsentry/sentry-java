/*
 * Adapted from https://github.com/MatejTymes/JavaFixes/blob/37e74b9d0a29f7a47485c6d1bb1307f01fb93634/src/main/java/javafixes/concurrency/ReusableCountLatch.java
 *
 * Copyright (C) 2016 Matej Tymes
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.sentry.transport;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import org.jetbrains.annotations.NotNull;

/**
 * <p>A synchronization aid that allows one or more threads to wait until a set of operations being
 * performed in other threads completes.
 *
 * <p>A {@code ReusableCountLatch} is initialized with a given <em>count</em>. The {@link
 * #waitTillZero} methods block until the current count reaches zero due to invocations of the
 * {@link #decrement} method, after which all waiting threads are released. If zero has been reached
 * any subsequent invocations of {@link #waitTillZero} return immediately. The count can be
 * increased calling the {@link #increment()} method and any subsequent thread calling the {@link
 * #waitTillZero} method will be blocked again until another zero is reached.
 *
 * <p>{@code ReusableCountLatch} provides more versatility than {@link
 * java.util.concurrent.CountDownLatch CountDownLatch} as the count doesn't have to be known upfront
 * and you can reuse this class as many times as you want to. It is also better than a {@link
 * java.util.concurrent.Phaser Phaser} whose count is limited to 65_535. {@code ReusableCountLatch}
 * instead can count up to 2_147_483_647 (2^31-1).
 *
 * <p>Great use case for {@code ReusableCountLatch} is when you wait for tasks on other threads to
 * finish, but these tasks could trigger more tasks and it is not known upfront how many will be
 * triggered in total.
 *
 * @author mtymes
 * @since 07/10/16 00:10 AM
 */
public final class ReusableCountLatch {

  private final @NotNull Sync sync;

  /**
   * Constructs a {@code ReusableCountLatch} initialized with the given count.
   *
   * @param initialCount the number of times {@link #decrement} must be invoked before threads can
   *     pass through {@link #waitTillZero}. For each additional call of the {@link #increment}
   *     method one more {@link #decrement} must be called.
   * @throws IllegalArgumentException if {@code initialCount} is negative
   */
  public ReusableCountLatch(final int initialCount) {
    if (initialCount < 0) {
      throw new IllegalArgumentException(
          "negative initial count '" + initialCount + "' is not allowed");
    }
    this.sync = new Sync(initialCount);
  }

  /** Constructs a {@code ReusableCountLatch} with initial count set to 0. */
  public ReusableCountLatch() {
    this(0);
  }

  /**
   * Returns the current count.
   *
   * @return the current count
   */
  public int getCount() {
    return sync.getCount();
  }

  /**
   * Decrements the count of the latch, releasing all waiting threads if the count reaches zero.
   *
   * <p>If the current count is greater than zero then it is decremented. If the new count is zero
   * then all waiting threads are re-enabled for thread scheduling purposes.
   *
   * <p>If the current count equals zero then nothing happens.
   */
  public void decrement() {
    sync.decrement();
  }

  /**
   * Increments the count of the latch, which will make it possible to block all threads waiting
   * till count reaches zero.
   */
  public void increment() {
    sync.increment();
  }

  /**
   * Causes the current thread to wait until the latch has counted down to zero, unless the thread
   * is {@linkplain Thread#interrupt interrupted}.
   *
   * <p>If the current count is zero then this method returns immediately.
   *
   * <p>If the current count is greater than zero then the current thread becomes disabled for
   * thread scheduling purposes and lies dormant until one of two things happen:
   *
   * <ul>
   *   <li>The count reaches zero due to invocations of the {@link #decrement} method; or
   *   <li>Some other thread {@linkplain Thread#interrupt interrupts} the current thread.
   * </ul>
   *
   * <p>If the current thread:
   *
   * <ul>
   *   <li>has its interrupted status set on entry to this method; or
   *   <li>is {@linkplain Thread#interrupt interrupted} while waiting,
   * </ul>
   *
   * then {@link InterruptedException} is thrown and the current thread's interrupted status is
   * cleared.
   *
   * @throws InterruptedException if the current thread is interrupted while waiting
   */
  public void waitTillZero() throws InterruptedException {
    sync.acquireSharedInterruptibly(1);
  }

  /**
   * Causes the current thread to wait until the latch has counted down to zero, unless the thread
   * is {@linkplain Thread#interrupt interrupted}, or the specified waiting time elapses.
   *
   * <p>If the current count is zero then this method returns immediately with the value {@code
   * true}.
   *
   * <p>If the current count is greater than zero then the current thread becomes disabled for
   * thread scheduling purposes and lies dormant until one of three things happen:
   *
   * <ul>
   *   <li>The count reaches zero due to invocations of the {@link #decrement()} method; or
   *   <li>Some other thread {@linkplain Thread#interrupt interrupts} the current thread; or
   *   <li>The specified waiting time elapses.
   * </ul>
   *
   * <p>If the count reaches zero then the method returns with the value {@code true}.
   *
   * <p>If the current thread:
   *
   * <ul>
   *   <li>has its interrupted status set on entry to this method; or
   *   <li>is {@linkplain Thread#interrupt interrupted} while waiting,
   * </ul>
   *
   * then {@link InterruptedException} is thrown and the current thread's interrupted status is
   * cleared.
   *
   * <p>If the specified waiting time elapses then the value {@code false} is returned. If the time
   * is less than or equal to zero, the method will not wait at all.
   *
   * @param timeout the maximum time to wait
   * @param unit the time unit of the {@code timeout} argument
   * @return {@code true} if the count reached zero and {@code false} if the waiting time elapsed
   *     before the count reached zero
   * @throws InterruptedException if the current thread is interrupted while waiting
   */
  public boolean waitTillZero(final long timeout, final @NotNull TimeUnit unit)
      throws InterruptedException {
    return sync.tryAcquireSharedNanos(1, unit.toNanos(timeout));
  }

  /** Synchronization control For ReusableCountLatch. Uses AQS state to represent count. */
  private static final class Sync extends AbstractQueuedSynchronizer {
    private static final long serialVersionUID = 5970133580157457018L;

    Sync(final int count) {
      setState(count);
    }

    private int getCount() {
      return getState();
    }

    private void increment() {
      for (; ; ) {
        int oldCount = getState();
        int newCount = oldCount + 1;
        if (compareAndSetState(oldCount, newCount)) {
          return;
        }
      }
    }

    private void decrement() {
      releaseShared(1);
    }

    @Override
    public int tryAcquireShared(final int acquires) {
      return (getState() == 0) ? 1 : -1;
    }

    @Override
    public boolean tryReleaseShared(final int releases) {
      // Decrement count; signal when transition to zero
      for (; ; ) {
        int oldCount = getState();
        if (oldCount == 0) {
          return false;
        }
        int newCount = oldCount - 1;
        if (compareAndSetState(oldCount, newCount)) {
          return newCount == 0;
        }
      }
    }
  }
}

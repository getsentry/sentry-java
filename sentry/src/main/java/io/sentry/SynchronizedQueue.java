/*
 * Adapted from https://github.com/apache/commons-collections/blob/fce46cdcc6fa33ba9472921d4b3ec3f548d8cbcc/src/main/java/org/apache/commons/collections4/queue/SynchronizedQueue.java
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sentry;

import io.sentry.util.AutoClosableReentrantLock;
import java.util.Queue;
import org.jetbrains.annotations.NotNull;

/**
 * Decorates another {@link Queue} to synchronize its behaviour for a multi-threaded environment.
 *
 * <p>Methods are synchronized, then forwarded to the decorated queue. Iterators must be separately
 * synchronized around the loop.
 *
 * @param <E> the type of the elements in the collection
 * @since 4.2
 */
final class SynchronizedQueue<E> extends SynchronizedCollection<E> implements Queue<E> {

  /** Serialization version */
  private static final long serialVersionUID = 1L;

  /**
   * Factory method to create a synchronized queue.
   *
   * @param <E> the type of the elements in the queue
   * @param queue the queue to decorate, must not be null
   * @return a new synchronized Queue
   * @throws NullPointerException if queue is null
   */
  public static <E> SynchronizedQueue<E> synchronizedQueue(final Queue<E> queue) {
    return new SynchronizedQueue<>(queue);
  }

  // -----------------------------------------------------------------------
  /**
   * Constructor that wraps (not copies).
   *
   * @param queue the queue to decorate, must not be null
   * @throws NullPointerException if queue is null
   */
  private SynchronizedQueue(final Queue<E> queue) {
    super(queue);
  }

  /**
   * Constructor that wraps (not copies).
   *
   * @param queue the queue to decorate, must not be null
   * @param lock the lock to use, must not be null
   * @throws NullPointerException if queue or lock is null
   */
  @SuppressWarnings("ProtectedMembersInFinalClass")
  protected SynchronizedQueue(final Queue<E> queue, final AutoClosableReentrantLock lock) {
    super(queue, lock);
  }

  /**
   * Gets the queue being decorated.
   *
   * @return the decorated queue
   */
  @Override
  protected Queue<E> decorated() {
    return (Queue<E>) super.decorated();
  }

  @Override
  public E element() {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      return decorated().element();
    }
  }

  @SuppressWarnings("UndefinedEquals")
  @Override
  public boolean equals(final Object object) {
    if (object == this) {
      return true;
    }
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      return decorated().equals(object);
    }
  }

  // -----------------------------------------------------------------------

  @Override
  public int hashCode() {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      return decorated().hashCode();
    }
  }

  @Override
  public boolean offer(final E e) {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      return decorated().offer(e);
    }
  }

  @Override
  public E peek() {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      return decorated().peek();
    }
  }

  @Override
  public E poll() {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      return decorated().poll();
    }
  }

  @Override
  public E remove() {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      return decorated().remove();
    }
  }

  @Override
  public Object[] toArray() {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      return decorated().toArray();
    }
  }

  @Override
  public <T> T[] toArray(T[] object) {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      return decorated().toArray(object);
    }
  }
}

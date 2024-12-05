package io.sentry;

import java.io.Serializable;
import java.util.AbstractCollection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Queue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class DisabledQueue<E> extends AbstractCollection<E> implements Queue<E>, Serializable {

  /** Serialization version. */
  private static final long serialVersionUID = -8423413834657610417L;

  /** Constructor that creates a queue that does not accept any element. */
  public DisabledQueue() {}

  // -----------------------------------------------------------------------
  /**
   * Returns the number of elements stored in the queue.
   *
   * @return this queue's size
   */
  @Override
  public int size() {
    return 0;
  }

  /**
   * Returns true if this queue is empty; false otherwise.
   *
   * @return false
   */
  @Override
  public boolean isEmpty() {
    return true;
  }

  /** Does nothing. */
  @Override
  public void clear() {}

  /**
   * Since the queue is disabled, the element will not be added.
   *
   * @param element the element to add
   * @return false, always
   */
  @Override
  public boolean add(final @NotNull E element) {
    return false;
  }

  // -----------------------------------------------------------------------

  /**
   * Receives an element but do nothing with it.
   *
   * @param element the element to add
   * @return false, always
   */
  @Override
  public boolean offer(@NotNull E element) {
    return false;
  }

  @Override
  public @Nullable E poll() {
    return null;
  }

  @Override
  public @Nullable E element() {
    return null;
  }

  @Override
  public @Nullable E peek() {
    return null;
  }

  @Override
  public @NotNull E remove() {
    throw new NoSuchElementException("queue is disabled");
  }

  // -----------------------------------------------------------------------

  /**
   * Returns an iterator over this queue's elements.
   *
   * @return an iterator over this queue's elements
   */
  @Override
  public @NotNull Iterator<E> iterator() {
    return new Iterator<E>() {

      @Override
      public boolean hasNext() {
        return false;
      }

      @Override
      public E next() {
        throw new NoSuchElementException();
      }

      @Override
      public void remove() {
        throw new IllegalStateException();
      }
    };
  }
}

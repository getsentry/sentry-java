/*
 * Adapted from: https://github.com/square/tape/tree/445cd3fd0a7b3ec48c9ea3e0e86663fe6d3735d8/tape/src/main/java/com/squareup/tape2
 *
 *  Copyright (C) 2010 Square, Inc.
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
package io.sentry.cache.tape;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/** A queue of objects. */
@ApiStatus.Internal
public abstract class ObjectQueue<T> implements Iterable<T>, Closeable {
  /** A queue for objects that are atomically and durably serialized to {@code file}. */
  public static <T> ObjectQueue<T> create(QueueFile qf, Converter<T> converter) {
    return new FileObjectQueue<>(qf, converter);
  }

  /** An empty queue for objects that is essentially a no-op. */
  public static <T> ObjectQueue<T> createEmpty() {
    return new EmptyObjectQueue<>();
  }

  /** The underlying {@link QueueFile} backing this queue, or null if it's only in memory. */
  public abstract @Nullable QueueFile file();

  /** Returns the number of entries in the queue. */
  public abstract int size();

  /** Returns {@code true} if this queue contains no entries. */
  public boolean isEmpty() {
    return size() == 0;
  }

  /** Enqueues an entry that can be processed at any time. */
  public abstract void add(T entry) throws IOException;

  /**
   * Returns the head of the queue, or {@code null} if the queue is empty. Does not modify the
   * queue.
   */
  public abstract @Nullable T peek() throws IOException;

  /**
   * Reads up to {@code max} entries from the head of the queue without removing the entries. If the
   * queue's {@link #size()} is less than {@code max} then only {@link #size()} entries are read.
   */
  public List<T> peek(int max) throws IOException {
    int end = Math.min(max, size());
    List<T> subList = new ArrayList<T>(end);
    Iterator<T> iterator = iterator();
    for (int i = 0; i < end; i++) {
      subList.add(iterator.next());
    }
    return Collections.unmodifiableList(subList);
  }

  /** Returns the entries in the queue as an unmodifiable {@link List}. */
  public List<T> asList() throws IOException {
    return peek(size());
  }

  /** Removes the head of the queue. */
  public void remove() throws IOException {
    remove(1);
  }

  /** Removes {@code n} entries from the head of the queue. */
  public abstract void remove(int n) throws IOException;

  /** Clears this queue. Also truncates the file to the initial size. */
  public void clear() throws IOException {
    remove(size());
  }

  /**
   * Convert a byte stream to and from a concrete type.
   *
   * @param <T> Object type.
   */
  public interface Converter<T> {
    /** Converts bytes to an object. */
    @Nullable
    T from(byte[] source) throws IOException;

    /** Converts {@code value} to bytes written to the specified stream. */
    void toStream(T value, OutputStream sink) throws IOException;
  }
}

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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class FileObjectQueue<T> extends ObjectQueue<T> {
  /** Backing storage implementation. */
  private final QueueFile queueFile;
  /** Reusable byte output buffer. */
  private final DirectByteArrayOutputStream bytes = new DirectByteArrayOutputStream();

  final Converter<T> converter;

  FileObjectQueue(QueueFile queueFile, Converter<T> converter) {
    this.queueFile = queueFile;
    this.converter = converter;
  }

  @Override
  public @NotNull QueueFile file() {
    return queueFile;
  }

  @Override
  public int size() {
    return queueFile.size();
  }

  @Override
  public boolean isEmpty() {
    return queueFile.isEmpty();
  }

  @Override
  public void add(T entry) throws IOException {
    bytes.reset();
    converter.toStream(entry, bytes);
    queueFile.add(bytes.getArray(), 0, bytes.size());
  }

  @Override
  public @Nullable T peek() throws IOException {
    byte[] bytes = queueFile.peek();
    if (bytes == null) return null;
    return converter.from(bytes);
  }

  @Override
  public void remove() throws IOException {
    queueFile.remove();
  }

  @Override
  public void remove(int n) throws IOException {
    queueFile.remove(n);
  }

  @Override
  public void clear() throws IOException {
    queueFile.clear();
  }

  @Override
  public void close() throws IOException {
    queueFile.close();
  }

  /**
   * Returns an iterator over entries in this queue.
   *
   * <p>The iterator disallows modifications to the queue during iteration. Removing entries from
   * the head of the queue is permitted during iteration using {@link Iterator#remove()}.
   *
   * <p>The iterator may throw an unchecked {@link IOException} during {@link Iterator#next()} or
   * {@link Iterator#remove()}.
   */
  @Override
  public Iterator<T> iterator() {
    return new QueueFileIterator(queueFile.iterator());
  }

  @Override
  public String toString() {
    return "FileObjectQueue{" + "queueFile=" + queueFile + '}';
  }

  private final class QueueFileIterator implements Iterator<T> {
    final Iterator<byte[]> iterator;

    QueueFileIterator(Iterator<byte[]> iterator) {
      this.iterator = iterator;
    }

    @Override
    public boolean hasNext() {
      return iterator.hasNext();
    }

    @Override
    @Nullable
    public T next() {
      byte[] data = iterator.next();
      try {
        return converter.from(data);
      } catch (IOException e) {
        throw QueueFile.<Error>getSneakyThrowable(e);
      }
    }

    @Override
    public void remove() {
      iterator.remove();
    }
  }

  /** Enables direct access to the internal array. Avoids unnecessary copying. */
  private static final class DirectByteArrayOutputStream extends ByteArrayOutputStream {
    DirectByteArrayOutputStream() {}

    /**
     * Gets a reference to the internal byte array. The {@link #size()} method indicates how many
     * bytes contain actual data added since the last {@link #reset()} call.
     */
    byte[] getArray() {
      return buf;
    }
  }
}

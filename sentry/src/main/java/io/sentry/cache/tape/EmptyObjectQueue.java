package io.sentry.cache.tape;

import java.io.IOException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class EmptyObjectQueue<T> extends ObjectQueue<T> {
  @Override
  public @Nullable QueueFile file() {
    return null;
  }

  @Override
  public int size() {
    return 0;
  }

  @Override
  public void add(T entry) throws IOException {
  }

  @Override
  public @Nullable T peek() throws IOException {
    return null;
  }

  @Override
  public void remove(int n) throws IOException {
  }

  @Override
  public void close() throws IOException {
  }

  @NotNull
  @Override
  public Iterator<T> iterator() {
    return new EmptyIterator<>();
  }

  private final static class EmptyIterator<T> implements Iterator<T> {

    @Override
    public boolean hasNext() {
      return false;
    }

    @Override
    public T next() {
      throw new NoSuchElementException("No elements in EmptyIterator!");
    }
  }
}

package io.sentry;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ConcurrentHashMap;

public final class TransactionContexts extends ConcurrentHashMap<String, Object> implements Cloneable {
  private static final long serialVersionUID = 252445813254943011L;

  public TransactionContexts() {
  }

  public TransactionContexts(final @NotNull Trace trace) {
    this.setTrace(trace);
  }

  private <T> T toContextType(String key, Class<T> clazz) {
    Object item = get(key);
    return clazz.isInstance(item) ? clazz.cast(item) : null;
  }

  public Trace getTrace() {
    return toContextType(Trace.TYPE, Trace.class);
  }

  public void setTrace(final @NotNull Trace trace) {
    this.put(Trace.TYPE, trace);
  }
}

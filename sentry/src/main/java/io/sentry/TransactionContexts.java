package io.sentry;

import io.sentry.protocol.Contexts;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

public final class TransactionContexts extends Contexts {
  private static final long serialVersionUID = 252445813254943011L;

  public TransactionContexts() {}

  public TransactionContexts(final @NotNull Trace trace) {
    this.setTrace(trace);
  }

  public Trace getTrace() {
    return toContextType(Trace.TYPE, Trace.class);
  }

  public void setTrace(final @NotNull Trace trace) {
    this.put(Trace.TYPE, trace);
  }

  @Override
  public @NotNull TransactionContexts clone() throws CloneNotSupportedException {
    final TransactionContexts clone = new TransactionContexts();

    super.cloneInto(clone);

    for (final Map.Entry<String, Object> entry : entrySet()) {
      if (entry != null) {
        final Object value = entry.getValue();
        if (Trace.TYPE.equals(entry.getKey()) && value instanceof Trace) {
          clone.setTrace(((Trace) value).clone());
        }
      }
    }

    return clone;
  }
}

package io.sentry;

import io.sentry.util.Objects;
import java.util.Deque;
import java.util.concurrent.LinkedBlockingDeque;
import org.jetbrains.annotations.NotNull;

final class Stack {

  static final class StackItem {
    private final SentryOptions options;
    private volatile @NotNull ISentryClient client;
    private volatile @NotNull Scope scope;

    StackItem(
        final @NotNull SentryOptions options,
        final @NotNull ISentryClient client,
        final @NotNull Scope scope) {
      this.client = Objects.requireNonNull(client, "ISentryClient is required.");
      this.scope = Objects.requireNonNull(scope, "Scope is required.");
      this.options = Objects.requireNonNull(options, "Options is required");
    }

    StackItem(final @NotNull StackItem item) {
      options = item.options;
      client = item.client;

      try {
        scope = item.scope.clone();
      } catch (CloneNotSupportedException e) {
        // TODO: Why do we need to deal with this? We must guarantee clone is possible here!
        options.getLogger().log(SentryLevel.ERROR, "Clone not supported");
        scope = new Scope(item.options);
      }
    }

    public @NotNull ISentryClient getClient() {
      return client;
    }

    public void setClient(final @NotNull ISentryClient client) {
      this.client = client;
    }

    public @NotNull Scope getScope() {
      return scope;
    }

    public SentryOptions getOptions() {
      return options;
    }
  }

  private final @NotNull Deque<StackItem> items = new LinkedBlockingDeque<>();
  private final @NotNull ILogger logger;

  public Stack(final @NotNull ILogger logger, final @NotNull StackItem rootStackItem) {
    this.logger = Objects.requireNonNull(logger, "logger is required");
    this.items.push(Objects.requireNonNull(rootStackItem, "rootStackItem is required"));
  }

  public Stack(final @NotNull Stack stack) {
    this(stack.logger, stack.items.getFirst());
    for (final StackItem item : stack.items) {
      push(new StackItem(item));
    }
  }

  @NotNull
  StackItem peek() {
    // peek can never return null since Stack can be created only with an item and pop does not drop
    // the last item.
    return items.peek();
  }

  void pop() {
    synchronized (items) {
      if (items.size() != 1) {
        items.pop();
      } else {
        logger.log(SentryLevel.WARNING, "Attempt to pop the root scope.");
      }
    }
  }

  void push(final @NotNull StackItem stackItem) {
    items.push(stackItem);
  }

  int size() {
    return items.size();
  }
}

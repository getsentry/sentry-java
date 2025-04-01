package io.sentry;

import io.sentry.util.AutoClosableReentrantLock;
import io.sentry.util.Objects;
import java.util.Deque;
import java.util.Iterator;
import java.util.concurrent.LinkedBlockingDeque;
import org.jetbrains.annotations.NotNull;

/** TODO [POTEL] can this class be removed? */
final class Stack {

  static final class StackItem {
    private final SentryOptions options;
    private volatile @NotNull ISentryClient client;
    private volatile @NotNull IScope scope;

    StackItem(
        final @NotNull SentryOptions options,
        final @NotNull ISentryClient client,
        final @NotNull IScope scope) {
      this.client = Objects.requireNonNull(client, "ISentryClient is required.");
      this.scope = Objects.requireNonNull(scope, "Scope is required.");
      this.options = Objects.requireNonNull(options, "Options is required");
    }

    StackItem(final @NotNull StackItem item) {
      options = item.options;
      client = item.client;
      scope = item.scope.clone();
    }

    public @NotNull ISentryClient getClient() {
      return client;
    }

    public void setClient(final @NotNull ISentryClient client) {
      this.client = client;
    }

    public @NotNull IScope getScope() {
      return scope;
    }

    public @NotNull SentryOptions getOptions() {
      return options;
    }
  }

  private final @NotNull Deque<StackItem> items = new LinkedBlockingDeque<>();
  private final @NotNull ILogger logger;
  private final @NotNull AutoClosableReentrantLock itemsLock = new AutoClosableReentrantLock();

  public Stack(final @NotNull ILogger logger, final @NotNull StackItem rootStackItem) {
    this.logger = Objects.requireNonNull(logger, "logger is required");
    this.items.push(Objects.requireNonNull(rootStackItem, "rootStackItem is required"));
  }

  public Stack(final @NotNull Stack stack) {
    this(stack.logger, new StackItem(stack.items.getLast()));
    final Iterator<StackItem> iterator = stack.items.descendingIterator();
    // skip first item (root item)
    if (iterator.hasNext()) {
      iterator.next();
    }
    while (iterator.hasNext()) {
      push(new StackItem(iterator.next()));
    }
  }

  @NotNull
  StackItem peek() {
    // peek can never return null since Stack can be created only with an item and pop does not drop
    // the last item.
    return items.peek();
  }

  void pop() {
    try (final @NotNull ISentryLifecycleToken ignored = itemsLock.acquire()) {
      if (items.size() != 1) {
        items.pop();
      } else {
        if (logger.isEnabled(SentryLevel.WARNING)) {
          logger.log(SentryLevel.WARNING, "Attempt to pop the root scope.");
        }
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

package io.sentry;

import io.sentry.hints.SessionEndHint;
import io.sentry.hints.SessionStartHint;
import io.sentry.protocol.SentryId;
import io.sentry.protocol.User;
import io.sentry.util.Objects;
import java.io.Closeable;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Hub implements IHub {

  private static final class StackItem {
    private volatile @NotNull ISentryClient client;
    private volatile @NotNull Scope scope;

    StackItem(final @NotNull ISentryClient client, final @NotNull Scope scope) {
      this.client = Objects.requireNonNull(client, "ISentryClient is required.");
      this.scope = Objects.requireNonNull(scope, "Scope is required.");
    }
  }

  private volatile @NotNull SentryId lastEventId;
  private final @NotNull SentryOptions options;
  private volatile boolean isEnabled;
  private final @NotNull Deque<StackItem> stack = new LinkedBlockingDeque<>();

  public Hub(final @NotNull SentryOptions options) {
    this(options, createRootStackItem(options));

    // Integrations are no longer registed on Hub ctor, but on Sentry.init
  }

  private Hub(final @NotNull SentryOptions options, final @Nullable StackItem rootStackItem) {
    validateOptions(options);

    this.options = options;
    if (rootStackItem != null) {
      this.stack.push(rootStackItem);
    }
    this.lastEventId = SentryId.EMPTY_ID;

    // Integrations will use this Hub instance once registered.
    // Make sure Hub ready to be used then.
    this.isEnabled = true;
  }

  private static void validateOptions(final @NotNull SentryOptions options) {
    Objects.requireNonNull(options, "SentryOptions is required.");
    if (options.getDsn() == null || options.getDsn().isEmpty()) {
      throw new IllegalArgumentException(
          "Hub requires a DSN to be instantiated. Considering using the NoOpHub is no DSN is available.");
    }
  }

  private static StackItem createRootStackItem(final @NotNull SentryOptions options) {
    validateOptions(options);
    final Scope scope = new Scope(options);
    final ISentryClient client = new SentryClient(options);
    return new StackItem(client, scope);
  }

  @Override
  public boolean isEnabled() {
    return isEnabled;
  }

  @Override
  public @NotNull SentryId captureEvent(
      final @NotNull SentryEvent event, final @Nullable Object hint) {
    SentryId sentryId = SentryId.EMPTY_ID;
    if (!isEnabled()) {
      options
          .getLogger()
          .log(
              SentryLevel.WARNING, "Instance is disabled and this 'captureEvent' call is a no-op.");
    } else if (event == null) {
      options.getLogger().log(SentryLevel.WARNING, "captureEvent called with null parameter.");
    } else {
      try {
        final StackItem item = stack.peek();
        if (item != null) {
          sentryId = item.client.captureEvent(event, item.scope, hint);
        } else {
          options.getLogger().log(SentryLevel.FATAL, "Stack peek was null when captureEvent");
        }
      } catch (Exception e) {
        options
            .getLogger()
            .log(
                SentryLevel.ERROR, "Error while capturing event with id: " + event.getEventId(), e);
      }
    }
    this.lastEventId = sentryId;
    return sentryId;
  }

  @Override
  public @NotNull SentryId captureMessage(
      final @NotNull String message, final @NotNull SentryLevel level) {
    SentryId sentryId = SentryId.EMPTY_ID;
    if (!isEnabled()) {
      options
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "Instance is disabled and this 'captureMessage' call is a no-op.");
    } else if (message == null) {
      options.getLogger().log(SentryLevel.WARNING, "captureMessage called with null parameter.");
    } else {
      try {
        final StackItem item = stack.peek();
        if (item != null) {
          sentryId = item.client.captureMessage(message, level, item.scope);
        } else {
          options.getLogger().log(SentryLevel.FATAL, "Stack peek was null when captureMessage");
        }
      } catch (Exception e) {
        options.getLogger().log(SentryLevel.ERROR, "Error while capturing message: " + message, e);
      }
    }
    this.lastEventId = sentryId;
    return sentryId;
  }

  @ApiStatus.Internal
  @Override
  public SentryId captureEnvelope(
      final @NotNull SentryEnvelope envelope, final @Nullable Object hint) {
    Objects.requireNonNull(envelope, "SentryEnvelope is required.");

    SentryId sentryId = SentryId.EMPTY_ID;
    if (!isEnabled()) {
      options
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "Instance is disabled and this 'captureEnvelope' call is a no-op.");
    } else {
      try {
        final StackItem item = stack.peek();
        if (item != null) {
          sentryId = item.client.captureEnvelope(envelope, hint);
        } else {
          options.getLogger().log(SentryLevel.FATAL, "Stack peek was null when captureEnvelope");
        }
      } catch (Exception e) {
        options.getLogger().log(SentryLevel.ERROR, "Error while capturing envelope.", e);
      }
    }
    this.lastEventId = sentryId;
    return sentryId;
  }

  @Override
  public @NotNull SentryId captureException(
      final @NotNull Throwable throwable, final @Nullable Object hint) {
    SentryId sentryId = SentryId.EMPTY_ID;
    if (!isEnabled()) {
      options
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "Instance is disabled and this 'captureException' call is a no-op.");
    } else if (throwable == null) {
      options.getLogger().log(SentryLevel.WARNING, "captureException called with null parameter.");
    } else {
      try {
        final StackItem item = stack.peek();
        if (item != null) {
          sentryId = item.client.captureException(throwable, item.scope, hint);
        } else {
          options.getLogger().log(SentryLevel.FATAL, "Stack peek was null when captureException");
        }
      } catch (Exception e) {
        options
            .getLogger()
            .log(
                SentryLevel.ERROR, "Error while capturing exception: " + throwable.getMessage(), e);
      }
    }
    this.lastEventId = sentryId;
    return sentryId;
  }

  @Override
  public void startSession() {
    if (!isEnabled()) {
      options
          .getLogger()
          .log(
              SentryLevel.WARNING, "Instance is disabled and this 'startSession' call is a no-op.");
    } else {
      final StackItem item = this.stack.peek();
      if (item != null) {
        final Scope.SessionPair pair = item.scope.startSession();

        // TODO: add helper overload `captureSessions` to pass a list of sessions and submit a
        // single envelope
        // Or create the envelope here with both items and call `captureEnvelope`
        if (pair.getPrevious() != null) {
          item.client.captureSession(pair.getPrevious(), new SessionEndHint());
        }

        item.client.captureSession(pair.getCurrent(), new SessionStartHint());
      } else {
        options.getLogger().log(SentryLevel.FATAL, "Stack peek was null when startSession");
      }
    }
  }

  @Override
  public void endSession() {
    if (!isEnabled()) {
      options
          .getLogger()
          .log(SentryLevel.WARNING, "Instance is disabled and this 'endSession' call is a no-op.");
    } else {
      final StackItem item = this.stack.peek();
      if (item != null) {
        final Session previousSession = item.scope.endSession();
        if (previousSession != null) {
          item.client.captureSession(previousSession, new SessionEndHint());
        }
      } else {
        options.getLogger().log(SentryLevel.FATAL, "Stack peek was null when endSession");
      }
    }
  }

  @Override
  public void close() {
    if (!isEnabled()) {
      options
          .getLogger()
          .log(SentryLevel.WARNING, "Instance is disabled and this 'close' call is a no-op.");
    } else {
      try {
        for (Integration integration : options.getIntegrations()) {
          if (integration instanceof Closeable) {
            ((Closeable) integration).close();
          }
        }
        options.getExecutorService().close(options.getShutdownTimeout());

        // Close the top-most client
        final StackItem item = stack.peek();
        if (item != null) {
          // TODO: should we end session before closing client?

          item.client.close();
        } else {
          options.getLogger().log(SentryLevel.FATAL, "Stack peek was NULL when closing Hub");
        }
      } catch (Exception e) {
        options.getLogger().log(SentryLevel.ERROR, "Error while closing the Hub.", e);
      }
      isEnabled = false;
    }
  }

  @Override
  public void addBreadcrumb(final @NotNull Breadcrumb breadcrumb, final @Nullable Object hint) {
    if (!isEnabled()) {
      options
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "Instance is disabled and this 'addBreadcrumb' call is a no-op.");
    } else if (breadcrumb == null) {
      options.getLogger().log(SentryLevel.WARNING, "addBreadcrumb called with null parameter.");
    } else {
      final StackItem item = stack.peek();
      if (item != null) {
        item.scope.addBreadcrumb(breadcrumb, hint);
      } else {
        options.getLogger().log(SentryLevel.FATAL, "Stack peek was null when addBreadcrumb");
      }
    }
  }

  @Override
  public void setLevel(final @Nullable SentryLevel level) {
    if (!isEnabled()) {
      options
          .getLogger()
          .log(SentryLevel.WARNING, "Instance is disabled and this 'setLevel' call is a no-op.");
    } else {
      final StackItem item = stack.peek();
      if (item != null) {
        item.scope.setLevel(level);
      } else {
        options.getLogger().log(SentryLevel.FATAL, "Stack peek was null when setLevel");
      }
    }
  }

  @Override
  public void setTransaction(final @Nullable String transaction) {
    if (!isEnabled()) {
      options
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "Instance is disabled and this 'setTransaction' call is a no-op.");
    } else {
      final StackItem item = stack.peek();
      if (item != null) {
        item.scope.setTransaction(transaction);
      } else {
        options.getLogger().log(SentryLevel.FATAL, "Stack peek was null when setTransaction");
      }
    }
  }

  @Override
  public void setUser(final @Nullable User user) {
    if (!isEnabled()) {
      options
          .getLogger()
          .log(SentryLevel.WARNING, "Instance is disabled and this 'setUser' call is a no-op.");
    } else {
      final StackItem item = stack.peek();
      if (item != null) {
        item.scope.setUser(user);
      } else {
        options.getLogger().log(SentryLevel.FATAL, "Stack peek was null when setUser");
      }
    }
  }

  @Override
  public void setFingerprint(final @NotNull List<String> fingerprint) {
    if (!isEnabled()) {
      options
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "Instance is disabled and this 'setFingerprint' call is a no-op.");
    } else if (fingerprint == null) {
      options.getLogger().log(SentryLevel.WARNING, "setFingerprint called with null parameter.");
    } else {
      final StackItem item = stack.peek();
      if (item != null) {
        item.scope.setFingerprint(fingerprint);
      } else {
        options.getLogger().log(SentryLevel.FATAL, "Stack peek was null when setFingerprint");
      }
    }
  }

  @Override
  public void clearBreadcrumbs() {
    if (!isEnabled()) {
      options
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "Instance is disabled and this 'clearBreadcrumbs' call is a no-op.");
    } else {
      final StackItem item = stack.peek();
      if (item != null) {
        item.scope.clearBreadcrumbs();
      } else {
        options.getLogger().log(SentryLevel.FATAL, "Stack peek was null when clearBreadcrumbs");
      }
    }
  }

  @Override
  public void setTag(final @NotNull String key, final @NotNull String value) {
    if (!isEnabled()) {
      options
          .getLogger()
          .log(SentryLevel.WARNING, "Instance is disabled and this 'setTag' call is a no-op.");
    } else if (key == null || value == null) {
      options.getLogger().log(SentryLevel.WARNING, "setTag called with null parameter.");
    } else {
      final StackItem item = stack.peek();
      if (item != null) {
        item.scope.setTag(key, value);
      } else {
        options.getLogger().log(SentryLevel.FATAL, "Stack peek was null when setTag");
      }
    }
  }

  @Override
  public void removeTag(final @NotNull String key) {
    if (!isEnabled()) {
      options
          .getLogger()
          .log(SentryLevel.WARNING, "Instance is disabled and this 'removeTag' call is a no-op.");
    } else if (key == null) {
      options.getLogger().log(SentryLevel.WARNING, "removeTag called with null parameter.");
    } else {
      final StackItem item = stack.peek();
      if (item != null) {
        item.scope.removeTag(key);
      } else {
        options.getLogger().log(SentryLevel.FATAL, "Stack peek was null when removeTag");
      }
    }
  }

  @Override
  public void setExtra(final @NotNull String key, final @NotNull String value) {
    if (!isEnabled()) {
      options
          .getLogger()
          .log(SentryLevel.WARNING, "Instance is disabled and this 'setExtra' call is a no-op.");
    } else if (key == null || value == null) {
      options.getLogger().log(SentryLevel.WARNING, "setExtra called with null parameter.");
    } else {
      final StackItem item = stack.peek();
      if (item != null) {
        item.scope.setExtra(key, value);
      } else {
        options.getLogger().log(SentryLevel.FATAL, "Stack peek was null when setExtra");
      }
    }
  }

  @Override
  public void removeExtra(final @NotNull String key) {
    if (!isEnabled()) {
      options
          .getLogger()
          .log(SentryLevel.WARNING, "Instance is disabled and this 'removeExtra' call is a no-op.");
    } else if (key == null) {
      options.getLogger().log(SentryLevel.WARNING, "removeExtra called with null parameter.");
    } else {
      final StackItem item = stack.peek();
      if (item != null) {
        item.scope.removeExtra(key);
      } else {
        options.getLogger().log(SentryLevel.FATAL, "Stack peek was null when removeExtra");
      }
    }
  }

  @Override
  public @NotNull SentryId getLastEventId() {
    return lastEventId;
  }

  @Override
  public void pushScope() {
    if (!isEnabled()) {
      options
          .getLogger()
          .log(SentryLevel.WARNING, "Instance is disabled and this 'pushScope' call is a no-op.");
    } else {
      final StackItem item = stack.peek();
      if (item != null) {
        Scope clone = null;
        try {
          clone = item.scope.clone();
        } catch (CloneNotSupportedException e) {
          options
              .getLogger()
              .log(SentryLevel.ERROR, "An error has occurred when cloning a Scope", e);
        }
        if (clone != null) {
          final StackItem newItem = new StackItem(item.client, clone);
          stack.push(newItem);
        }
      } else {
        options.getLogger().log(SentryLevel.FATAL, "Stack peek was NULL when pushScope");
      }
    }
  }

  @Override
  public void popScope() {
    if (!isEnabled()) {
      options
          .getLogger()
          .log(SentryLevel.WARNING, "Instance is disabled and this 'popScope' call is a no-op.");
    } else {
      // Don't drop the root scope
      synchronized (stack) {
        if (stack.size() != 1) {
          stack.pop();
        } else {
          options.getLogger().log(SentryLevel.WARNING, "Attempt to pop the root scope.");
        }
      }
    }
  }

  @Override
  public void withScope(final @NotNull ScopeCallback callback) {
    if (!isEnabled()) {
      options
          .getLogger()
          .log(SentryLevel.WARNING, "Instance is disabled and this 'withScope' call is a no-op.");
    } else {
      pushScope();
      final StackItem item = stack.peek();
      if (item != null) {
        try {
          callback.run(item.scope);
        } catch (Exception e) {
          options.getLogger().log(SentryLevel.ERROR, "Error in the 'withScope' callback.", e);
        }
      } else {
        options.getLogger().log(SentryLevel.FATAL, "Stack peek was null when withScope");
      }
      popScope();
    }
  }

  @Override
  public void configureScope(final @NotNull ScopeCallback callback) {
    if (!isEnabled()) {
      options
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "Instance is disabled and this 'configureScope' call is a no-op.");
    } else {
      final StackItem item = stack.peek();
      if (item != null) {
        try {
          callback.run(item.scope);
        } catch (Exception e) {
          options.getLogger().log(SentryLevel.ERROR, "Error in the 'configureScope' callback.", e);
        }
      } else {
        options.getLogger().log(SentryLevel.FATAL, "Stack peek was null when configureScope");
      }
    }
  }

  @Override
  public void bindClient(final @NotNull ISentryClient client) {
    if (!isEnabled()) {
      options
          .getLogger()
          .log(SentryLevel.WARNING, "Instance is disabled and this 'bindClient' call is a no-op.");
    } else {
      final StackItem item = stack.peek();
      if (item != null) {
        if (client != null) {
          options.getLogger().log(SentryLevel.DEBUG, "New client bound to scope.");
          item.client = client;
        } else {
          options.getLogger().log(SentryLevel.DEBUG, "NoOp client bound to scope.");
          item.client = NoOpSentryClient.getInstance();
        }
      } else {
        options.getLogger().log(SentryLevel.FATAL, "Stack peek was null when bindClient");
      }
    }
  }

  @Override
  public void flush(long timeoutMillis) {
    if (!isEnabled()) {
      options
          .getLogger()
          .log(SentryLevel.WARNING, "Instance is disabled and this 'flush' call is a no-op.");
    } else {
      final StackItem item = stack.peek();
      if (item != null) {
        try {
          item.client.flush(timeoutMillis);
        } catch (Exception e) {
          options.getLogger().log(SentryLevel.ERROR, "Error in the 'client.flush'.", e);
        }
      } else {
        options.getLogger().log(SentryLevel.FATAL, "Stack peek was null when flush");
      }
    }
  }

  @Override
  public @NotNull IHub clone() {
    if (!isEnabled()) {
      options.getLogger().log(SentryLevel.WARNING, "Disabled Hub cloned.");
    }
    // Clone will be invoked in parallel
    final Hub clone = new Hub(this.options, null);
    for (StackItem item : this.stack) {
      Scope clonedScope;
      try {
        clonedScope = item.scope.clone();
      } catch (CloneNotSupportedException e) {
        // TODO: Why do we need to deal with this? We must guarantee clone is possible here!
        options.getLogger().log(SentryLevel.ERROR, "Clone not supported");
        clonedScope = new Scope(options);
      }
      final StackItem cloneItem = new StackItem(item.client, clonedScope);
      clone.stack.push(cloneItem);
    }
    return clone;
  }

  @Override
  public SentryId captureTransaction(Transaction transaction, Object hint) {
    SentryId sentryId = SentryId.EMPTY_ID;
    if (!isEnabled()) {
      options
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "Instance is disabled and this 'captureTransaction' call is a no-op.");
    } else if (transaction == null) {
      options
          .getLogger()
          .log(SentryLevel.WARNING, "captureTransaction called with null parameter.");
    } else {
      try {
        final StackItem item = stack.peek();
        if (item != null) {
          sentryId = item.client.captureTransaction(transaction, item.scope, hint);
        } else {
          options.getLogger().log(SentryLevel.FATAL, "Stack peek was null when captureTransaction");
        }
      } catch (Exception e) {
        options
            .getLogger()
            .log(
                SentryLevel.ERROR,
                "Error while capturing event with id: " + transaction.getEventId(),
                e);
      }
    }
    this.lastEventId = sentryId;
    return sentryId;
  }

  @Override
  public @Nullable Transaction startTransaction(TransactionContexts transactionContexts) {
    Transaction transaction = null;
    if (!isEnabled()) {
      options
          .getLogger()
          .log(SentryLevel.WARNING, "Instance is disabled and this 'setExtra' call is a no-op.");
    } else {
      final StackItem item = stack.peek();
      if (item != null) {
        transaction = new Transaction(transactionContexts, this);
        item.scope.setTransaction(transaction);
      } else {
        options.getLogger().log(SentryLevel.FATAL, "Stack peek was null when setExtra");
      }
    }
    return transaction;
  }
}

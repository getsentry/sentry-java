package io.sentry.core;

import io.sentry.core.protocol.SentryId;
import io.sentry.core.protocol.User;
import io.sentry.core.util.Objects;
import java.io.Closeable;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Hub implements IHub {

  private static final class StackItem {
    private volatile @NotNull ISentryClient client;
    private volatile @NotNull Scope scope;

    StackItem(@NotNull ISentryClient client, @NotNull Scope scope) {
      this.client = client;
      this.scope = scope;
    }
  }

  private volatile @NotNull SentryId lastEventId;
  private final @NotNull SentryOptions options;
  private volatile boolean isEnabled;
  private final @NotNull Deque<StackItem> stack = new LinkedBlockingDeque<>();

  public Hub(@NotNull SentryOptions options) {
    this(options, createRootStackItem(options));

    // Register integrations against a root Hub
    for (Integration integration : options.getIntegrations()) {
      integration.register(HubAdapter.getInstance(), options);
    }
  }

  private Hub(@NotNull SentryOptions options, @Nullable StackItem rootStackItem) {
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

  private static void validateOptions(@NotNull SentryOptions options) {
    Objects.requireNonNull(options, "SentryOptions is required.");
    if (options.getDsn() == null || options.getDsn().isEmpty()) {
      throw new IllegalArgumentException(
          "Hub requires a DSN to be instantiated. Considering using the NoOpHub is no DSN is available.");
    }
  }

  private static StackItem createRootStackItem(@NotNull SentryOptions options) {
    validateOptions(options);
    Scope scope = new Scope(options);
    ISentryClient client = new SentryClient(options);
    return new StackItem(client, scope);
  }

  @Override
  public boolean isEnabled() {
    return isEnabled;
  }

  @Override
  public @NotNull SentryId captureEvent(@NotNull SentryEvent event, @Nullable Object hint) {
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
        StackItem item = stack.peek();
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
  public @NotNull SentryId captureMessage(@NotNull String message, @NotNull SentryLevel level) {
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
        StackItem item = stack.peek();
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

  @Override
  public @NotNull SentryId captureException(@NotNull Throwable throwable, @Nullable Object hint) {
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
        StackItem item = stack.peek();
        if (item != null) {
          sentryId = item.client.captureException(throwable, item.scope, hint);
        } else {
          options.getLogger().log(SentryLevel.FATAL, "Stack peek was null when captureException");
        }
      } catch (Exception e) {
        options
            .getLogger()
            .log(SentryLevel.ERROR, "Error while capturing message: " + throwable.getMessage(), e);
      }
    }
    this.lastEventId = sentryId;
    return sentryId;
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

        // Close the top-most client
        StackItem item = stack.peek();
        if (item != null) {
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
  public void addBreadcrumb(@NotNull Breadcrumb breadcrumb, @Nullable Object hint) {
    if (!isEnabled()) {
      options
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "Instance is disabled and this 'addBreadcrumb' call is a no-op.");
    } else if (breadcrumb == null) {
      options.getLogger().log(SentryLevel.WARNING, "addBreadcrumb called with null parameter.");
    } else {
      StackItem item = stack.peek();
      if (item != null) {
        item.scope.addBreadcrumb(breadcrumb, hint);
      } else {
        options.getLogger().log(SentryLevel.FATAL, "Stack peek was null when addBreadcrumb");
      }
    }
  }

  @Override
  public void setLevel(@Nullable SentryLevel level) {
    if (!isEnabled()) {
      options
          .getLogger()
          .log(SentryLevel.WARNING, "Instance is disabled and this 'setLevel' call is a no-op.");
    } else {
      StackItem item = stack.peek();
      if (item != null) {
        item.scope.setLevel(level);
      } else {
        options.getLogger().log(SentryLevel.FATAL, "Stack peek was null when setLevel");
      }
    }
  }

  @Override
  public void setTransaction(@Nullable String transaction) {
    if (!isEnabled()) {
      options
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "Instance is disabled and this 'setTransaction' call is a no-op.");
    } else {
      StackItem item = stack.peek();
      if (item != null) {
        item.scope.setTransaction(transaction);
      } else {
        options.getLogger().log(SentryLevel.FATAL, "Stack peek was null when setTransaction");
      }
    }
  }

  @Override
  public void setUser(@Nullable User user) {
    if (!isEnabled()) {
      options
          .getLogger()
          .log(SentryLevel.WARNING, "Instance is disabled and this 'setUser' call is a no-op.");
    } else {
      StackItem item = stack.peek();
      if (item != null) {
        item.scope.setUser(user);
      } else {
        options.getLogger().log(SentryLevel.FATAL, "Stack peek was null when setUser");
      }
    }
  }

  @Override
  public void setFingerprint(@NotNull List<String> fingerprint) {
    if (!isEnabled()) {
      options
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "Instance is disabled and this 'setFingerprint' call is a no-op.");
    } else if (fingerprint == null) {
      options.getLogger().log(SentryLevel.WARNING, "setFingerprint called with null parameter.");
    } else {
      StackItem item = stack.peek();
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
      StackItem item = stack.peek();
      if (item != null) {
        item.scope.clearBreadcrumbs();
      } else {
        options.getLogger().log(SentryLevel.FATAL, "Stack peek was null when clearBreadcrumbs");
      }
    }
  }

  @Override
  public void setTag(@NotNull String key, @NotNull String value) {
    if (!isEnabled()) {
      options
          .getLogger()
          .log(SentryLevel.WARNING, "Instance is disabled and this 'setTag' call is a no-op.");
    } else if (key == null || value == null) {
      options.getLogger().log(SentryLevel.WARNING, "setTag called with null parameter.");
    } else {
      StackItem item = stack.peek();
      if (item != null) {
        item.scope.setTag(key, value);
      } else {
        options.getLogger().log(SentryLevel.FATAL, "Stack peek was null when setTag");
      }
    }
  }

  @Override
  public void removeTag(@NotNull String key) {
    if (!isEnabled()) {
      options
          .getLogger()
          .log(SentryLevel.WARNING, "Instance is disabled and this 'removeTag' call is a no-op.");
    } else if (key == null) {
      options.getLogger().log(SentryLevel.WARNING, "removeTag called with null parameter.");
    } else {
      StackItem item = stack.peek();
      if (item != null) {
        item.scope.removeTag(key);
      } else {
        options.getLogger().log(SentryLevel.FATAL, "Stack peek was null when removeTag");
      }
    }
  }

  @Override
  public void setExtra(@NotNull String key, @NotNull String value) {
    if (!isEnabled()) {
      options
          .getLogger()
          .log(SentryLevel.WARNING, "Instance is disabled and this 'setExtra' call is a no-op.");
    } else if (key == null || value == null) {
      options.getLogger().log(SentryLevel.WARNING, "setExtra called with null parameter.");
    } else {
      StackItem item = stack.peek();
      if (item != null) {
        item.scope.setExtra(key, value);
      } else {
        options.getLogger().log(SentryLevel.FATAL, "Stack peek was null when setExtra");
      }
    }
  }

  @Override
  public void removeExtra(@NotNull String key) {
    if (!isEnabled()) {
      options
          .getLogger()
          .log(SentryLevel.WARNING, "Instance is disabled and this 'removeExtra' call is a no-op.");
    } else if (key == null) {
      options.getLogger().log(SentryLevel.WARNING, "removeExtra called with null parameter.");
    } else {
      StackItem item = stack.peek();
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
          .log(
              SentryLevel.WARNING,
              "Instance is disabled and this 'addBreadcrumb' call is a no-op.");
    } else {
      StackItem item = stack.peek();
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
          StackItem newItem = new StackItem(item.client, clone);
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
  public void withScope(@NotNull ScopeCallback callback) {
    if (!isEnabled()) {
      options
          .getLogger()
          .log(SentryLevel.WARNING, "Instance is disabled and this 'withScope' call is a no-op.");
    } else {
      pushScope();
      StackItem item = stack.peek();
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
  public void configureScope(@NotNull ScopeCallback callback) {
    if (!isEnabled()) {
      options
          .getLogger()
          .log(SentryLevel.WARNING, "Instance is disabled and this 'withScope' call is a no-op.");
    } else {
      StackItem item = stack.peek();
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
  public void bindClient(@NotNull ISentryClient client) {
    if (!isEnabled()) {
      options
          .getLogger()
          .log(SentryLevel.WARNING, "Instance is disabled and this 'bindClient' call is a no-op.");
    } else {
      StackItem item = stack.peek();
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
  public void flush(long timeoutMills) {
    if (!isEnabled()) {
      options
          .getLogger()
          .log(SentryLevel.WARNING, "Instance is disabled and this 'flush' call is a no-op.");
    } else {
      StackItem item = stack.peek();
      if (item != null) {
        try {
          item.client.flush(timeoutMills);
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
    Hub clone = new Hub(this.options, null);
    for (StackItem item : this.stack) {
      Scope clonedScope;
      try {
        clonedScope = item.scope.clone();
      } catch (CloneNotSupportedException e) {
        // TODO: Why do we need to deal with this? We must guarantee clone is possible here!
        options.getLogger().log(SentryLevel.ERROR, "Clone not supported");
        clonedScope = new Scope(options);
      }
      StackItem cloneItem = new StackItem(item.client, clonedScope);
      clone.stack.push(cloneItem);
    }
    return clone;
  }
}

package io.sentry.core;

import static io.sentry.core.ILogger.logIfNotNull;

import io.sentry.core.protocol.SentryId;
import io.sentry.core.util.Objects;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
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
  private final Deque<StackItem> stack = new LinkedBlockingDeque<>();

  public Hub(@NotNull SentryOptions options) {
    this(options, createRootStackItem(options));
  }

  private Hub(@NotNull SentryOptions options, @Nullable StackItem rootStackItem) {
    this.options = options;
    if (rootStackItem != null) {
      this.stack.push(rootStackItem);
    }
    this.lastEventId = SentryId.EMPTY_ID;

    // Integrations will use this Hub instance once registered.
    // Make sure Hub ready to be used then.
    this.isEnabled = true;

    for (Integration integration : options.getIntegrations()) {
      integration.register(this, options);
    }
  }

  private static StackItem createRootStackItem(@NotNull SentryOptions options) {
    Objects.requireNonNull(options, "SentryOptions is required.");
    Scope scope = new Scope(options.getMaxBreadcrumbs());
    ISentryClient client = new SentryClient(options);
    return new StackItem(client, scope);
  }

  @Override
  public boolean isEnabled() {
    return isEnabled;
  }

  @NotNull
  @Override
  public SentryId captureEvent(SentryEvent event) {
    SentryId sentryId = SentryId.EMPTY_ID;
    if (!isEnabled()) {
      logIfNotNull(
          options.getLogger(),
          SentryLevel.WARNING,
          "Instance is disabled and this 'captureEvent' call is a no-op.");
    } else if (event == null) {
      logIfNotNull(
          options.getLogger(), SentryLevel.WARNING, "captureEvent called with null parameter.");
    } else {
      try {
        StackItem item = stack.peek();
        if (item != null) {
          sentryId = item.client.captureEvent(event, item.scope);
        } else {
          logIfNotNull(
              options.getLogger(), SentryLevel.FATAL, "Stack peek was null when captureEvent");
        }
      } catch (Exception e) {
        logIfNotNull(
            options.getLogger(),
            SentryLevel.ERROR,
            "Error while capturing event with id: " + event.getEventId(),
            e);
      }
    }
    this.lastEventId = sentryId;
    return sentryId;
  }

  @NotNull
  @Override
  public SentryId captureMessage(String message) {
    SentryId sentryId = SentryId.EMPTY_ID;
    if (!isEnabled()) {
      logIfNotNull(
          options.getLogger(),
          SentryLevel.WARNING,
          "Instance is disabled and this 'captureMessage' call is a no-op.");
    } else if (message == null) {
      logIfNotNull(
          options.getLogger(), SentryLevel.WARNING, "captureMessage called with null parameter.");
    } else {
      try {
        StackItem item = stack.peek();
        if (item != null) {
          sentryId = item.client.captureMessage(message, item.scope);
        } else {
          logIfNotNull(
              options.getLogger(), SentryLevel.FATAL, "Stack peek was null when captureMessage");
        }
      } catch (Exception e) {
        logIfNotNull(
            options.getLogger(), SentryLevel.ERROR, "Error while capturing message: " + message, e);
      }
    }
    this.lastEventId = sentryId;
    return sentryId;
  }

  @NotNull
  @Override
  public SentryId captureException(Throwable throwable) {
    SentryId sentryId = SentryId.EMPTY_ID;
    if (!isEnabled()) {
      logIfNotNull(
          options.getLogger(),
          SentryLevel.WARNING,
          "Instance is disabled and this 'captureException' call is a no-op.");
    } else if (throwable == null) {
      logIfNotNull(
          options.getLogger(), SentryLevel.WARNING, "captureException called with null parameter.");
    } else {
      try {
        StackItem item = stack.peek();
        if (item != null) {
          sentryId = item.client.captureException(throwable, item.scope);
        } else {
          logIfNotNull(
              options.getLogger(), SentryLevel.FATAL, "Stack peek was null when captureException");
        }
      } catch (Exception e) {
        logIfNotNull(
            options.getLogger(),
            SentryLevel.ERROR,
            "Error while capturing message: " + throwable.getMessage(),
            e);
      }
    }
    this.lastEventId = sentryId;
    return sentryId;
  }

  @Override
  public void close() {
    if (!isEnabled()) {
      logIfNotNull(
          options.getLogger(),
          SentryLevel.WARNING,
          "Instance is disabled and this 'close' call is a no-op.");
    } else {
      try {
        // Close the top-most client
        StackItem item = stack.peek();
        if (item != null) {
          item.client.close();
        } else {
          logIfNotNull(
              options.getLogger(), SentryLevel.FATAL, "Stack peek was NULL when closing Hub");
        }
      } catch (Exception e) {
        logIfNotNull(options.getLogger(), SentryLevel.ERROR, "Error while closing the Hub.", e);
      }
      isEnabled = false;
    }
  }

  @Override
  public void addBreadcrumb(Breadcrumb breadcrumb) {
    if (!isEnabled()) {
      logIfNotNull(
          options.getLogger(),
          SentryLevel.WARNING,
          "Instance is disabled and this 'addBreadcrumb' call is a no-op.");
    } else if (breadcrumb == null) {
      logIfNotNull(
          options.getLogger(), SentryLevel.WARNING, "addBreadcrumb called with null parameter.");
    } else {
      StackItem item = stack.peek();
      if (item != null) {
        SentryOptions.BeforeBreadcrumbCallback callback = options.getBeforeBreadcrumb();
        if (callback != null) {
          breadcrumb = executeBeforeBreadcrumb(callback, breadcrumb);
        }
        if (breadcrumb != null) {
          item.scope.addBreadcrumb(breadcrumb);
        }
      } else {
        logIfNotNull(
            options.getLogger(), SentryLevel.FATAL, "Stack peek was null when addBreadcrumb");
      }
    }
  }

  private Breadcrumb executeBeforeBreadcrumb(
      SentryOptions.BeforeBreadcrumbCallback callback, Breadcrumb breadcrumb) {
    try {
      breadcrumb = callback.execute(breadcrumb);
    } catch (Exception e) {
      logIfNotNull(
          options.getLogger(),
          SentryLevel.ERROR,
          "The BeforeBreadcrumbCallback callback threw an exception. It will be added as breadcrumb and continue.",
          e);

      Map<String, String> data = breadcrumb.getData();
      if (breadcrumb.getData() == null) {
        data = new HashMap<>();
      }
      data.put("sentry:message", e.getMessage());
      StringWriter sw = new StringWriter();
      e.printStackTrace(new PrintWriter(sw));
      data.put("sentry:stacktrace", sw.toString());
      breadcrumb.setData(data);
    }
    return breadcrumb;
  }

  @NotNull
  @Override
  public SentryId getLastEventId() {
    return lastEventId;
  }

  @Override
  public void pushScope() {
    if (!isEnabled()) {
      logIfNotNull(
          options.getLogger(),
          SentryLevel.WARNING,
          "Instance is disabled and this 'addBreadcrumb' call is a no-op.");
    } else {
      StackItem item = stack.peek();
      if (item != null) {
        Scope clone = null;
        try {
          clone = item.scope.clone();
        } catch (CloneNotSupportedException e) {
          logIfNotNull(
              options.getLogger(),
              SentryLevel.ERROR,
              "An error has occurred when cloning a Scope",
              e);
        }
        if (clone != null) {
          StackItem newItem = new StackItem(item.client, clone);
          stack.push(newItem);
        }
      } else {
        logIfNotNull(options.getLogger(), SentryLevel.FATAL, "Stack peek was NULL when pushScope");
      }
    }
  }

  @Override
  public void popScope() {
    if (!isEnabled()) {
      logIfNotNull(
          options.getLogger(),
          SentryLevel.WARNING,
          "Instance is disabled and this 'popScope' call is a no-op.");
    } else {
      // Don't drop the root scope
      synchronized (stack) { // TODO: is it necessary? we should never sync a concurrent object
        if (stack.size() != 1) {
          stack.pop();
        } else {
          logIfNotNull(options.getLogger(), SentryLevel.WARNING, "Attempt to pop the root scope.");
        }
      }
    }
  }

  @Override
  public void withScope(@NotNull ScopeCallback callback) {
    if (!isEnabled()) {
      logIfNotNull(
          options.getLogger(),
          SentryLevel.WARNING,
          "Instance is disabled and this 'withScope' call is a no-op.");
    } else {
      pushScope();
      StackItem item = stack.peek();
      if (item != null) {
        try {
          callback.run(item.scope);
        } catch (Exception e) {
          logIfNotNull(
              options.getLogger(), SentryLevel.ERROR, "Error in the 'withScope' callback.", e);
        }
      } else {
        logIfNotNull(options.getLogger(), SentryLevel.FATAL, "Stack peek was null when withScope");
      }
      popScope();
    }
  }

  @Override
  public void configureScope(@NotNull ScopeCallback callback) {
    if (!isEnabled()) {
      logIfNotNull(
          options.getLogger(),
          SentryLevel.WARNING,
          "Instance is disabled and this 'withScope' call is a no-op.");
    } else {
      StackItem item = stack.peek();
      if (item != null) {
        try {
          callback.run(item.scope);
        } catch (Exception e) {
          logIfNotNull(
              options.getLogger(), SentryLevel.ERROR, "Error in the 'configureScope' callback.", e);
        }
      } else {
        logIfNotNull(
            options.getLogger(), SentryLevel.FATAL, "Stack peek was null when configureScope");
      }
    }
  }

  @Override
  public void bindClient(ISentryClient client) {
    if (!isEnabled()) {
      logIfNotNull(
          options.getLogger(),
          SentryLevel.WARNING,
          "Instance is disabled and this 'bindClient' call is a no-op.");
    } else {
      StackItem item = stack.peek();
      if (item != null) {
        if (client != null) {
          logIfNotNull(options.getLogger(), SentryLevel.DEBUG, "New client bound to scope.");
          item.client = client;
        } else {
          logIfNotNull(options.getLogger(), SentryLevel.DEBUG, "NoOp client bound to scope.");
          item.client = NoOpSentryClient.getInstance();
        }
      } else {
        logIfNotNull(options.getLogger(), SentryLevel.FATAL, "Stack peek was null when bindClient");
      }
    }
  }

  @Override
  public void flush(long timeoutMills) {
    if (!isEnabled()) {
      logIfNotNull(
          options.getLogger(),
          SentryLevel.WARNING,
          "Instance is disabled and this 'flush' call is a no-op.");
    } else {
      StackItem item = stack.peek();
      if (item != null) {
        try {
          item.client.flush(timeoutMills);
        } catch (Exception e) {
          logIfNotNull(options.getLogger(), SentryLevel.ERROR, "Error in the 'client.flush'.", e);
        }
      } else {
        logIfNotNull(options.getLogger(), SentryLevel.FATAL, "Stack peek was null when flush");
      }
    }
  }

  @NotNull
  @Override
  public IHub clone() {
    if (!isEnabled()) {
      logIfNotNull(options.getLogger(), SentryLevel.WARNING, "Disabled Hub cloned.");
    }
    // Clone will be invoked in parallel
    Hub clone = new Hub(this.options, null);
    for (StackItem item : this.stack) {
      clone.stack.push(item);
    }
    return clone;
  }
}

/*
 * Adapted from https://github.com/open-telemetry/opentelemetry-java/blob/0aacc55d1e3f5cc6dbb4f8fa26bcb657b01a7bc9/context/src/main/java/io/opentelemetry/context/ThreadLocalContextStorage.java
 *
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.sentry.opentelemetry;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextStorage;
import io.opentelemetry.context.Scope;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Workaround to make OpenTelemetry context storage work for Sentry since Sentry sometimes forks
 * Context without cleaning up. We are not yet sure if this is something we can easliy fix, since
 * Sentry static API makes heavy use of getCurrentScopes and there is no easy way of knowing when to
 * restore previous Context.
 */
@ApiStatus.Experimental
@ApiStatus.Internal
public final class SentryOtelThreadLocalStorage implements ContextStorage {
  private static final Logger logger =
      Logger.getLogger(SentryOtelThreadLocalStorage.class.getName());

  private static final ThreadLocal<Context> THREAD_LOCAL_STORAGE = new ThreadLocal<>();

  @Override
  public Scope attach(Context toAttach) {
    if (toAttach == null) {
      // Null context not allowed so ignore it.
      return NoopScope.INSTANCE;
    }

    Context beforeAttach = current();
    if (toAttach == beforeAttach) {
      return NoopScope.INSTANCE;
    }

    THREAD_LOCAL_STORAGE.set(toAttach);

    return new SentryScopeImpl(beforeAttach);
  }

  private static class SentryScopeImpl implements Scope {
    @Nullable private final Context beforeAttach;
    private boolean closed;

    private SentryScopeImpl(@Nullable Context beforeAttach) {
      this.beforeAttach = beforeAttach;
    }

    @Override
    public void close() {
      //      if (!closed && current() == toAttach) {
      // Used to make OTel thread local storage compatible with Sentry where cleanup isn't always
      // performed correctly
      if (!closed) {
        closed = true;
        THREAD_LOCAL_STORAGE.set(beforeAttach);
      } else {
        logger.log(
            Level.FINE,
            " Trying to close scope which does not represent current context. Ignoring the call.");
      }
    }
  }

  @Override
  @Nullable
  public Context current() {
    return THREAD_LOCAL_STORAGE.get();
  }

  enum NoopScope implements Scope {
    INSTANCE;

    @Override
    public void close() {}
  }
}

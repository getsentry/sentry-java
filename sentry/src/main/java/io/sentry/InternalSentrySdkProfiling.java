package io.sentry;

import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Sentry SDK internal API methods meant for being used by the Sentry Hybrid SDKs. */
@ApiStatus.Internal
public class InternalSentrySdkProfiling {

  /**
   * @return a copy of the current hub's topmost scope, or null in case the hub is disabled
   */
  @Nullable
  public static Scope getCurrentScope() {
    final @NotNull AtomicReference<Scope> scopeRef = new AtomicReference<>();
    HubAdapter.getInstance()
        .configureScope(
            scope -> {
              scopeRef.set(new Scope(scope));
            });
    return scopeRef.get();
  }
}

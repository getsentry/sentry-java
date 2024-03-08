package io.sentry;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Scopes {

  // TODO ctor without SentryOptions?
  public static final IScope ROOT_SCOPE = new Scope(new SentryOptions());
  public static final IScope ROOT_ISOLATION_SCOPE = new Scope(new SentryOptions());

  private final @NotNull IScope scope;
  private final @NotNull IScope isolationScope;
  // TODO just for debugging
  @SuppressWarnings("UnusedVariable")
  private final @Nullable Scopes parentScopes;

  private final @NotNull String creator;

  private Scopes(
      final @NotNull IScope scope,
      final @NotNull IScope isolationScope,
      final @NotNull String creator) {
    this.scope = scope;
    this.isolationScope = isolationScope;
    this.parentScopes = null;
    this.creator = creator;
  }

  private Scopes(
      final @NotNull IScope scope,
      final @NotNull IScope isolationScope,
      final @Nullable Scopes parentScopes,
      final @NotNull String creator) {
    this.scope = scope;
    this.isolationScope = isolationScope;
    this.parentScopes = parentScopes;
    this.creator = creator;
  }

  public @NotNull String getCreator() {
    return creator;
  }

  public @NotNull IScope getScope() {
    return scope;
  }

  public @NotNull IScope getIsolationScope() {
    return isolationScope;
  }

  public @Nullable Scopes getParent() {
    return parentScopes;
  }

  public boolean isAncestorOf(final @Nullable Scopes otherScopes) {
    if (otherScopes == null) {
      return false;
    }

    if (this == otherScopes) {
      return true;
    }

    final @Nullable Scopes parent = otherScopes.getParent();
    if (parent != null) {
      return isAncestorOf(parent);
    }

    return false;
  }

  public @NotNull Scopes forkedScopes(final @NotNull String creator) {
    return new Scopes(scope.clone(), isolationScope.clone(), this, creator);
  }

  public @NotNull Scopes forkedCurrentScope(final @NotNull String creator) {
    return new Scopes(scope.clone(), isolationScope, this, creator);
  }

  public static Scopes forkedRoots(final @NotNull String creator) {
    return new Scopes(ROOT_SCOPE.clone(), ROOT_ISOLATION_SCOPE.clone(), creator);
  }
}

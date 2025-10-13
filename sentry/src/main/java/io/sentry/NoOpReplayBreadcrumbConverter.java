package io.sentry;

import io.sentry.rrweb.RRWebEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class NoOpReplayBreadcrumbConverter implements ReplayBreadcrumbConverter {

  private static final NoOpReplayBreadcrumbConverter instance = new NoOpReplayBreadcrumbConverter();

  public static NoOpReplayBreadcrumbConverter getInstance() {
    return instance;
  }

  private NoOpReplayBreadcrumbConverter() {}

  @Override
  public @Nullable RRWebEvent convert(final @NotNull Breadcrumb breadcrumb) {
    return null;
  }

  @Override
  public void setUserBeforeBreadcrumbCallback(
      SentryOptions.@Nullable BeforeBreadcrumbCallback beforeBreadcrumbCallback) {}

  @Override
  public @Nullable Breadcrumb execute(
      final @NotNull Breadcrumb breadcrumb, final @NotNull Hint hint) {
    return breadcrumb;
  }
}

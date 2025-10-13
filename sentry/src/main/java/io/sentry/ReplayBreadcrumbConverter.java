package io.sentry;

import io.sentry.rrweb.RRWebEvent;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * ReplayBreadcrumbConverter will always be the registered {@link
 * io.sentry.SentryOptions.BeforeBreadcrumbCallback} for extending replays with data from breadcrumb
 * {@link io.sentry.Hint}s. As such, implementations need to wrap and delegate to any user-provided
 * BeforeBreadcrumbCallback {@link ReplayBreadcrumbConverter#setUserBeforeBreadcrumbCallback} .
 */
@ApiStatus.Internal
public interface ReplayBreadcrumbConverter extends SentryOptions.BeforeBreadcrumbCallback {
  @Nullable
  RRWebEvent convert(@NotNull Breadcrumb breadcrumb);

  void setUserBeforeBreadcrumbCallback(
      @Nullable SentryOptions.BeforeBreadcrumbCallback beforeBreadcrumbCallback);
}

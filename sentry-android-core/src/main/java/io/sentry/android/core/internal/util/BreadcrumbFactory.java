package io.sentry.android.core.internal.util;

import io.sentry.Breadcrumb;
import io.sentry.SentryLevel;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class BreadcrumbFactory {

  public static @NotNull Breadcrumb forSession(@NotNull String state) {
    final Breadcrumb breadcrumb = new Breadcrumb();
    breadcrumb.setType("session");
    breadcrumb.setData("state", state);
    breadcrumb.setCategory("app.lifecycle");
    breadcrumb.setLevel(SentryLevel.INFO);
    return breadcrumb;
  }
}

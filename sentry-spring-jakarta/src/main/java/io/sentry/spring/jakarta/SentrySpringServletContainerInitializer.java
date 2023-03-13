package io.sentry.spring.jakarta;

import com.jakewharton.nopen.annotation.Open;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterRegistration;
import jakarta.servlet.ServletContainerInitializer;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import java.util.EnumSet;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Servlet container initializer used to add the {@link SentrySpringFilter} to the {@link
 * ServletContext}.
 */
@Open
public class SentrySpringServletContainerInitializer implements ServletContainerInitializer {
  @Override
  public void onStartup(final @Nullable Set<Class<?>> c, final @NotNull ServletContext ctx)
      throws ServletException {
    final FilterRegistration.Dynamic dynamic =
        ctx.addFilter("sentrySpringFilter", SentrySpringFilter.class);
    if (dynamic != null) {
      dynamic.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), false, "/*");
    }
  }
}

package io.sentry.spring;

import com.jakewharton.nopen.annotation.Open;
import java.util.Set;
import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Servlet container initializer used to add the {@link SentrySpringRequestListener} to the {@link
 * ServletContext}.
 */
@Open
public class SentrySpringServletContainerInitializer implements ServletContainerInitializer {
  @Override
  public void onStartup(final @Nullable Set<Class<?>> c, final @NotNull ServletContext ctx)
      throws ServletException {
    ctx.addListener(SentrySpringRequestListener.class);
  }
}

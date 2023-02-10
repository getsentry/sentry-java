package io.sentry.servlet.jakarta;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.SentryIntegrationPackageStorage;
import jakarta.servlet.ServletContainerInitializer;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Servlet container initializer used to add the {@link SentryServletRequestListener} to the {@link
 * ServletContext}.
 */
@Open
public class SentryServletContainerInitializer implements ServletContainerInitializer {
  @Override
  public void onStartup(@Nullable Set<Class<?>> c, @NotNull ServletContext ctx)
      throws ServletException {
    ctx.addListener(SentryServletRequestListener.class);
    SentryIntegrationPackageStorage.addIntegration("Servlet-Jakarta");
    SentryIntegrationPackageStorage.addPackage(
        "maven:io.sentry:sentry-servlet-jakarta", BuildConfig.VERSION_NAME);
  }
}

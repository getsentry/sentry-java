package io.sentry.servlet;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.SentryIntegrationPackageStorage;
import java.util.Set;
import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Servlet container initializer used to add the {@link SentryServletRequestListener} to the {@link
 * ServletContext}.
 */
@Open
public class SentryServletContainerInitializer implements ServletContainerInitializer {

  static {
    SentryIntegrationPackageStorage.getInstance()
        .addPackage("maven:io.sentry:sentry-servlet", BuildConfig.VERSION_NAME);
  }

  @Override
  public void onStartup(@Nullable Set<Class<?>> c, @NotNull ServletContext ctx)
      throws ServletException {
    ctx.addListener(SentryServletRequestListener.class);
    SentryIntegrationPackageStorage.getInstance().addIntegration("Servlet");
  }
}

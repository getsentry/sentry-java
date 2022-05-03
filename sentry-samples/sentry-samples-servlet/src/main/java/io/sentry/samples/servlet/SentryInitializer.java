package io.sentry.samples.servlet;

import io.sentry.Sentry;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

/** Initializes Sentry. */
@WebListener
public final class SentryInitializer implements ServletContextListener {

  @Override
  public void contextInitialized(ServletContextEvent sce) {
    Sentry.init(
        options -> {
          // NOTE: Replace the test DSN below with YOUR OWN DSN to see the events from this app in
          // your Sentry project/dashboard
          options.setDsn(
              "https://502f25099c204a2fbf4cb16edc5975d1@o447951.ingest.sentry.io/5428563");

          // disables shutdown hook, as Sentry has to be closed on application undeploy.
          options.setEnableShutdownHook(false);

          // All events get assigned to the release. See more at
          // https://docs.sentry.io/workflow/releases/
          options.setRelease("io.sentry.samples.servlet@3.0.0+1");

          // Modifications to event before it goes out. Could replace the event altogether
          options.setBeforeSend(
              (event, hint) -> {
                // Drop an event altogether:
                if (event.getTag("SomeTag") != null) {
                  return null;
                }
                return event;
              });

          // Allows inspecting and modifying, returning a new or simply rejecting (returning null)
          options.setBeforeBreadcrumb(
              (breadcrumb, hint) -> {
                // Don't add breadcrumbs with message containing:
                if (breadcrumb.getMessage() != null
                    && breadcrumb.getMessage().contains("bad breadcrumb")) {
                  return null;
                }
                return breadcrumb;
              });

          // Configure the background worker which sends events to sentry:
          // Wait up to 5 seconds before shutdown while there are events to send.
          options.setShutdownTimeoutMillis(5000);

          // Enable SDK logging with Debug level
          options.setDebug(true);

          // Include frames from our package
          options.addInAppInclude("io.sentry.samples");
        });
  }

  @Override
  public void contextDestroyed(ServletContextEvent sce) {
    Sentry.flush(5000);
    Sentry.close();
  }
}

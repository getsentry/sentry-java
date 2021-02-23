package io.sentry.samples.console;

import io.sentry.Breadcrumb;
import io.sentry.EventProcessor;
import io.sentry.ISpan;
import io.sentry.Sentry;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.SpanStatus;

public class Main {

  public static void main(String[] args) throws InterruptedException {
    Sentry.init(
        options -> {
          // NOTE: Replace the test DSN below with YOUR OWN DSN to see the events from this app in
          // your Sentry project/dashboard
          options.setDsn(
              "https://502f25099c204a2fbf4cb16edc5975d1@o447951.ingest.sentry.io/5428563");

          // All events get assigned to the release. See more at
          // https://docs.sentry.io/workflow/releases/
          options.setRelease("io.sentry.samples.console@3.0.0+1");

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
          options.setShutdownTimeout(5000);

          // Enable SDK logging with Debug level
          options.setDebug(true);
          // To change the verbosity, use:
          // By default it's DEBUG.
          options.setDiagnosticLevel(
              SentryLevel
                  .DEBUG); //  A good option to have SDK debug log in prod is to use only level
          // ERROR here.

          // Exclude frames from some packages from being "inApp" so are hidden by default in Sentry
          // UI:
          options.addInAppExclude("org.jboss");

          // Performance configuration options
          // Set what percentage of traces should be collected
          options.setTracesSampleRate(1.0); // set 0.5 to send 50% of traces

          // Determine traces sample rate based on the sampling context
          options.setTracesSampler(
              context -> {
                // only 10% of transactions with "/product" prefix will be collected
                if (!context.getTransactionContext().getName().startsWith("/products")) {
                  return 0.1;
                } else {
                  return 1.0;
                }
              });
        });

    // Performance feature
    //
    // Transactions collect execution time of the piece of code that's executed between the start
    // and finish of transaction.
    try {
      ISpan transaction = Sentry.startTransaction("transaction name", "op");
      // Transactions can contain one or more Spans
      ISpan outerSpan = transaction.startChild("child");
      Thread.sleep(100);
      // Spans create a tree structure. Each span can have one ore more spans inside.
      ISpan innerSpan = outerSpan.startChild("jdbc", "select * from product where id = :id");
      innerSpan.setStatus(SpanStatus.OK);
      Thread.sleep(300);
      // Finish the span and mark the end time of the span execution.
      // Note: finishing spans does not sent them to Sentry
      innerSpan.finish();
      // Every SentryEvent reported during the execution of the transaction or a span, will have
      // trace
      // context attached
      Sentry.captureMessage("this message is connected to the outerSpan");
      outerSpan.finish();
      // marks transaction as finished and sends it together with all child spans to Sentry
      transaction.finish();

      // All events that have not been sent yet are being flushed on JVM exit. Events can be also
      // flushed manually:
      // Sentry.close();
    } catch (RuntimeException e) {
      e.printStackTrace();
    }
  }

  private static class SomeEventProcessor implements EventProcessor {
    @Override
    public SentryEvent process(SentryEvent event, Object hint) {
      // Here you can modify the event as you need
      if (event.getLevel() != null && event.getLevel().ordinal() > SentryLevel.INFO.ordinal()) {
        event.addBreadcrumb(new Breadcrumb("Processed by " + SomeEventProcessor.class));
      }

      return event;
    }
  }
}

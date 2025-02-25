package io.sentry.samples.console;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import io.sentry.Breadcrumb;
import io.sentry.EventProcessor;
import io.sentry.Hint;
import io.sentry.ISentryLifecycleToken;
import io.sentry.ISpan;
import io.sentry.ITransaction;
import io.sentry.Sentry;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.SpanStatus;
import io.sentry.protocol.Message;
import io.sentry.protocol.User;
import java.util.Collections;

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

          options.setBeforeSendTransaction(
              (transaction, hint) -> {
                // Drop a transaction:
                if (transaction.getTag("SomeTransactionTag") != null) {
                  return null;
                }

                return transaction;
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
          // To change the verbosity, use:
          // By default it's DEBUG.
          //          options.setDiagnosticLevel(
          //              SentryLevel
          //                  .ERROR); //  A good option to have SDK debug log in prod is to use
          // only level
          // ERROR here.
          options.setEnablePrettySerializationOutput(false);

          // Exclude frames from some packages from being "inApp" so are hidden by default in Sentry
          // UI:
          options.addInAppExclude("org.jboss");

          // Include frames from our package
          options.addInAppInclude("io.sentry.samples");

          // Performance configuration options
          // Set what percentage of traces should be collected
          options.setTracesSampleRate(1.0); // set 0.5 to send 50% of traces

          // Determine traces sample rate based on the sampling context
          //          options.setTracesSampler(
          //              context -> {
          //                // only 10% of transactions with "/product" prefix will be collected
          //                if (!context.getTransactionContext().getName().startsWith("/products"))
          // {
          //                  return 0.1;
          //                } else {
          //                  return 0.5;
          //                }
          //              });
        });

    Sentry.addBreadcrumb(
        "A 'bad breadcrumb' that will be rejected because of 'BeforeBreadcrumb callback above.'");

    // Data added to the root scope (no PushScope called up to this point)
    // The modifications done here will affect all events sent and will propagate to child scopes.
    Sentry.configureScope(
        scope -> {
          scope.addEventProcessor(new SomeEventProcessor());

          scope.setExtra("SomeExtraInfo", "Some value for extra info");
        });

    // Configures a scope which is only valid within the callback
    Sentry.withScope(
        scope -> {
          scope.setLevel(SentryLevel.FATAL);
          scope.setTransaction("main");

          // This message includes the data set to the scope in this block:
          Sentry.captureMessage("Fatal message!");
        });

    // Only data added to the scope on `configureScope` above is included.
    Sentry.captureMessage("Some warning!", SentryLevel.WARNING);

    // Sending exception:
    Exception exception = new RuntimeException("Some error!");
    Sentry.captureException(exception);

    // An event with breadcrumb and user data
    SentryEvent evt = new SentryEvent();
    Message msg = new Message();
    msg.setMessage("Detailed event");
    evt.setMessage(msg);
    evt.addBreadcrumb("Breadcrumb directly to the event");
    User user = new User();
    user.setUsername("some@user");
    evt.setUser(user);
    // Group all events with the following fingerprint:
    evt.setFingerprints(Collections.singletonList("NewClientDebug"));
    evt.setLevel(SentryLevel.DEBUG);
    Sentry.captureEvent(evt);

    int count = 10;
    for (int i = 0; i < count; i++) {
      String messageContent = "%d of %d items we'll wait to flush to Sentry!";
      Message message = new Message();
      message.setMessage(messageContent);
      message.setFormatted(String.format(messageContent, i, count));
      SentryEvent event = new SentryEvent();
      event.setMessage(message);

      final Hint hint = new Hint();
      hint.set("level", SentryLevel.DEBUG);
      Sentry.captureEvent(event, hint);
    }

    // Performance feature
    //
    // Transactions collect execution time of the piece of code that's executed between the start
    // and finish of transaction.
    ITransaction transaction = Sentry.startTransaction("transaction name", "op");
    // Transactions can contain one or more Spans
    ISpan outerSpan = transaction.startChild("child");
    Thread.sleep(100);
    // Spans create a tree structure. Each span can have one ore more spans inside.
    ISpan innerSpan = outerSpan.startChild("jdbc", "select * from product where id = :id");
    innerSpan.setStatus(SpanStatus.OK);
    Thread.sleep(300);
    // Finish the span and mark the end time of the span execution.
    // Note: finishing spans does not send them to Sentry
    innerSpan.finish();
    try (ISentryLifecycleToken outerScope = outerSpan.makeCurrent()) {
      Span otelSpan =
          GlobalOpenTelemetry.get()
              .getTracer("demoTracer", "1.0.0")
              .spanBuilder("otelSpan")
              .startSpan();
      try (Scope innerScope = otelSpan.makeCurrent()) {
        otelSpan.setAttribute("otel-attribute", "attribute-value");
        Thread.sleep(150);
        otelSpan.setStatus(StatusCode.OK);
      } finally {
        otelSpan.end();
      }
    }
    // Every SentryEvent reported during the execution of the transaction or a span, will have trace
    // context attached
    Sentry.captureMessage("this message is connected to the outerSpan");
    outerSpan.finish();
    // marks transaction as finished and sends it together with all child spans to Sentry
    transaction.finish();

    // All events that have not been sent yet are being flushed on JVM exit. Events can be also
    // flushed manually:
    // Sentry.close();
  }

  private static class SomeEventProcessor implements EventProcessor {
    @Override
    public SentryEvent process(SentryEvent event, Hint hint) {
      // Here you can modify the event as you need
      if (event.getLevel() != null && event.getLevel().ordinal() > SentryLevel.INFO.ordinal()) {
        event.addBreadcrumb(new Breadcrumb("Processed by " + SomeEventProcessor.class));
      }

      return event;
    }
  }
}

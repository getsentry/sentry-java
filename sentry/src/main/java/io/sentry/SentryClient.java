package io.sentry;

import io.sentry.hints.DiskFlushNotification;
import io.sentry.protocol.Contexts;
import io.sentry.protocol.SentryId;
import io.sentry.protocol.SentryTransaction;
import io.sentry.transport.ITransport;
import io.sentry.util.ApplyScopeUtils;
import io.sentry.util.Objects;
import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public final class SentryClient implements ISentryClient {
  static final String SENTRY_PROTOCOL_VERSION = "7";

  private boolean enabled;

  private final @NotNull SentryOptions options;
  private final @NotNull ITransport transport;
  private final @Nullable Random random;

  private final @NotNull SortBreadcrumbsByDate sortBreadcrumbsByDate = new SortBreadcrumbsByDate();

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  SentryClient(final @NotNull SentryOptions options) {
    this.options = Objects.requireNonNull(options, "SentryOptions is required.");
    this.enabled = true;

    ITransportFactory transportFactory = options.getTransportFactory();
    if (transportFactory instanceof NoOpTransportFactory) {
      transportFactory = new AsyncHttpTransportFactory();
      options.setTransportFactory(transportFactory);
    }

    final RequestDetailsResolver requestDetailsResolver = new RequestDetailsResolver(options);
    transport = transportFactory.create(options, requestDetailsResolver.resolve());

    this.random = options.getSampleRate() == null ? null : new Random();
  }

  private boolean shouldApplyScopeData(
      final @NotNull SentryBaseEvent event, final @Nullable Object hint) {
    if (ApplyScopeUtils.shouldApplyScopeData(hint)) {
      return true;
    } else {
      options
          .getLogger()
          .log(SentryLevel.DEBUG, "Event was cached so not applying scope: %s", event.getEventId());
      return false;
    }
  }

  @Override
  public @NotNull SentryId captureEvent(
      @NotNull SentryEvent event, final @Nullable Scope scope, final @Nullable Object hint) {
    Objects.requireNonNull(event, "SentryEvent is required.");

    options.getLogger().log(SentryLevel.DEBUG, "Capturing event: %s", event.getEventId());

    if (shouldApplyScopeData(event, hint)) {
      // Event has already passed through here before it was cached
      // Going through again could be reading data that is no longer relevant
      // i.e proguard id, app version, threads
      event = applyScope(event, scope, hint);

      if (event == null) {
        options.getLogger().log(SentryLevel.DEBUG, "Event was dropped by applyScope");
        return SentryId.EMPTY_ID;
      }
    }

    event = processEvent(event, hint, options.getEventProcessors());

    Session session = null;

    if (event != null) {
      session = updateSessionData(event, hint, scope);

      if (!sample()) {
        options
            .getLogger()
            .log(
                SentryLevel.DEBUG,
                "Event %s was dropped due to sampling decision.",
                event.getEventId());
        // setting event as null to not be sent as its been discarded by sample rate
        event = null;
      }
    }

    if (event != null) {
      if (event.getThrowable() != null
          && options.containsIgnoredExceptionForType(event.getThrowable())) {
        options
            .getLogger()
            .log(
                SentryLevel.DEBUG,
                "Event was dropped as the exception %s is ignored",
                event.getThrowable().getClass());
        return SentryId.EMPTY_ID;
      }
      event = executeBeforeSend(event, hint);

      if (event == null) {
        options.getLogger().log(SentryLevel.DEBUG, "Event was dropped by beforeSend");
      }
    }

    SentryId sentryId = SentryId.EMPTY_ID;
    if (event != null && event.getEventId() != null) {
      sentryId = event.getEventId();
    }

    try {
      final TraceState traceState =
          scope != null && scope.getTransaction() != null
              ? scope.getTransaction().traceState()
              : null;
      final SentryEnvelope envelope =
          buildEnvelope(event, getAttachmentsFromScope(scope), session, traceState);

      if (envelope != null) {
        transport.send(envelope, hint);
      }
    } catch (IOException e) {
      options.getLogger().log(SentryLevel.WARNING, e, "Capturing event %s failed.", sentryId);

      // if there was an error capturing the event, we return an emptyId
      sentryId = SentryId.EMPTY_ID;
    }

    return sentryId;
  }

  private @Nullable List<Attachment> getAttachmentsFromScope(@Nullable Scope scope) {
    if (scope != null) {
      return scope.getAttachments();
    } else {
      return null;
    }
  }

  private @Nullable SentryEnvelope buildEnvelope(
      final @Nullable SentryBaseEvent event,
      final @Nullable List<Attachment> attachments,
      final @Nullable Session session,
      final @Nullable TraceState traceState)
      throws IOException {
    SentryId sentryId = null;

    final List<SentryEnvelopeItem> envelopeItems = new ArrayList<>();

    if (event != null) {
      final SentryEnvelopeItem eventItem =
          SentryEnvelopeItem.fromEvent(options.getSerializer(), event);
      envelopeItems.add(eventItem);
      sentryId = event.getEventId();
    }

    if (session != null) {
      final SentryEnvelopeItem sessionItem =
          SentryEnvelopeItem.fromSession(options.getSerializer(), session);
      envelopeItems.add(sessionItem);
    }

    if (attachments != null) {
      for (final Attachment attachment : attachments) {
        final SentryEnvelopeItem attachmentItem =
            SentryEnvelopeItem.fromAttachment(attachment, options.getMaxAttachmentSize());
        envelopeItems.add(attachmentItem);
      }
    }

    if (!envelopeItems.isEmpty()) {
      final SentryEnvelopeHeader envelopeHeader =
          new SentryEnvelopeHeader(sentryId, options.getSdkVersion(), traceState);
      return new SentryEnvelope(envelopeHeader, envelopeItems);
    }

    return null;
  }

  @Nullable
  private SentryEvent processEvent(
      @NotNull SentryEvent event,
      final @Nullable Object hint,
      final @NotNull List<EventProcessor> eventProcessors) {
    for (final EventProcessor processor : eventProcessors) {
      try {
        event = processor.process(event, hint);
      } catch (Exception e) {
        options
            .getLogger()
            .log(
                SentryLevel.ERROR,
                e,
                "An exception occurred while processing event by processor: %s",
                processor.getClass().getName());
      }

      if (event == null) {
        options
            .getLogger()
            .log(
                SentryLevel.DEBUG,
                "Event was dropped by a processor: %s",
                processor.getClass().getName());
        break;
      }
    }
    return event;
  }

  @Nullable
  private SentryTransaction processTransaction(
      @NotNull SentryTransaction transaction,
      final @Nullable Object hint,
      final @NotNull List<EventProcessor> eventProcessors) {
    for (final EventProcessor processor : eventProcessors) {
      try {
        transaction = processor.process(transaction, hint);
      } catch (Exception e) {
        options
            .getLogger()
            .log(
                SentryLevel.ERROR,
                e,
                "An exception occurred while processing transaction by processor: %s",
                processor.getClass().getName());
      }

      if (transaction == null) {
        options
            .getLogger()
            .log(
                SentryLevel.DEBUG,
                "Transaction was dropped by a processor: %s",
                processor.getClass().getName());
        break;
      }
    }
    return transaction;
  }

  @Override
  public void captureUserFeedback(final @NotNull UserFeedback userFeedback) {
    Objects.requireNonNull(userFeedback, "SentryEvent is required.");

    if (SentryId.EMPTY_ID.equals(userFeedback.getEventId())) {
      options.getLogger().log(SentryLevel.WARNING, "Capturing userFeedback without a Sentry Id.");
      return;
    }
    options
        .getLogger()
        .log(SentryLevel.DEBUG, "Capturing userFeedback: %s", userFeedback.getEventId());

    try {
      final SentryEnvelope envelope = buildEnvelope(userFeedback);
      transport.send(envelope);
    } catch (IOException e) {
      options
          .getLogger()
          .log(
              SentryLevel.WARNING,
              e,
              "Capturing user feedback %s failed.",
              userFeedback.getEventId());
    }
  }

  private @NotNull SentryEnvelope buildEnvelope(final @NotNull UserFeedback userFeedback) {
    final List<SentryEnvelopeItem> envelopeItems = new ArrayList<>();

    final SentryEnvelopeItem userFeedbackItem =
        SentryEnvelopeItem.fromUserFeedback(options.getSerializer(), userFeedback);
    envelopeItems.add(userFeedbackItem);

    final SentryEnvelopeHeader envelopeHeader =
        new SentryEnvelopeHeader(userFeedback.getEventId(), options.getSdkVersion());

    return new SentryEnvelope(envelopeHeader, envelopeItems);
  }

  /**
   * Updates the session data based on the event, hint and scope data
   *
   * @param event the SentryEvent
   * @param hint the hint or null
   * @param scope the Scope or null
   */
  @TestOnly
  @Nullable
  Session updateSessionData(
      final @NotNull SentryEvent event, final @Nullable Object hint, final @Nullable Scope scope) {
    Session clonedSession = null;

    if (ApplyScopeUtils.shouldApplyScopeData(hint)) {
      if (scope != null) {
        clonedSession =
            scope.withSession(
                session -> {
                  if (session != null) {
                    Session.State status = null;
                    if (event.isCrashed()) {
                      status = Session.State.Crashed;
                    }

                    boolean crashedOrErrored = false;
                    if (Session.State.Crashed == status || event.isErrored()) {
                      crashedOrErrored = true;
                    }

                    String userAgent = null;
                    if (event.getRequest() != null && event.getRequest().getHeaders() != null) {
                      if (event.getRequest().getHeaders().containsKey("user-agent")) {
                        userAgent = event.getRequest().getHeaders().get("user-agent");
                      }
                    }

                    if (session.update(status, userAgent, crashedOrErrored)) {
                      // if hint is DiskFlushNotification, it means we have an uncaughtException
                      // and we can end the session.
                      if (hint instanceof DiskFlushNotification) {
                        session.end();
                      }
                    }
                  } else {
                    options
                        .getLogger()
                        .log(SentryLevel.INFO, "Session is null on scope.withSession");
                  }
                });
      } else {
        options.getLogger().log(SentryLevel.INFO, "Scope is null on client.captureEvent");
      }
    }
    return clonedSession;
  }

  @ApiStatus.Internal
  @Override
  public void captureSession(final @NotNull Session session, final @Nullable Object hint) {
    Objects.requireNonNull(session, "Session is required.");

    if (session.getRelease() == null || session.getRelease().isEmpty()) {
      options
          .getLogger()
          .log(SentryLevel.WARNING, "Sessions can't be captured without setting a release.");
      return;
    }

    SentryEnvelope envelope;
    try {
      envelope = SentryEnvelope.from(options.getSerializer(), session, options.getSdkVersion());
    } catch (IOException e) {
      options.getLogger().log(SentryLevel.ERROR, "Failed to capture session.", e);
      return;
    }

    captureEnvelope(envelope, hint);
  }

  @ApiStatus.Internal
  @Override
  public @NotNull SentryId captureEnvelope(
      final @NotNull SentryEnvelope envelope, final @Nullable Object hint) {
    Objects.requireNonNull(envelope, "SentryEnvelope is required.");

    try {
      transport.send(envelope, hint);
    } catch (IOException e) {
      options.getLogger().log(SentryLevel.ERROR, "Failed to capture envelope.", e);
      return SentryId.EMPTY_ID;
    }
    final SentryId eventId = envelope.getHeader().getEventId();
    if (eventId != null) {
      return eventId;
    } else {
      return SentryId.EMPTY_ID;
    }
  }

  @Override
  public @NotNull SentryId captureTransaction(
      @NotNull SentryTransaction transaction,
      @Nullable TraceState traceState,
      final @Nullable Scope scope,
      final @Nullable Object hint) {
    Objects.requireNonNull(transaction, "Transaction is required.");

    options
        .getLogger()
        .log(SentryLevel.DEBUG, "Capturing transaction: %s", transaction.getEventId());

    SentryId sentryId = SentryId.EMPTY_ID;
    if (transaction.getEventId() != null) {
      sentryId = transaction.getEventId();
    }

    if (shouldApplyScopeData(transaction, hint)) {
      transaction = applyScope(transaction, scope);

      if (transaction != null && scope != null) {
        transaction = processTransaction(transaction, hint, scope.getEventProcessors());
      }

      if (transaction == null) {
        options.getLogger().log(SentryLevel.DEBUG, "Transaction was dropped by applyScope");
      }
    }

    if (transaction != null) {
      transaction = processTransaction(transaction, hint, options.getEventProcessors());
    }

    if (transaction == null) {
      options.getLogger().log(SentryLevel.DEBUG, "Transaction was dropped by Event processors.");
      return SentryId.EMPTY_ID;
    }

    try {
      final SentryEnvelope envelope =
          buildEnvelope(
              transaction, filterForTransaction(getAttachmentsFromScope(scope)), null, traceState);
      if (envelope != null) {
        transport.send(envelope, hint);
      } else {
        sentryId = SentryId.EMPTY_ID;
      }
    } catch (IOException e) {
      options.getLogger().log(SentryLevel.WARNING, e, "Capturing transaction %s failed.", sentryId);
      // if there was an error capturing the event, we return an emptyId
      sentryId = SentryId.EMPTY_ID;
    }

    return sentryId;
  }

  private @Nullable List<Attachment> filterForTransaction(@Nullable List<Attachment> attachments) {
    if (attachments == null) {
      return null;
    }

    List<Attachment> attachmentsToSend = new ArrayList<>();
    for (Attachment attachment : attachments) {
      if (attachment.isAddToTransactions()) {
        attachmentsToSend.add(attachment);
      }
    }

    return attachmentsToSend;
  }

  private @Nullable SentryEvent applyScope(
      @NotNull SentryEvent event, final @Nullable Scope scope, final @Nullable Object hint) {
    if (scope != null) {
      applyScope(event, scope);

      if (event.getTransaction() == null) {
        event.setTransaction(scope.getTransactionName());
      }
      if (event.getFingerprints() == null) {
        event.setFingerprints(scope.getFingerprint());
      }
      // Level from scope exceptionally take precedence over the event
      if (scope.getLevel() != null) {
        event.setLevel(scope.getLevel());
      }
      // Set trace data from active span to connect events with transactions
      final ISpan span = scope.getSpan();
      if (event.getContexts().getTrace() == null && span != null) {
        event.getContexts().setTrace(span.getSpanContext());
      }

      event = processEvent(event, hint, scope.getEventProcessors());
    }
    return event;
  }

  private <T extends SentryBaseEvent> @NotNull T applyScope(
      final @NotNull T sentryBaseEvent, final @Nullable Scope scope) {
    if (scope != null) {
      if (sentryBaseEvent.getRequest() == null) {
        sentryBaseEvent.setRequest(scope.getRequest());
      }
      if (sentryBaseEvent.getUser() == null) {
        sentryBaseEvent.setUser(scope.getUser());
      }
      if (sentryBaseEvent.getTags() == null) {
        sentryBaseEvent.setTags(new HashMap<>(scope.getTags()));
      } else {
        for (Map.Entry<String, String> item : scope.getTags().entrySet()) {
          if (!sentryBaseEvent.getTags().containsKey(item.getKey())) {
            sentryBaseEvent.getTags().put(item.getKey(), item.getValue());
          }
        }
      }
      if (sentryBaseEvent.getBreadcrumbs() == null) {
        sentryBaseEvent.setBreadcrumbs(new ArrayList<>(scope.getBreadcrumbs()));
      } else {
        sortBreadcrumbsByDate(sentryBaseEvent, scope.getBreadcrumbs());
      }
      if (sentryBaseEvent.getExtras() == null) {
        sentryBaseEvent.setExtras(new HashMap<>(scope.getExtras()));
      } else {
        for (Map.Entry<String, Object> item : scope.getExtras().entrySet()) {
          if (!sentryBaseEvent.getExtras().containsKey(item.getKey())) {
            sentryBaseEvent.getExtras().put(item.getKey(), item.getValue());
          }
        }
      }
      final Contexts contexts = sentryBaseEvent.getContexts();
      for (Map.Entry<String, Object> entry : new Contexts(scope.getContexts()).entrySet()) {
        if (!contexts.containsKey(entry.getKey())) {
          contexts.put(entry.getKey(), entry.getValue());
        }
      }
    }
    return sentryBaseEvent;
  }

  private void sortBreadcrumbsByDate(
      final @NotNull SentryBaseEvent event, final @NotNull Collection<Breadcrumb> breadcrumbs) {
    final List<Breadcrumb> sortedBreadcrumbs = event.getBreadcrumbs();

    if (sortedBreadcrumbs != null && !breadcrumbs.isEmpty()) {
      sortedBreadcrumbs.addAll(breadcrumbs);
      Collections.sort(sortedBreadcrumbs, sortBreadcrumbsByDate);
    }
  }

  private @Nullable SentryEvent executeBeforeSend(
      @NotNull SentryEvent event, final @Nullable Object hint) {
    final SentryOptions.BeforeSendCallback beforeSend = options.getBeforeSend();
    if (beforeSend != null) {
      try {
        event = beforeSend.execute(event, hint);
      } catch (Exception e) {
        options
            .getLogger()
            .log(
                SentryLevel.ERROR,
                "The BeforeSend callback threw an exception. It will be added as breadcrumb and continue.",
                e);

        final Breadcrumb breadcrumb = new Breadcrumb();
        breadcrumb.setMessage("BeforeSend callback failed.");
        breadcrumb.setCategory("SentryClient");
        breadcrumb.setLevel(SentryLevel.ERROR);
        if (e.getMessage() != null) {
          breadcrumb.setData("sentry:message", e.getMessage());
        }
        event.addBreadcrumb(breadcrumb);
      }
    }
    return event;
  }

  @Override
  public void close() {
    options.getLogger().log(SentryLevel.INFO, "Closing SentryClient.");

    try {
      flush(options.getShutdownTimeout());
      transport.close();
    } catch (IOException e) {
      options
          .getLogger()
          .log(SentryLevel.WARNING, "Failed to close the connection to the Sentry Server.", e);
    }
    for (EventProcessor eventProcessor : options.getEventProcessors()) {
      if (eventProcessor instanceof Closeable) {
        try {
          ((Closeable) eventProcessor).close();
        } catch (IOException e) {
          options
              .getLogger()
              .log(
                  SentryLevel.WARNING,
                  "Failed to close the event processor {}.",
                  eventProcessor,
                  e);
        }
      }
    }
    enabled = false;
  }

  @Override
  public void flush(final long timeoutMillis) {
    transport.flush(timeoutMillis);
  }

  private boolean sample() {
    // https://docs.sentry.io/development/sdk-dev/features/#event-sampling
    if (options.getSampleRate() != null && random != null) {
      final double sampling = options.getSampleRate();
      return !(sampling < random.nextDouble()); // bad luck
    }
    return true;
  }

  private static final class SortBreadcrumbsByDate implements Comparator<Breadcrumb> {

    @SuppressWarnings({"JdkObsolete", "JavaUtilDate"})
    @Override
    public int compare(final @NotNull Breadcrumb b1, final @NotNull Breadcrumb b2) {
      return b1.getTimestamp().compareTo(b2.getTimestamp());
    }
  }
}

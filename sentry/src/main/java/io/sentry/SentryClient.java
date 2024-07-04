package io.sentry;

import io.sentry.clientreport.DiscardReason;
import io.sentry.exception.SentryEnvelopeException;
import io.sentry.hints.AbnormalExit;
import io.sentry.hints.Backfillable;
import io.sentry.hints.DiskFlushNotification;
import io.sentry.hints.TransactionEnd;
import io.sentry.metrics.EncodedMetrics;
import io.sentry.metrics.IMetricsClient;
import io.sentry.metrics.NoopMetricsAggregator;
import io.sentry.protocol.Contexts;
import io.sentry.protocol.SentryId;
import io.sentry.protocol.SentryTransaction;
import io.sentry.transport.ITransport;
import io.sentry.transport.RateLimiter;
import io.sentry.util.CheckInUtils;
import io.sentry.util.HintUtils;
import io.sentry.util.Objects;
import io.sentry.util.TracingUtils;
import java.io.Closeable;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public final class SentryClient implements ISentryClient, IMetricsClient {
  static final String SENTRY_PROTOCOL_VERSION = "7";

  private boolean enabled;

  private final @NotNull SentryOptions options;
  private final @NotNull ITransport transport;
  private final @Nullable SecureRandom random;
  private final @NotNull SortBreadcrumbsByDate sortBreadcrumbsByDate = new SortBreadcrumbsByDate();
  private final @NotNull IMetricsAggregator metricsAggregator;

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

    metricsAggregator =
        options.isEnableMetrics()
            ? new MetricsAggregator(options, this)
            : NoopMetricsAggregator.getInstance();

    this.random = options.getSampleRate() == null ? null : new SecureRandom();
  }

  private boolean shouldApplyScopeData(
      final @NotNull SentryBaseEvent event, final @NotNull Hint hint) {
    if (HintUtils.shouldApplyScopeData(hint)) {
      return true;
    } else {
      options
          .getLogger()
          .log(SentryLevel.DEBUG, "Event was cached so not applying scope: %s", event.getEventId());
      return false;
    }
  }

  private boolean shouldApplyScopeData(final @NotNull CheckIn event, final @NotNull Hint hint) {
    if (HintUtils.shouldApplyScopeData(hint)) {
      return true;
    } else {
      options
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "Check-in was cached so not applying scope: %s",
              event.getCheckInId());
      return false;
    }
  }

  @Override
  public @NotNull SentryId captureEvent(
      @NotNull SentryEvent event, final @Nullable IScope scope, @Nullable Hint hint) {
    Objects.requireNonNull(event, "SentryEvent is required.");

    if (hint == null) {
      hint = new Hint();
    }

    if (shouldApplyScopeData(event, hint)) {
      addScopeAttachmentsToHint(scope, hint);
    }

    options.getLogger().log(SentryLevel.DEBUG, "Capturing event: %s", event.getEventId());

    if (event != null) {
      final Throwable eventThrowable = event.getThrowable();
      if (eventThrowable != null && options.containsIgnoredExceptionForType(eventThrowable)) {
        options
            .getLogger()
            .log(
                SentryLevel.DEBUG,
                "Event was dropped as the exception %s is ignored",
                eventThrowable.getClass());
        options
            .getClientReportRecorder()
            .recordLostEvent(DiscardReason.EVENT_PROCESSOR, DataCategory.Error);
        return SentryId.EMPTY_ID;
      }
    }

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

    if (event != null) {
      event = executeBeforeSend(event, hint);

      if (event == null) {
        options.getLogger().log(SentryLevel.DEBUG, "Event was dropped by beforeSend");
        options
            .getClientReportRecorder()
            .recordLostEvent(DiscardReason.BEFORE_SEND, DataCategory.Error);
      }
    }

    if (event == null) {
      return SentryId.EMPTY_ID;
    }

    @Nullable
    Session sessionBeforeUpdate =
        scope != null ? scope.withSession((@Nullable Session session) -> {}) : null;
    @Nullable Session session = null;

    if (event != null) {
      // https://develop.sentry.dev/sdk/sessions/#terminal-session-states
      if (sessionBeforeUpdate == null || !sessionBeforeUpdate.isTerminated()) {
        session = updateSessionData(event, hint, scope);
      }

      if (!sample()) {
        options
            .getLogger()
            .log(
                SentryLevel.DEBUG,
                "Event %s was dropped due to sampling decision.",
                event.getEventId());
        options
            .getClientReportRecorder()
            .recordLostEvent(DiscardReason.SAMPLE_RATE, DataCategory.Error);
        // setting event as null to not be sent as its been discarded by sample rate
        event = null;
      }
    }

    final boolean shouldSendSessionUpdate =
        shouldSendSessionUpdateForDroppedEvent(sessionBeforeUpdate, session);

    if (event == null && !shouldSendSessionUpdate) {
      options
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "Not sending session update for dropped event as it did not cause the session health to change.");
      return SentryId.EMPTY_ID;
    }

    SentryId sentryId = SentryId.EMPTY_ID;
    if (event != null && event.getEventId() != null) {
      sentryId = event.getEventId();
    }

    if (event != null) {
      options.getReplayController().sendReplayForEvent(event, hint);
    }

    try {
      @Nullable TraceContext traceContext = null;
      if (HintUtils.hasType(hint, Backfillable.class)) {
        // for backfillable hint we synthesize Baggage from event values
        if (event != null) {
          final Baggage baggage = Baggage.fromEvent(event, options);
          traceContext = baggage.toTraceContext();
        }
      } else if (scope != null) {
        final @Nullable ITransaction transaction = scope.getTransaction();
        if (transaction != null) {
          traceContext = transaction.traceContext();
        } else {
          final @NotNull PropagationContext propagationContext =
              TracingUtils.maybeUpdateBaggage(scope, options);
          traceContext = propagationContext.traceContext();
        }
      }

      final boolean shouldSendAttachments = event != null;
      List<Attachment> attachments = shouldSendAttachments ? getAttachments(hint) : null;
      final @Nullable SentryEnvelope envelope =
          buildEnvelope(event, attachments, session, traceContext, null);

      hint.clear();
      if (envelope != null) {
        sentryId = sendEnvelope(envelope, hint);
      }
    } catch (IOException | SentryEnvelopeException e) {
      options.getLogger().log(SentryLevel.WARNING, e, "Capturing event %s failed.", sentryId);

      // if there was an error capturing the event, we return an emptyId
      sentryId = SentryId.EMPTY_ID;
    }

    // if we encountered a crash/abnormal exit finish tracing in order to persist and send
    // any running transaction / profiling data. We also finish session replay, and it has priority
    // over transactions as it takes longer to finalize replay than transactions, therefore
    // the replay_id will be the trigger for flushing and unblocking the thread in case of a crash
    if (scope != null) {
      finalizeTransaction(scope, hint);
      finalizeReplay(scope, hint);
    }

    return sentryId;
  }

  private void finalizeTransaction(final @NotNull IScope scope, final @NotNull Hint hint) {
    final @Nullable ITransaction transaction = scope.getTransaction();
    if (transaction != null) {
      if (HintUtils.hasType(hint, TransactionEnd.class)) {
        final Object sentrySdkHint = HintUtils.getSentrySdkHint(hint);
        if (sentrySdkHint instanceof DiskFlushNotification) {
          ((DiskFlushNotification) sentrySdkHint).setFlushable(transaction.getEventId());
          transaction.forceFinish(SpanStatus.ABORTED, false, hint);
        } else {
          transaction.forceFinish(SpanStatus.ABORTED, false, null);
        }
      }
    }
  }

  private void finalizeReplay(final @NotNull IScope scope, final @NotNull Hint hint) {
    final @Nullable SentryId replayId = scope.getReplayId();
    if (!SentryId.EMPTY_ID.equals(replayId)) {
      if (HintUtils.hasType(hint, TransactionEnd.class)) {
        final Object sentrySdkHint = HintUtils.getSentrySdkHint(hint);
        if (sentrySdkHint instanceof DiskFlushNotification) {
          ((DiskFlushNotification) sentrySdkHint).setFlushable(replayId);
        }
      }
    }
  }

  @Override
  public @NotNull SentryId captureReplayEvent(
      @NotNull SentryReplayEvent event, final @Nullable IScope scope, @Nullable Hint hint) {
    Objects.requireNonNull(event, "SessionReplay is required.");

    if (hint == null) {
      hint = new Hint();
    }

    if (shouldApplyScopeData(event, hint)) {
      applyScope(event, scope);
    }

    options.getLogger().log(SentryLevel.DEBUG, "Capturing session replay: %s", event.getEventId());

    SentryId sentryId = SentryId.EMPTY_ID;
    if (event.getEventId() != null) {
      sentryId = event.getEventId();
    }

    event = processReplayEvent(event, hint, options.getEventProcessors());

    if (event == null) {
      options.getLogger().log(SentryLevel.DEBUG, "Replay was dropped by Event processors.");
      return SentryId.EMPTY_ID;
    }

    try {
      @Nullable TraceContext traceContext = null;
      if (scope != null) {
        final @Nullable ITransaction transaction = scope.getTransaction();
        if (transaction != null) {
          traceContext = transaction.traceContext();
        } else {
          final @NotNull PropagationContext propagationContext =
              TracingUtils.maybeUpdateBaggage(scope, options);
          traceContext = propagationContext.traceContext();
        }
      }

      final SentryEnvelope envelope = buildEnvelope(event, hint.getReplayRecording(), traceContext);

      hint.clear();
      transport.send(envelope, hint);
    } catch (IOException e) {
      options.getLogger().log(SentryLevel.WARNING, e, "Capturing event %s failed.", sentryId);

      // if there was an error capturing the event, we return an emptyId
      sentryId = SentryId.EMPTY_ID;
    }

    return sentryId;
  }

  private void addScopeAttachmentsToHint(@Nullable IScope scope, @NotNull Hint hint) {
    if (scope != null) {
      hint.addAttachments(scope.getAttachments());
    }
  }

  private boolean shouldSendSessionUpdateForDroppedEvent(
      @Nullable Session sessionBeforeUpdate, @Nullable Session sessionAfterUpdate) {
    if (sessionAfterUpdate == null) {
      return false;
    }

    if (sessionBeforeUpdate == null) {
      return true;
    }

    final boolean didSessionMoveToCrashedState =
        sessionAfterUpdate.getStatus() == Session.State.Crashed
            && sessionBeforeUpdate.getStatus() != Session.State.Crashed;
    if (didSessionMoveToCrashedState) {
      return true;
    }

    final boolean didSessionMoveToErroredState =
        sessionAfterUpdate.errorCount() > 0 && sessionBeforeUpdate.errorCount() <= 0;
    if (didSessionMoveToErroredState) {
      return true;
    }

    return false;
  }

  private @Nullable List<Attachment> getAttachments(final @NotNull Hint hint) {
    @NotNull final List<Attachment> attachments = hint.getAttachments();

    @Nullable final Attachment screenshot = hint.getScreenshot();
    if (screenshot != null) {
      attachments.add(screenshot);
    }

    @Nullable final Attachment viewHierarchy = hint.getViewHierarchy();
    if (viewHierarchy != null) {
      attachments.add(viewHierarchy);
    }

    @Nullable final Attachment threadDump = hint.getThreadDump();
    if (threadDump != null) {
      attachments.add(threadDump);
    }

    return attachments;
  }

  private @Nullable SentryEnvelope buildEnvelope(
      final @Nullable SentryBaseEvent event,
      final @Nullable List<Attachment> attachments,
      final @Nullable Session session,
      final @Nullable TraceContext traceContext,
      final @Nullable ProfilingTraceData profilingTraceData)
      throws IOException, SentryEnvelopeException {
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

    if (profilingTraceData != null) {
      final SentryEnvelopeItem profilingTraceItem =
          SentryEnvelopeItem.fromProfilingTrace(
              profilingTraceData, options.getMaxTraceFileSize(), options.getSerializer());
      envelopeItems.add(profilingTraceItem);

      if (sentryId == null) {
        sentryId = new SentryId(profilingTraceData.getProfileId());
      }
    }

    if (attachments != null) {
      for (final Attachment attachment : attachments) {
        final SentryEnvelopeItem attachmentItem =
            SentryEnvelopeItem.fromAttachment(
                options.getSerializer(),
                options.getLogger(),
                attachment,
                options.getMaxAttachmentSize());
        envelopeItems.add(attachmentItem);
      }
    }

    if (!envelopeItems.isEmpty()) {
      final SentryEnvelopeHeader envelopeHeader =
          new SentryEnvelopeHeader(sentryId, options.getSdkVersion(), traceContext);

      return new SentryEnvelope(envelopeHeader, envelopeItems);
    }

    return null;
  }

  @Nullable
  private SentryEvent processEvent(
      @NotNull SentryEvent event,
      final @NotNull Hint hint,
      final @NotNull List<EventProcessor> eventProcessors) {
    for (final EventProcessor processor : eventProcessors) {
      try {
        // only wire backfillable events through the backfilling processors, skip from others, and
        // the other way around
        final boolean isBackfillingProcessor = processor instanceof BackfillingEventProcessor;
        final boolean isBackfillable = HintUtils.hasType(hint, Backfillable.class);
        if (isBackfillable && isBackfillingProcessor) {
          event = processor.process(event, hint);
        } else if (!isBackfillable && !isBackfillingProcessor) {
          event = processor.process(event, hint);
        }
      } catch (Throwable e) {
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
        options
            .getClientReportRecorder()
            .recordLostEvent(DiscardReason.EVENT_PROCESSOR, DataCategory.Error);
        break;
      }
    }
    return event;
  }

  @Nullable
  private SentryTransaction processTransaction(
      @NotNull SentryTransaction transaction,
      final @NotNull Hint hint,
      final @NotNull List<EventProcessor> eventProcessors) {
    for (final EventProcessor processor : eventProcessors) {
      final int spanCountBeforeProcessor = transaction.getSpans().size();
      try {
        transaction = processor.process(transaction, hint);
      } catch (Throwable e) {
        options
            .getLogger()
            .log(
                SentryLevel.ERROR,
                e,
                "An exception occurred while processing transaction by processor: %s",
                processor.getClass().getName());
      }
      final int spanCountAfterProcessor = transaction == null ? 0 : transaction.getSpans().size();

      if (transaction == null) {
        options
            .getLogger()
            .log(
                SentryLevel.DEBUG,
                "Transaction was dropped by a processor: %s",
                processor.getClass().getName());
        options
            .getClientReportRecorder()
            .recordLostEvent(DiscardReason.EVENT_PROCESSOR, DataCategory.Transaction);
        // If we drop a transaction, we are also dropping all its spans (+1 for the root span)
        options
            .getClientReportRecorder()
            .recordLostEvent(
                DiscardReason.EVENT_PROCESSOR, DataCategory.Span, spanCountBeforeProcessor + 1);
        break;
      } else if (spanCountAfterProcessor < spanCountBeforeProcessor) {
        // If the callback removed some spans, we report it
        final int droppedSpanCount = spanCountBeforeProcessor - spanCountAfterProcessor;
        options
            .getLogger()
            .log(
                SentryLevel.DEBUG,
                "%d spans were dropped by a processor: %s",
                droppedSpanCount,
                processor.getClass().getName());
        options
            .getClientReportRecorder()
            .recordLostEvent(DiscardReason.EVENT_PROCESSOR, DataCategory.Span, droppedSpanCount);
      }
    }
    return transaction;
  }

  @Nullable
  private SentryReplayEvent processReplayEvent(
      @NotNull SentryReplayEvent replayEvent,
      final @NotNull Hint hint,
      final @NotNull List<EventProcessor> eventProcessors) {
    for (final EventProcessor processor : eventProcessors) {
      try {
        replayEvent = processor.process(replayEvent, hint);
      } catch (Throwable e) {
        options
            .getLogger()
            .log(
                SentryLevel.ERROR,
                e,
                "An exception occurred while processing replay event by processor: %s",
                processor.getClass().getName());
      }

      if (replayEvent == null) {
        options
            .getLogger()
            .log(
                SentryLevel.DEBUG,
                "Replay event was dropped by a processor: %s",
                processor.getClass().getName());
        options
            .getClientReportRecorder()
            .recordLostEvent(DiscardReason.EVENT_PROCESSOR, DataCategory.Replay);
        break;
      }
    }
    return replayEvent;
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
      final @NotNull SentryEnvelope envelope = buildEnvelope(userFeedback);
      sendEnvelope(envelope, null);
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

  private @NotNull SentryEnvelope buildEnvelope(
      final @NotNull CheckIn checkIn, final @Nullable TraceContext traceContext) {
    final List<SentryEnvelopeItem> envelopeItems = new ArrayList<>();

    final SentryEnvelopeItem checkInItem =
        SentryEnvelopeItem.fromCheckIn(options.getSerializer(), checkIn);
    envelopeItems.add(checkInItem);

    final SentryEnvelopeHeader envelopeHeader =
        new SentryEnvelopeHeader(checkIn.getCheckInId(), options.getSdkVersion(), traceContext);

    return new SentryEnvelope(envelopeHeader, envelopeItems);
  }

  private @NotNull SentryEnvelope buildEnvelope(
      final @NotNull SentryReplayEvent event,
      final @Nullable ReplayRecording replayRecording,
      final @Nullable TraceContext traceContext) {
    final List<SentryEnvelopeItem> envelopeItems = new ArrayList<>();

    final SentryEnvelopeItem replayItem =
        SentryEnvelopeItem.fromReplay(
            options.getSerializer(), options.getLogger(), event, replayRecording);
    envelopeItems.add(replayItem);
    final SentryId sentryId = event.getEventId();

    final SentryEnvelopeHeader envelopeHeader =
        new SentryEnvelopeHeader(sentryId, options.getSdkVersion(), traceContext);

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
      final @NotNull SentryEvent event, final @NotNull Hint hint, final @Nullable IScope scope) {
    Session clonedSession = null;

    if (HintUtils.shouldApplyScopeData(hint)) {
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

                    final Object sentrySdkHint = HintUtils.getSentrySdkHint(hint);
                    @Nullable String abnormalMechanism = null;
                    if (sentrySdkHint instanceof AbnormalExit) {
                      abnormalMechanism = ((AbnormalExit) sentrySdkHint).mechanism();
                      status = Session.State.Abnormal;
                    }

                    if (session.update(status, userAgent, crashedOrErrored, abnormalMechanism)) {
                      // if session terminated we can end it.
                      if (session.isTerminated()) {
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
  public void captureSession(final @NotNull Session session, final @Nullable Hint hint) {
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
      final @NotNull SentryEnvelope envelope, @Nullable Hint hint) {
    Objects.requireNonNull(envelope, "SentryEnvelope is required.");

    if (hint == null) {
      hint = new Hint();
    }

    try {
      hint.clear();
      return sendEnvelope(envelope, hint);
    } catch (IOException e) {
      options.getLogger().log(SentryLevel.ERROR, "Failed to capture envelope.", e);
    }
    return SentryId.EMPTY_ID;
  }

  private @NotNull SentryId sendEnvelope(
      @NotNull final SentryEnvelope envelope, @Nullable final Hint hint) throws IOException {
    final @Nullable SentryOptions.BeforeEnvelopeCallback beforeEnvelopeCallback =
        options.getBeforeEnvelopeCallback();
    if (beforeEnvelopeCallback != null) {
      try {
        beforeEnvelopeCallback.execute(envelope, hint);
      } catch (Throwable e) {
        options
            .getLogger()
            .log(SentryLevel.ERROR, "The BeforeEnvelope callback threw an exception.", e);
      }
    }
    if (hint == null) {
      transport.send(envelope);
    } else {
      transport.send(envelope, hint);
    }
    final @Nullable SentryId id = envelope.getHeader().getEventId();
    return id != null ? id : SentryId.EMPTY_ID;
  }

  @Override
  public @NotNull SentryId captureTransaction(
      @NotNull SentryTransaction transaction,
      @Nullable TraceContext traceContext,
      final @Nullable IScope scope,
      @Nullable Hint hint,
      final @Nullable ProfilingTraceData profilingTraceData) {
    Objects.requireNonNull(transaction, "Transaction is required.");

    if (hint == null) {
      hint = new Hint();
    }

    if (shouldApplyScopeData(transaction, hint)) {
      addScopeAttachmentsToHint(scope, hint);
    }

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

    final int spanCountBeforeCallback = transaction.getSpans().size();
    transaction = executeBeforeSendTransaction(transaction, hint);
    final int spanCountAfterCallback = transaction == null ? 0 : transaction.getSpans().size();

    if (transaction == null) {
      options
          .getLogger()
          .log(SentryLevel.DEBUG, "Transaction was dropped by beforeSendTransaction.");
      options
          .getClientReportRecorder()
          .recordLostEvent(DiscardReason.BEFORE_SEND, DataCategory.Transaction);
      // If we drop a transaction, we are also dropping all its spans (+1 for the root span)
      options
          .getClientReportRecorder()
          .recordLostEvent(
              DiscardReason.BEFORE_SEND, DataCategory.Span, spanCountBeforeCallback + 1);
      return SentryId.EMPTY_ID;
    } else if (spanCountAfterCallback < spanCountBeforeCallback) {
      // If the callback removed some spans, we report it
      final int droppedSpanCount = spanCountBeforeCallback - spanCountAfterCallback;
      options
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "%d spans were dropped by beforeSendTransaction.",
              droppedSpanCount);
      options
          .getClientReportRecorder()
          .recordLostEvent(DiscardReason.BEFORE_SEND, DataCategory.Span, droppedSpanCount);
    }

    try {
      final SentryEnvelope envelope =
          buildEnvelope(
              transaction,
              filterForTransaction(getAttachments(hint)),
              null,
              traceContext,
              profilingTraceData);

      hint.clear();
      if (envelope != null) {
        sentryId = sendEnvelope(envelope, hint);
      }
    } catch (IOException | SentryEnvelopeException e) {
      options.getLogger().log(SentryLevel.WARNING, e, "Capturing transaction %s failed.", sentryId);
      // if there was an error capturing the event, we return an emptyId
      sentryId = SentryId.EMPTY_ID;
    }

    return sentryId;
  }

  @Override
  @ApiStatus.Experimental
  public @NotNull SentryId captureCheckIn(
      @NotNull CheckIn checkIn, final @Nullable IScope scope, @Nullable Hint hint) {
    if (hint == null) {
      hint = new Hint();
    }

    if (checkIn.getEnvironment() == null) {
      checkIn.setEnvironment(options.getEnvironment());
    }

    if (checkIn.getRelease() == null) {
      checkIn.setRelease(options.getRelease());
    }

    if (shouldApplyScopeData(checkIn, hint)) {
      checkIn = applyScope(checkIn, scope);
    }

    if (CheckInUtils.isIgnored(options.getIgnoredCheckIns(), checkIn.getMonitorSlug())) {
      options
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "Check-in was dropped as slug %s is ignored",
              checkIn.getMonitorSlug());
      // TODO in a follow up PR with DataCategory.Monitor
      //      options
      //        .getClientReportRecorder()
      //        .recordLostEvent(DiscardReason.EVENT_PROCESSOR, DataCategory.Error);
      return SentryId.EMPTY_ID;
    }

    options.getLogger().log(SentryLevel.DEBUG, "Capturing check-in: %s", checkIn.getCheckInId());

    SentryId sentryId = checkIn.getCheckInId();

    try {
      @Nullable TraceContext traceContext = null;
      if (scope != null) {
        final @Nullable ITransaction transaction = scope.getTransaction();
        if (transaction != null) {
          traceContext = transaction.traceContext();
        } else {
          final @NotNull PropagationContext propagationContext =
              TracingUtils.maybeUpdateBaggage(scope, options);
          traceContext = propagationContext.traceContext();
        }
      }

      final @NotNull SentryEnvelope envelope = buildEnvelope(checkIn, traceContext);

      hint.clear();
      sentryId = sendEnvelope(envelope, hint);
    } catch (IOException e) {
      options.getLogger().log(SentryLevel.WARNING, e, "Capturing check-in %s failed.", sentryId);
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
      @NotNull SentryEvent event, final @Nullable IScope scope, final @NotNull Hint hint) {
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
      if (event.getContexts().getTrace() == null) {
        if (span == null) {
          event
              .getContexts()
              .setTrace(TransactionContext.fromPropagationContext(scope.getPropagationContext()));
        } else {
          event.getContexts().setTrace(span.getSpanContext());
        }
      }

      event = processEvent(event, hint, scope.getEventProcessors());
    }
    return event;
  }

  private @NotNull CheckIn applyScope(@NotNull CheckIn checkIn, final @Nullable IScope scope) {
    if (scope != null) {
      // Set trace data from active span to connect events with transactions
      final ISpan span = scope.getSpan();
      if (checkIn.getContexts().getTrace() == null) {
        if (span == null) {
          checkIn
              .getContexts()
              .setTrace(TransactionContext.fromPropagationContext(scope.getPropagationContext()));
        } else {
          checkIn.getContexts().setTrace(span.getSpanContext());
        }
      }
    }
    return checkIn;
  }

  private @NotNull SentryReplayEvent applyScope(
      final @NotNull SentryReplayEvent replayEvent, final @Nullable IScope scope) {
    // no breadcrumbs and extras for replay events
    if (scope != null) {
      if (replayEvent.getRequest() == null) {
        replayEvent.setRequest(scope.getRequest());
      }
      if (replayEvent.getUser() == null) {
        replayEvent.setUser(scope.getUser());
      }
      if (replayEvent.getTags() == null) {
        replayEvent.setTags(new HashMap<>(scope.getTags()));
      } else {
        for (Map.Entry<String, String> item : scope.getTags().entrySet()) {
          if (!replayEvent.getTags().containsKey(item.getKey())) {
            replayEvent.getTags().put(item.getKey(), item.getValue());
          }
        }
      }
      final Contexts contexts = replayEvent.getContexts();
      for (Map.Entry<String, Object> entry : new Contexts(scope.getContexts()).entrySet()) {
        if (!contexts.containsKey(entry.getKey())) {
          contexts.put(entry.getKey(), entry.getValue());
        }
      }

      // Set trace data from active span to connect replays with transactions
      final ISpan span = scope.getSpan();
      if (replayEvent.getContexts().getTrace() == null) {
        if (span == null) {
          replayEvent
              .getContexts()
              .setTrace(TransactionContext.fromPropagationContext(scope.getPropagationContext()));
        } else {
          replayEvent.getContexts().setTrace(span.getSpanContext());
        }
      }
    }
    return replayEvent;
  }

  private <T extends SentryBaseEvent> @NotNull T applyScope(
      final @NotNull T sentryBaseEvent, final @Nullable IScope scope) {
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
      @NotNull SentryEvent event, final @NotNull Hint hint) {
    final SentryOptions.BeforeSendCallback beforeSend = options.getBeforeSend();
    if (beforeSend != null) {
      try {
        event = beforeSend.execute(event, hint);
      } catch (Throwable e) {
        options
            .getLogger()
            .log(
                SentryLevel.ERROR,
                "The BeforeSend callback threw an exception. It will be added as breadcrumb and continue.",
                e);

        // drop event in case of an error in beforeSend due to PII concerns
        event = null;
      }
    }
    return event;
  }

  private @Nullable SentryTransaction executeBeforeSendTransaction(
      @NotNull SentryTransaction transaction, final @NotNull Hint hint) {
    final SentryOptions.BeforeSendTransactionCallback beforeSendTransaction =
        options.getBeforeSendTransaction();
    if (beforeSendTransaction != null) {
      try {
        transaction = beforeSendTransaction.execute(transaction, hint);
      } catch (Throwable e) {
        options
            .getLogger()
            .log(
                SentryLevel.ERROR,
                "The BeforeSendTransaction callback threw an exception. It will be added as breadcrumb and continue.",
                e);

        // drop transaction in case of an error in beforeSend due to PII concerns
        transaction = null;
      }
    }
    return transaction;
  }

  @Override
  public void close() {
    close(false);
  }

  @Override
  public void close(final boolean isRestarting) {
    options.getLogger().log(SentryLevel.INFO, "Closing SentryClient.");
    try {
      metricsAggregator.close();
    } catch (IOException e) {
      options.getLogger().log(SentryLevel.WARNING, "Failed to close the metrics aggregator.", e);
    }
    try {
      flush(isRestarting ? 0 : options.getShutdownTimeoutMillis());
      transport.close(isRestarting);
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

  @Override
  public @Nullable RateLimiter getRateLimiter() {
    return transport.getRateLimiter();
  }

  @Override
  public boolean isHealthy() {
    return transport.isHealthy();
  }

  private boolean sample() {
    // https://docs.sentry.io/development/sdk-dev/features/#event-sampling
    if (options.getSampleRate() != null && random != null) {
      final double sampling = options.getSampleRate();
      return !(sampling < random.nextDouble()); // bad luck
    }
    return true;
  }

  @Override
  public @NotNull IMetricsAggregator getMetricsAggregator() {
    return metricsAggregator;
  }

  @Override
  public @NotNull SentryId captureMetrics(final @NotNull EncodedMetrics metrics) {

    final @NotNull SentryEnvelopeItem envelopeItem = SentryEnvelopeItem.fromMetrics(metrics);
    final @NotNull SentryEnvelopeHeader envelopeHeader =
        new SentryEnvelopeHeader(new SentryId(), options.getSdkVersion(), null);

    final @NotNull SentryEnvelope envelope =
        new SentryEnvelope(envelopeHeader, Collections.singleton(envelopeItem));
    final @Nullable SentryId id = captureEnvelope(envelope);
    return id != null ? id : SentryId.EMPTY_ID;
  }

  private static final class SortBreadcrumbsByDate implements Comparator<Breadcrumb> {

    @SuppressWarnings({"JdkObsolete", "JavaUtilDate"})
    @Override
    public int compare(final @NotNull Breadcrumb b1, final @NotNull Breadcrumb b2) {
      return b1.getTimestamp().compareTo(b2.getTimestamp());
    }
  }
}

package io.sentry.android.core;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import io.sentry.DateUtils;
import io.sentry.HubAdapter;
import io.sentry.IHub;
import io.sentry.ILogger;
import io.sentry.IScope;
import io.sentry.ISerializer;
import io.sentry.ObjectWriter;
import io.sentry.SentryEnvelope;
import io.sentry.SentryEnvelopeItem;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.Session;
import io.sentry.android.core.performance.ActivityLifecycleTimeSpan;
import io.sentry.android.core.performance.AppStartMetrics;
import io.sentry.android.core.performance.TimeSpan;
import io.sentry.protocol.App;
import io.sentry.protocol.Device;
import io.sentry.protocol.SentryId;
import io.sentry.protocol.User;
import io.sentry.util.MapObjectWriter;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Sentry SDK internal API methods meant for being used by the Sentry Hybrid SDKs. */
@ApiStatus.Internal
public final class InternalSentrySdk {

  /**
   * @return a copy of the current hub's topmost scope, or null in case the hub is disabled
   */
  @Nullable
  public static IScope getCurrentScope() {
    final @NotNull AtomicReference<IScope> scopeRef = new AtomicReference<>();
    HubAdapter.getInstance()
        .configureScope(
            scope -> {
              scopeRef.set(scope.clone());
            });
    return scopeRef.get();
  }

  /**
   * Serializes the provided scope. Specific data may be back-filled (e.g. device context) if the
   * scope itself does not provide it.
   *
   * @param context Android context
   * @param options Sentry Options
   * @param scope the scope
   * @return a map containing all relevant scope data (user, contexts, tags, extras, fingerprint,
   *     level, breadcrumbs)
   */
  @NotNull
  public static Map<String, Object> serializeScope(
      final @NotNull Context context,
      final @NotNull SentryAndroidOptions options,
      final @Nullable IScope scope) {

    final @NotNull Map<String, Object> data = new HashMap<>();
    if (scope == null) {
      return data;
    }
    try {
      final @NotNull ILogger logger = options.getLogger();
      final @NotNull ObjectWriter writer = new MapObjectWriter(data);

      final @NotNull DeviceInfoUtil deviceInfoUtil = DeviceInfoUtil.getInstance(context, options);
      final @NotNull Device deviceInfo = deviceInfoUtil.collectDeviceInformation(true, true);
      scope.getContexts().setDevice(deviceInfo);
      scope.getContexts().setOperatingSystem(deviceInfoUtil.getOperatingSystem());

      // user
      @Nullable User user = scope.getUser();
      if (user == null) {
        user = new User();
        scope.setUser(user);
      }
      if (user.getId() == null) {
        try {
          user.setId(Installation.id(context));
        } catch (RuntimeException e) {
          logger.log(SentryLevel.ERROR, "Could not retrieve installation ID", e);
        }
      }

      // app context
      @Nullable App app = scope.getContexts().getApp();
      if (app == null) {
        app = new App();
      }
      app.setAppName(ContextUtils.getApplicationName(context, options.getLogger()));

      final @NotNull TimeSpan appStartTimeSpan =
          AppStartMetrics.getInstance().getAppStartTimeSpanWithFallback(options);
      if (appStartTimeSpan.hasStarted()) {
        app.setAppStartTime(DateUtils.toUtilDate(appStartTimeSpan.getStartTimestamp()));
      }

      final @NotNull BuildInfoProvider buildInfoProvider =
          new BuildInfoProvider(options.getLogger());
      final @Nullable PackageInfo packageInfo =
          ContextUtils.getPackageInfo(
              context, PackageManager.GET_PERMISSIONS, options.getLogger(), buildInfoProvider);
      if (packageInfo != null) {
        ContextUtils.setAppPackageInfo(packageInfo, buildInfoProvider, app);
      }
      scope.getContexts().setApp(app);

      writer.name("user").value(logger, scope.getUser());
      writer.name("contexts").value(logger, scope.getContexts());
      writer.name("tags").value(logger, scope.getTags());
      writer.name("extras").value(logger, scope.getExtras());
      writer.name("fingerprint").value(logger, scope.getFingerprint());
      writer.name("level").value(logger, scope.getLevel());
      writer.name("breadcrumbs").value(logger, scope.getBreadcrumbs());
    } catch (Throwable e) {
      options.getLogger().log(SentryLevel.ERROR, "Could not serialize scope.", e);
      return new HashMap<>();
    }

    return data;
  }

  /**
   * Captures the provided envelope. Compared to {@link IHub#captureEvent(SentryEvent)} this method
   * <br>
   * - will not enrich events with additional data (e.g. scope)<br>
   * - will not execute beforeSend: it's up to the caller to take care of this<br>
   * - will not perform any sampling: it's up to the caller to take care of this<br>
   * - will enrich the envelope with a Session update if applicable<br>
   *
   * @param envelopeData the serialized envelope data
   * @return The Id (SentryId object) of the event, or null in case the envelope could not be
   *     captured
   */
  @Nullable
  public static SentryId captureEnvelope(final @NotNull byte[] envelopeData) {
    final @NotNull IHub hub = HubAdapter.getInstance();
    final @NotNull SentryOptions options = hub.getOptions();

    try (final InputStream envelopeInputStream = new ByteArrayInputStream(envelopeData)) {
      final @NotNull ISerializer serializer = options.getSerializer();
      final @Nullable SentryEnvelope envelope =
          options.getEnvelopeReader().read(envelopeInputStream);
      if (envelope == null) {
        return null;
      }

      final @NotNull List<SentryEnvelopeItem> envelopeItems = new ArrayList<>();

      // determine session state based on events inside envelope
      @Nullable Session.State status = null;
      boolean crashedOrErrored = false;
      for (SentryEnvelopeItem item : envelope.getItems()) {
        envelopeItems.add(item);

        final SentryEvent event = item.getEvent(serializer);
        if (event != null) {
          if (event.isCrashed()) {
            status = Session.State.Crashed;
          }
          if (event.isCrashed() || event.isErrored()) {
            crashedOrErrored = true;
          }
        }
      }

      // update session and add it to envelope if necessary
      final @Nullable Session session = updateSession(hub, options, status, crashedOrErrored);
      if (session != null) {
        final SentryEnvelopeItem sessionItem = SentryEnvelopeItem.fromSession(serializer, session);
        envelopeItems.add(sessionItem);
      }

      final SentryEnvelope repackagedEnvelope =
          new SentryEnvelope(envelope.getHeader(), envelopeItems);
      return hub.captureEnvelope(repackagedEnvelope);
    } catch (Throwable t) {
      options.getLogger().log(SentryLevel.ERROR, "Failed to capture envelope", t);
    }
    return null;
  }

  public static Map<String, Object> getAppStartMeasurement() {
    final @NotNull AppStartMetrics metrics = AppStartMetrics.getInstance();
    final @NotNull List<Map<String, Object>> spans = new ArrayList<>();

    final @NotNull TimeSpan processInitNativeSpan = new TimeSpan();
    processInitNativeSpan.setStartedAt(metrics.getAppStartTimeSpan().getStartUptimeMs());
    processInitNativeSpan.setStartUnixTimeMs(
        metrics.getAppStartTimeSpan().getStartTimestampMs()); // This has to go after setStartedAt
    processInitNativeSpan.setStoppedAt(metrics.getClassLoadedUptimeMs());
    processInitNativeSpan.setDescription("Process Initialization");

    addTimeSpanToSerializedSpans(processInitNativeSpan, spans);
    addTimeSpanToSerializedSpans(metrics.getApplicationOnCreateTimeSpan(), spans);

    for (final TimeSpan span : metrics.getContentProviderOnCreateTimeSpans()) {
      addTimeSpanToSerializedSpans(span, spans);
    }

    for (final ActivityLifecycleTimeSpan span : metrics.getActivityLifecycleTimeSpans()) {
      addTimeSpanToSerializedSpans(span.getOnCreate(), spans);
      addTimeSpanToSerializedSpans(span.getOnStart(), spans);
    }

    final @NotNull Map<String, Object> result = new HashMap<>();
    result.put("spans", spans);
    result.put("type", metrics.getAppStartType().toString().toLowerCase(Locale.ROOT));
    if (metrics.getAppStartTimeSpan().hasStarted()) {
      result.put("app_start_timestamp_ms", metrics.getAppStartTimeSpan().getStartTimestampMs());
    }

    return result;
  }

  private static void addTimeSpanToSerializedSpans(TimeSpan span, List<Map<String, Object>> spans) {
    if (span.hasNotStarted()) {
      HubAdapter.getInstance()
          .getOptions()
          .getLogger()
          .log(SentryLevel.WARNING, "Can not convert not-started TimeSpan to Map for Hybrid SDKs.");
      return;
    }

    if (span.hasNotStopped()) {
      HubAdapter.getInstance()
          .getOptions()
          .getLogger()
          .log(SentryLevel.WARNING, "Can not convert not-stopped TimeSpan to Map for Hybrid SDKs.");
      return;
    }

    final @NotNull Map<String, Object> spanMap = new HashMap<>();
    spanMap.put("description", span.getDescription());
    spanMap.put("start_timestamp_ms", span.getStartTimestampMs());
    spanMap.put("end_timestamp_ms", span.getProjectedStopTimestampMs());
    spans.add(spanMap);
  }

  @Nullable
  private static Session updateSession(
      final @NotNull IHub hub,
      final @NotNull SentryOptions options,
      final @Nullable Session.State status,
      final boolean crashedOrErrored) {
    final @NotNull AtomicReference<Session> sessionRef = new AtomicReference<>();
    hub.configureScope(
        scope -> {
          final @Nullable Session session = scope.getSession();
          if (session != null) {
            final boolean updated = session.update(status, null, crashedOrErrored, null);
            // if we have an uncaughtExceptionHint we can end the session.
            if (updated) {
              if (session.getStatus() == Session.State.Crashed) {
                session.end();
              }
              sessionRef.set(session);
            }
          } else {
            options.getLogger().log(SentryLevel.INFO, "Session is null on updateSession");
          }
        });
    return sessionRef.get();
  }
}

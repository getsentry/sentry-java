package io.sentry.android.core;

import static io.sentry.cache.PersistingOptionsObserver.DIST_FILENAME;
import static io.sentry.cache.PersistingOptionsObserver.ENVIRONMENT_FILENAME;
import static io.sentry.cache.PersistingOptionsObserver.PROGUARD_UUID_FILENAME;
import static io.sentry.cache.PersistingOptionsObserver.RELEASE_FILENAME;
import static io.sentry.cache.PersistingOptionsObserver.REPLAY_ERROR_SAMPLE_RATE_FILENAME;
import static io.sentry.cache.PersistingOptionsObserver.SDK_VERSION_FILENAME;
import static io.sentry.cache.PersistingScopeObserver.BREADCRUMBS_FILENAME;
import static io.sentry.cache.PersistingScopeObserver.CONTEXTS_FILENAME;
import static io.sentry.cache.PersistingScopeObserver.EXTRAS_FILENAME;
import static io.sentry.cache.PersistingScopeObserver.FINGERPRINT_FILENAME;
import static io.sentry.cache.PersistingScopeObserver.LEVEL_FILENAME;
import static io.sentry.cache.PersistingScopeObserver.REPLAY_FILENAME;
import static io.sentry.cache.PersistingScopeObserver.REQUEST_FILENAME;
import static io.sentry.cache.PersistingScopeObserver.TRACE_FILENAME;
import static io.sentry.cache.PersistingScopeObserver.TRANSACTION_FILENAME;
import static io.sentry.cache.PersistingScopeObserver.USER_FILENAME;
import static io.sentry.protocol.Contexts.REPLAY_ID;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.util.DisplayMetrics;
import androidx.annotation.WorkerThread;
import io.sentry.BackfillingEventProcessor;
import io.sentry.Breadcrumb;
import io.sentry.Hint;
import io.sentry.IpAddressUtils;
import io.sentry.SentryBaseEvent;
import io.sentry.SentryEvent;
import io.sentry.SentryExceptionFactory;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.SentryStackTraceFactory;
import io.sentry.SpanContext;
import io.sentry.android.core.internal.util.CpuInfoUtils;
import io.sentry.cache.PersistingOptionsObserver;
import io.sentry.cache.PersistingScopeObserver;
import io.sentry.hints.AbnormalExit;
import io.sentry.hints.Backfillable;
import io.sentry.protocol.App;
import io.sentry.protocol.Contexts;
import io.sentry.protocol.DebugImage;
import io.sentry.protocol.DebugMeta;
import io.sentry.protocol.Device;
import io.sentry.protocol.Mechanism;
import io.sentry.protocol.OperatingSystem;
import io.sentry.protocol.Request;
import io.sentry.protocol.SdkVersion;
import io.sentry.protocol.SentryStackTrace;
import io.sentry.protocol.SentryThread;
import io.sentry.protocol.SentryTransaction;
import io.sentry.protocol.User;
import io.sentry.util.HintUtils;
import io.sentry.util.SentryRandom;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Processes cached ApplicationExitInfo events (ANRs, tombstones) on a background thread, so we can
 * safely read data from disk synchronously.
 */
@ApiStatus.Internal
@WorkerThread
public final class ApplicationExitInfoEventProcessor implements BackfillingEventProcessor {

  private final @NotNull Context context;

  private final @NotNull SentryAndroidOptions options;

  private final @NotNull BuildInfoProvider buildInfoProvider;

  private final @NotNull SentryExceptionFactory sentryExceptionFactory;

  private final @Nullable PersistingScopeObserver persistingScopeObserver;

  // Only ANRv2 events are currently enriched with hint-specific content.
  // This can be extended to other hints like NativeCrashExit.
  private final @NotNull List<HintEnricher> hintEnrichers =
      Collections.singletonList(new AnrHintEnricher());

  public ApplicationExitInfoEventProcessor(
      final @NotNull Context context,
      final @NotNull SentryAndroidOptions options,
      final @NotNull BuildInfoProvider buildInfoProvider) {
    this.context = ContextUtils.getApplicationContext(context);
    this.options = options;
    this.buildInfoProvider = buildInfoProvider;
    this.persistingScopeObserver = options.findPersistingScopeObserver();

    final SentryStackTraceFactory sentryStackTraceFactory =
        new SentryStackTraceFactory(this.options);

    sentryExceptionFactory = new SentryExceptionFactory(sentryStackTraceFactory);
  }

  private @Nullable HintEnricher findEnricher(final @NotNull Object hint) {
    for (HintEnricher enricher : hintEnrichers) {
      if (enricher.supports(hint)) {
        return enricher;
      }
    }
    return null;
  }

  @Override
  public @NotNull SentryTransaction process(
      @NotNull SentryTransaction transaction, @NotNull Hint hint) {
    // that's only necessary because on newer versions of Unity, if not overriding this method, it's
    // throwing 'java.lang.AbstractMethodError: abstract method' and the reason is probably
    // compilation mismatch
    return transaction;
  }

  @Override
  public @Nullable SentryEvent process(@NotNull SentryEvent event, @NotNull Hint hint) {
    final Object unwrappedHint = HintUtils.getSentrySdkHint(hint);
    if (!(unwrappedHint instanceof Backfillable)) {
      options
          .getLogger()
          .log(
              SentryLevel.WARNING,
              "The event is not Backfillable, but has been passed to BackfillingEventProcessor, skipping.");
      return event;
    }
    final @NotNull Backfillable backfillable = (Backfillable) unwrappedHint;
    final @Nullable HintEnricher hintEnricher = findEnricher(unwrappedHint);

    if (hintEnricher != null) {
      hintEnricher.applyPreEnrichment(event, backfillable, unwrappedHint);
    }

    // We always set  os and device even if the ApplicationExitInfo event is not enrich-able.
    // The OS context may change in the meantime (OS update); we consider this an edge-case.
    mergeOS(event);
    setDevice(event);

    if (!backfillable.shouldEnrich()) {
      options
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "The event is Backfillable, but should not be enriched, skipping.");
      return event;
    }

    backfillScope(event);

    backfillOptions(event);

    setStaticValues(event);

    if (hintEnricher != null) {
      hintEnricher.applyPostEnrichment(event, backfillable, unwrappedHint);
    }

    return event;
  }

  // region scope persisted values
  private void backfillScope(final @NotNull SentryEvent event) {
    setRequest(event);
    setUser(event);
    setScopeTags(event);
    setBreadcrumbs(event);
    setExtras(event);
    setContexts(event);
    setTransaction(event);
    setFingerprints(event);
    setLevel(event);
    setTrace(event);
    setReplayId(event);
  }

  private boolean sampleReplay(final @NotNull SentryEvent event) {
    final @Nullable String replayErrorSampleRate =
        PersistingOptionsObserver.read(options, REPLAY_ERROR_SAMPLE_RATE_FILENAME, String.class);

    if (replayErrorSampleRate == null) {
      return false;
    }

    try {
      // we have to sample here with the old sample rate, because it may change between app launches
      final double replayErrorSampleRateDouble = Double.parseDouble(replayErrorSampleRate);
      if (replayErrorSampleRateDouble < SentryRandom.current().nextDouble()) {
        options
            .getLogger()
            .log(
                SentryLevel.DEBUG,
                "Not capturing replay for ANR %s due to not being sampled.",
                event.getEventId());
        return false;
      }
    } catch (Throwable e) {
      options.getLogger().log(SentryLevel.ERROR, "Error parsing replay sample rate.", e);
      return false;
    }

    return true;
  }

  private void setReplayId(final @NotNull SentryEvent event) {
    @Nullable String persistedReplayId = readFromDisk(options, REPLAY_FILENAME, String.class);
    @Nullable String cacheDirPath = options.getCacheDirPath();
    if (cacheDirPath == null) {
      return;
    }
    final @NotNull File replayFolder = new File(cacheDirPath, "replay_" + persistedReplayId);
    if (!replayFolder.exists()) {
      if (!sampleReplay(event)) {
        return;
      }
      // if the replay folder does not exist (e.g. running in buffer mode), we need to find the
      // latest replay folder that was modified before the ANR event.
      persistedReplayId = null;
      long lastModified = Long.MIN_VALUE;
      final File[] dirs = new File(cacheDirPath).listFiles();
      if (dirs != null) {
        for (File dir : dirs) {
          if (dir.isDirectory() && dir.getName().startsWith("replay_")) {
            if (dir.lastModified() > lastModified
                && dir.lastModified() <= event.getTimestamp().getTime()) {
              lastModified = dir.lastModified();
              persistedReplayId = dir.getName().substring("replay_".length());
            }
          }
        }
      }
    }

    if (persistedReplayId == null) {
      return;
    }

    // store the relevant replayId so ReplayIntegration can pick it up and finalize that replay
    PersistingScopeObserver.store(options, persistedReplayId, REPLAY_FILENAME);
    event.getContexts().put(REPLAY_ID, persistedReplayId);
  }

  private void setTrace(final @NotNull SentryEvent event) {
    final SpanContext spanContext = readFromDisk(options, TRACE_FILENAME, SpanContext.class);
    if (event.getContexts().getTrace() == null) {
      if (spanContext != null) {
        event.getContexts().setTrace(spanContext);
      }
    }
  }

  private void setLevel(final @NotNull SentryEvent event) {
    final SentryLevel level = readFromDisk(options, LEVEL_FILENAME, SentryLevel.class);
    if (event.getLevel() == null) {
      event.setLevel(level);
    }
  }

  @SuppressWarnings("unchecked")
  private void setFingerprints(final @NotNull SentryEvent event) {
    final List<String> fingerprint =
        (List<String>) readFromDisk(options, FINGERPRINT_FILENAME, List.class);
    if (event.getFingerprints() == null) {
      event.setFingerprints(fingerprint);
    }
  }

  private void setTransaction(final @NotNull SentryEvent event) {
    final String transaction = readFromDisk(options, TRANSACTION_FILENAME, String.class);
    if (event.getTransaction() == null) {
      event.setTransaction(transaction);
    }
  }

  private void setContexts(final @NotNull SentryBaseEvent event) {
    final Contexts persistedContexts = readFromDisk(options, CONTEXTS_FILENAME, Contexts.class);
    if (persistedContexts == null) {
      return;
    }
    final Contexts eventContexts = event.getContexts();
    for (Map.Entry<String, Object> entry : new Contexts(persistedContexts).entrySet()) {
      final Object value = entry.getValue();
      if (SpanContext.TYPE.equals(entry.getKey()) && value instanceof SpanContext) {
        // we fill it in setTrace later on
        continue;
      }
      if (!eventContexts.containsKey(entry.getKey())) {
        eventContexts.put(entry.getKey(), value);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private void setExtras(final @NotNull SentryBaseEvent event) {
    final Map<String, Object> extras =
        (Map<String, Object>) readFromDisk(options, EXTRAS_FILENAME, Map.class);
    if (extras == null) {
      return;
    }
    if (event.getExtras() == null) {
      event.setExtras(new HashMap<>(extras));
    } else {
      for (Map.Entry<String, Object> item : extras.entrySet()) {
        if (!event.getExtras().containsKey(item.getKey())) {
          event.getExtras().put(item.getKey(), item.getValue());
        }
      }
    }
  }

  @SuppressWarnings("unchecked")
  private void setBreadcrumbs(final @NotNull SentryBaseEvent event) {
    final List<Breadcrumb> breadcrumbs =
        (List<Breadcrumb>) readFromDisk(options, BREADCRUMBS_FILENAME, List.class);
    if (breadcrumbs == null) {
      return;
    }
    if (event.getBreadcrumbs() == null) {
      event.setBreadcrumbs(breadcrumbs);
    } else {
      event.getBreadcrumbs().addAll(breadcrumbs);
    }
  }

  @SuppressWarnings("unchecked")
  private void setScopeTags(final @NotNull SentryBaseEvent event) {
    final Map<String, String> tags =
        (Map<String, String>)
            readFromDisk(options, PersistingScopeObserver.TAGS_FILENAME, Map.class);
    if (tags == null) {
      return;
    }
    if (event.getTags() == null) {
      event.setTags(new HashMap<>(tags));
    } else {
      for (Map.Entry<String, String> item : tags.entrySet()) {
        if (!event.getTags().containsKey(item.getKey())) {
          event.setTag(item.getKey(), item.getValue());
        }
      }
    }
  }

  private void setUser(final @NotNull SentryBaseEvent event) {
    if (event.getUser() == null) {
      final User user = readFromDisk(options, USER_FILENAME, User.class);
      event.setUser(user);
    }
  }

  private void setRequest(final @NotNull SentryBaseEvent event) {
    if (event.getRequest() == null) {
      final Request request = readFromDisk(options, REQUEST_FILENAME, Request.class);
      event.setRequest(request);
    }
  }

  private <T> @Nullable T readFromDisk(
      final @NotNull SentryOptions options,
      final @NotNull String fileName,
      final @NotNull Class<T> clazz) {
    if (persistingScopeObserver == null) {
      return null;
    }

    return persistingScopeObserver.read(options, fileName, clazz);
  }

  // endregion

  // region options persisted values
  private void backfillOptions(final @NotNull SentryEvent event) {
    setRelease(event);
    setEnvironment(event);
    setDist(event);
    setDebugMeta(event);
    setSdk(event);
    setApp(event);
    setOptionsTags(event);
  }

  private void setApp(final @NotNull SentryBaseEvent event) {
    App app = event.getContexts().getApp();
    if (app == null) {
      app = new App();
    }
    app.setAppName(ContextUtils.getApplicationName(context));

    final PackageInfo packageInfo = ContextUtils.getPackageInfo(context, buildInfoProvider);
    if (packageInfo != null) {
      app.setAppIdentifier(packageInfo.packageName);
    }

    // backfill versionName and versionCode from the persisted release string
    final String release =
        event.getRelease() != null
            ? event.getRelease()
            : PersistingOptionsObserver.read(options, RELEASE_FILENAME, String.class);
    if (release != null) {
      try {
        final String versionName =
            release.substring(release.indexOf('@') + 1, release.indexOf('+'));
        final String versionCode = release.substring(release.indexOf('+') + 1);
        app.setAppVersion(versionName);
        app.setAppBuild(versionCode);
      } catch (Throwable e) {
        options
            .getLogger()
            .log(SentryLevel.WARNING, "Failed to parse release from scope cache: %s", release);
      }
    }

    try {
      final ContextUtils.SplitApksInfo splitApksInfo =
          DeviceInfoUtil.getInstance(context, options).getSplitApksInfo();
      if (splitApksInfo != null) {
        app.setSplitApks(splitApksInfo.isSplitApks());
        if (splitApksInfo.getSplitNames() != null) {
          app.setSplitNames(Arrays.asList(splitApksInfo.getSplitNames()));
        }
      }
    } catch (Throwable e) {
      options.getLogger().log(SentryLevel.ERROR, "Error getting split apks info.", e);
    }

    event.getContexts().setApp(app);
  }

  private void setRelease(final @NotNull SentryBaseEvent event) {
    if (event.getRelease() == null) {
      final String release =
          PersistingOptionsObserver.read(options, RELEASE_FILENAME, String.class);
      event.setRelease(release);
    }
  }

  private void setEnvironment(final @NotNull SentryBaseEvent event) {
    if (event.getEnvironment() == null) {
      final String environment =
          PersistingOptionsObserver.read(options, ENVIRONMENT_FILENAME, String.class);
      event.setEnvironment(environment != null ? environment : options.getEnvironment());
    }
  }

  private void setDebugMeta(final @NotNull SentryBaseEvent event) {
    DebugMeta debugMeta = event.getDebugMeta();

    if (debugMeta == null) {
      debugMeta = new DebugMeta();
    }
    if (debugMeta.getImages() == null) {
      debugMeta.setImages(new ArrayList<>());
    }
    List<DebugImage> images = debugMeta.getImages();
    if (images != null) {
      final String proguardUuid =
          PersistingOptionsObserver.read(options, PROGUARD_UUID_FILENAME, String.class);

      if (proguardUuid != null) {
        final DebugImage debugImage = new DebugImage();
        debugImage.setType(DebugImage.PROGUARD);
        debugImage.setUuid(proguardUuid);
        images.add(debugImage);
      }
      event.setDebugMeta(debugMeta);
    }
  }

  private void setDist(final @NotNull SentryBaseEvent event) {
    if (event.getDist() == null) {
      final String dist = PersistingOptionsObserver.read(options, DIST_FILENAME, String.class);
      event.setDist(dist);
    }
    // if there's no user-set dist, fall back to versionCode from the persisted release string
    if (event.getDist() == null) {
      final String release =
          PersistingOptionsObserver.read(options, RELEASE_FILENAME, String.class);
      if (release != null) {
        try {
          final String versionCode = release.substring(release.indexOf('+') + 1);
          event.setDist(versionCode);
        } catch (Throwable e) {
          options
              .getLogger()
              .log(SentryLevel.WARNING, "Failed to parse release from scope cache: %s", release);
        }
      }
    }
  }

  private void setSdk(final @NotNull SentryBaseEvent event) {
    if (event.getSdk() == null) {
      final SdkVersion sdkVersion =
          PersistingOptionsObserver.read(options, SDK_VERSION_FILENAME, SdkVersion.class);
      event.setSdk(sdkVersion);
    }
  }

  @SuppressWarnings("unchecked")
  private void setOptionsTags(final @NotNull SentryBaseEvent event) {
    final Map<String, String> tags =
        (Map<String, String>)
            PersistingOptionsObserver.read(
                options, PersistingOptionsObserver.TAGS_FILENAME, Map.class);
    if (tags == null) {
      return;
    }
    if (event.getTags() == null) {
      event.setTags(new HashMap<>(tags));
    } else {
      for (Map.Entry<String, String> item : tags.entrySet()) {
        if (!event.getTags().containsKey(item.getKey())) {
          event.setTag(item.getKey(), item.getValue());
        }
      }
    }
  }

  // endregion

  @Override
  public @Nullable Long getOrder() {
    return 12000L;
  }

  // region static values
  private void setStaticValues(final @NotNull SentryEvent event) {
    mergeUser(event);
    setSideLoadedInfo(event);
  }

  private void setDefaultPlatform(final @NotNull SentryBaseEvent event) {
    if (event.getPlatform() == null) {
      // this actually means JVM related.
      event.setPlatform(SentryBaseEvent.DEFAULT_PLATFORM);
    }
  }

  // by default we assume that the ANR is foreground, unless abnormalMechanism is "anr_background"
  private boolean isBackgroundAnr(final @NotNull Object hint) {
    if (hint instanceof AbnormalExit) {
      final String abnormalMechanism = ((AbnormalExit) hint).mechanism();
      return "anr_background".equals(abnormalMechanism);
    }
    return false;
  }

  private void mergeUser(final @NotNull SentryBaseEvent event) {
    @Nullable User user = event.getUser();
    if (user == null) {
      user = new User();
      event.setUser(user);
    }

    // userId should be set even if event is Cached as the userId is static and won't change anyway.
    if (user.getId() == null) {
      user.setId(getDeviceId());
    }
    if (user.getIpAddress() == null && options.isSendDefaultPii()) {
      user.setIpAddress(IpAddressUtils.DEFAULT_IP_ADDRESS);
    }
  }

  private @Nullable String getDeviceId() {
    try {
      return options.getRuntimeManager().runWithRelaxedPolicy(() -> Installation.id(context));
    } catch (Throwable e) {
      options.getLogger().log(SentryLevel.ERROR, "Error getting installationId.", e);
    }
    return null;
  }

  private void setSideLoadedInfo(final @NotNull SentryBaseEvent event) {
    try {
      final ContextUtils.SideLoadedInfo sideLoadedInfo =
          DeviceInfoUtil.getInstance(context, options).getSideLoadedInfo();
      if (sideLoadedInfo != null) {
        final @NotNull Map<String, String> tags = sideLoadedInfo.asTags();
        for (Map.Entry<String, String> entry : tags.entrySet()) {
          event.setTag(entry.getKey(), entry.getValue());
        }
      }
    } catch (Throwable e) {
      options.getLogger().log(SentryLevel.ERROR, "Error getting side loaded info.", e);
    }
  }

  private void setDevice(final @NotNull SentryBaseEvent event) {
    if (event.getContexts().getDevice() == null) {
      event.getContexts().setDevice(getDevice());
    }
  }

  // only use static data that does not change between app launches (e.g. timezone, boottime,
  // battery level will change)
  @SuppressLint("NewApi")
  private @NotNull Device getDevice() {
    Device device = new Device();
    device.setManufacturer(Build.MANUFACTURER);
    device.setBrand(Build.BRAND);
    device.setFamily(ContextUtils.getFamily(options.getLogger()));
    device.setModel(Build.MODEL);
    device.setModelId(Build.ID);
    device.setArchs(ContextUtils.getArchitectures());

    final ActivityManager.MemoryInfo memInfo =
        ContextUtils.getMemInfo(context, options.getLogger());
    if (memInfo != null) {
      // in bytes
      device.setMemorySize(getMemorySize(memInfo));
    }

    device.setSimulator(buildInfoProvider.isEmulator());

    DisplayMetrics displayMetrics = ContextUtils.getDisplayMetrics(context, options.getLogger());
    if (displayMetrics != null) {
      device.setScreenWidthPixels(displayMetrics.widthPixels);
      device.setScreenHeightPixels(displayMetrics.heightPixels);
      device.setScreenDensity(displayMetrics.density);
      device.setScreenDpi(displayMetrics.densityDpi);
    }

    if (device.getId() == null) {
      device.setId(getDeviceId());
    }

    final @NotNull List<Integer> cpuFrequencies = CpuInfoUtils.getInstance().readMaxFrequencies();
    if (!cpuFrequencies.isEmpty()) {
      device.setProcessorFrequency(Collections.max(cpuFrequencies).doubleValue());
      device.setProcessorCount(cpuFrequencies.size());
    }

    return device;
  }

  private @NotNull Long getMemorySize(final @NotNull ActivityManager.MemoryInfo memInfo) {
    return memInfo.totalMem;
  }

  private void mergeOS(final @NotNull SentryBaseEvent event) {
    final OperatingSystem currentOS = event.getContexts().getOperatingSystem();
    final OperatingSystem androidOS =
        DeviceInfoUtil.getInstance(context, options).getOperatingSystem();

    // make Android OS the main OS using the 'os' key
    event.getContexts().setOperatingSystem(androidOS);

    if (currentOS != null) {
      // add additional OS which was already part of the SentryEvent (eg Linux read from NDK)
      String osNameKey = currentOS.getName();
      if (osNameKey != null && !osNameKey.isEmpty()) {
        osNameKey = "os_" + osNameKey.trim().toLowerCase(Locale.ROOT);
      } else {
        osNameKey = "os_1";
      }
      event.getContexts().put(osNameKey, currentOS);
    }
    // endregion
  }

  private interface HintEnricher {
    boolean supports(@NotNull Object hint);

    void applyPreEnrichment(
        @NotNull SentryEvent event, @NotNull Backfillable hint, @NotNull Object rawHint);

    void applyPostEnrichment(
        @NotNull SentryEvent event, @NotNull Backfillable hint, @NotNull Object rawHint);
  }

  private final class AnrHintEnricher implements HintEnricher {

    @Override
    public boolean supports(@NotNull Object hint) {
      // While this is specifically an ANR enricher we discriminate enrichment application
      // on the broader AbnormalExit hints for now.
      return hint instanceof AbnormalExit;
    }

    @Override
    public void applyPreEnrichment(
        @NotNull SentryEvent event, @NotNull Backfillable hint, @NotNull Object rawHint) {
      final boolean isBackgroundAnr = isBackgroundAnr(rawHint);
      // we always set exception values and default platform even if the ANR is not enrich-able
      setDefaultPlatform(event);
      setAnrExceptions(event, hint, isBackgroundAnr);
    }

    @Override
    public void applyPostEnrichment(
        @NotNull SentryEvent event, @NotNull Backfillable hint, @NotNull Object rawHint) {
      final boolean isBackgroundAnr = isBackgroundAnr(rawHint);
      setAppForeground(event, !isBackgroundAnr);
      setDefaultAnrFingerprint(event, isBackgroundAnr);
    }

    private void setDefaultAnrFingerprint(
        final @NotNull SentryEvent event, final boolean isBackgroundAnr) {
      // sentry does not yet have a capability to provide default server-side fingerprint rules,
      // so we're doing this on the SDK side to group background and foreground ANRs separately
      // even if they have similar stacktraces.
      if (event.getFingerprints() == null) {
        event.setFingerprints(
            Arrays.asList("{{ default }}", isBackgroundAnr ? "background-anr" : "foreground-anr"));
      }
    }

    private void setAppForeground(
        final @NotNull SentryBaseEvent event, final boolean inForeground) {
      App app = event.getContexts().getApp();
      if (app == null) {
        app = new App();
        event.getContexts().setApp(app);
      }
      // TODO: not entirely correct, because we define background ANRs as not the ones of
      //  IMPORTANCE_FOREGROUND, but this doesn't mean the app was in foreground when an ANR
      //  happened but it's our best effort for now. We could serialize AppState in theory.
      if (app.getInForeground() == null) {
        app.setInForeground(inForeground);
      }
    }

    @Nullable
    private SentryThread findMainThread(final @Nullable List<SentryThread> threads) {
      if (threads != null) {
        for (SentryThread thread : threads) {
          final String name = thread.getName();
          if (name != null && name.equals("main")) {
            return thread;
          }
        }
      }
      return null;
    }

    private void setAnrExceptions(
        final @NotNull SentryEvent event,
        final @NotNull Backfillable hint,
        final boolean isBackgroundAnr) {
      if (event.getExceptions() != null) {
        return;
      }
      // AnrV2 threads contain a thread dump from the OS, so we just search for the main thread dump
      // and make an exception out of its stacktrace
      final Mechanism mechanism = new Mechanism();
      if (!hint.shouldEnrich()) {
        // we only enrich the latest ANR in the list, so this is historical
        mechanism.setType("HistoricalAppExitInfo");
      } else {
        mechanism.setType("AppExitInfo");
      }

      String message = "ANR";
      if (isBackgroundAnr) {
        message = "Background " + message;
      }
      final ApplicationNotResponding anr =
          new ApplicationNotResponding(message, Thread.currentThread());

      SentryThread mainThread = findMainThread(event.getThreads());
      if (mainThread == null) {
        // if there's no main thread in the event threads, we just create a dummy thread so the
        // exception is properly created as well, but without stacktrace
        mainThread = new SentryThread();
        mainThread.setStacktrace(new SentryStackTrace());
      }
      event.setExceptions(
          sentryExceptionFactory.getSentryExceptionsFromThread(mainThread, mechanism, anr));
    }
  }
}

package io.sentry;

import io.sentry.hints.AbnormalExit;
import io.sentry.hints.Cached;
import io.sentry.protocol.DebugImage;
import io.sentry.protocol.DebugMeta;
import io.sentry.protocol.SdkVersion;
import io.sentry.protocol.SentryException;
import io.sentry.protocol.SentryTransaction;
import io.sentry.protocol.User;
import io.sentry.util.HintUtils;
import io.sentry.util.Objects;
import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

@ApiStatus.Internal
public final class MainEventProcessor implements EventProcessor, Closeable {

  private final @NotNull SentryOptions options;
  private final @NotNull SentryThreadFactory sentryThreadFactory;
  private final @NotNull SentryExceptionFactory sentryExceptionFactory;
  private volatile @Nullable HostnameCache hostnameCache = null;

  public MainEventProcessor(final @NotNull SentryOptions options) {
    this.options = Objects.requireNonNull(options, "The SentryOptions is required.");

    final SentryStackTraceFactory sentryStackTraceFactory =
        new SentryStackTraceFactory(this.options);

    sentryExceptionFactory = new SentryExceptionFactory(sentryStackTraceFactory);
    sentryThreadFactory = new SentryThreadFactory(sentryStackTraceFactory, this.options);
  }

  MainEventProcessor(
      final @NotNull SentryOptions options,
      final @NotNull SentryThreadFactory sentryThreadFactory,
      final @NotNull SentryExceptionFactory sentryExceptionFactory) {
    this.options = Objects.requireNonNull(options, "The SentryOptions is required.");
    this.sentryThreadFactory =
        Objects.requireNonNull(sentryThreadFactory, "The SentryThreadFactory is required.");
    this.sentryExceptionFactory =
        Objects.requireNonNull(sentryExceptionFactory, "The SentryExceptionFactory is required.");
  }

  @Override
  public @NotNull SentryEvent process(final @NotNull SentryEvent event, final @NotNull Hint hint) {
    setCommons(event);
    setExceptions(event);
    setDebugMeta(event);
    setModules(event);

    if (shouldApplyScopeData(event, hint)) {
      processNonCachedEvent(event);
      setThreads(event, hint);
    }

    return event;
  }

  private void setDebugMeta(final @NotNull SentryBaseEvent event) {
    final @NotNull List<DebugImage> debugImages = new ArrayList<>();

    if (options.getProguardUuid() != null) {
      final DebugImage proguardMappingImage = new DebugImage();
      proguardMappingImage.setType(DebugImage.PROGUARD);
      proguardMappingImage.setUuid(options.getProguardUuid());
      debugImages.add(proguardMappingImage);
    }

    for (final @NotNull String bundleId : options.getBundleIds()) {
      final DebugImage sourceBundleImage = new DebugImage();
      sourceBundleImage.setType(DebugImage.JVM);
      sourceBundleImage.setDebugId(bundleId);
      debugImages.add(sourceBundleImage);
    }

    if (!debugImages.isEmpty()) {
      DebugMeta debugMeta = event.getDebugMeta();

      if (debugMeta == null) {
        debugMeta = new DebugMeta();
      }
      if (debugMeta.getImages() == null) {
        debugMeta.setImages(debugImages);
      } else {
        debugMeta.getImages().addAll(debugImages);
      }

      event.setDebugMeta(debugMeta);
    }
  }

  private void setModules(final @NotNull SentryEvent event) {
    final Map<String, String> modules = options.getModulesLoader().getOrLoadModules();
    if (modules == null) {
      return;
    }

    final Map<String, String> eventModules = event.getModules();
    if (eventModules == null) {
      event.setModules(modules);
    } else {
      eventModules.putAll(modules);
    }
  }

  private boolean shouldApplyScopeData(
      final @NotNull SentryBaseEvent event, final @NotNull Hint hint) {
    if (HintUtils.shouldApplyScopeData(hint)) {
      return true;
    } else {
      options
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "Event was cached so not applying data relevant to the current app execution/version: %s",
              event.getEventId());
      return false;
    }
  }

  private void processNonCachedEvent(final @NotNull SentryBaseEvent event) {
    setRelease(event);
    setEnvironment(event);
    setServerName(event);
    setDist(event);
    setSdk(event);
    setTags(event);
    mergeUser(event);
  }

  @Override
  public @NotNull SentryTransaction process(
      final @NotNull SentryTransaction transaction, final @NotNull Hint hint) {
    setCommons(transaction);
    setDebugMeta(transaction);

    if (shouldApplyScopeData(transaction, hint)) {
      processNonCachedEvent(transaction);
    }

    return transaction;
  }

  @Override
  public @NotNull SentryReplayEvent process(
      final @NotNull SentryReplayEvent event, final @NotNull Hint hint) {
    setCommons(event);
    // TODO: maybe later it's needed to deobfuscate something (e.g. view hierarchy), for now the
    // TODO: protocol does not support it
    // setDebugMeta(event);

    if (shouldApplyScopeData(event, hint)) {
      processNonCachedEvent(event);
      final @Nullable SdkVersion replaySdkVersion = options.getSessionReplay().getSdkVersion();
      if (replaySdkVersion != null) {
        // we override the SdkVersion only for replay events as those may come from Hybrid SDKs
        event.setSdk(replaySdkVersion);
      }
    }
    return event;
  }

  private void setCommons(final @NotNull SentryBaseEvent event) {
    setPlatform(event);
  }

  private void setPlatform(final @NotNull SentryBaseEvent event) {
    if (event.getPlatform() == null) {
      // this actually means JVM related.
      event.setPlatform(SentryBaseEvent.DEFAULT_PLATFORM);
    }
  }

  private void setRelease(final @NotNull SentryBaseEvent event) {
    if (event.getRelease() == null) {
      event.setRelease(options.getRelease());
    }
  }

  private void setEnvironment(final @NotNull SentryBaseEvent event) {
    if (event.getEnvironment() == null) {
      event.setEnvironment(options.getEnvironment());
    }
  }

  private void setServerName(final @NotNull SentryBaseEvent event) {
    if (event.getServerName() == null) {
      event.setServerName(options.getServerName());
    }

    if (options.isAttachServerName() && event.getServerName() == null) {
      ensureHostnameCache();
      if (hostnameCache != null) {
        event.setServerName(hostnameCache.getHostname());
      }
    }
  }

  private void ensureHostnameCache() {
    if (hostnameCache == null) {
      synchronized (this) {
        if (hostnameCache == null) {
          hostnameCache = HostnameCache.getInstance();
        }
      }
    }
  }

  private void setDist(final @NotNull SentryBaseEvent event) {
    if (event.getDist() == null) {
      event.setDist(options.getDist());
    }
  }

  private void setSdk(final @NotNull SentryBaseEvent event) {
    if (event.getSdk() == null) {
      event.setSdk(options.getSdkVersion());
    }
  }

  private void setTags(final @NotNull SentryBaseEvent event) {
    if (event.getTags() == null) {
      event.setTags(new HashMap<>(options.getTags()));
    } else {
      for (Map.Entry<String, String> item : options.getTags().entrySet()) {
        if (!event.getTags().containsKey(item.getKey())) {
          event.setTag(item.getKey(), item.getValue());
        }
      }
    }
  }

  private void mergeUser(final @NotNull SentryBaseEvent event) {
    @Nullable User user = event.getUser();
    if (user == null) {
      user = new User();
      event.setUser(user);
    }
    if (user.getIpAddress() == null) {
      user.setIpAddress(IpAddressUtils.DEFAULT_IP_ADDRESS);
    }
  }

  private void setExceptions(final @NotNull SentryEvent event) {
    final Throwable throwable = event.getThrowableMechanism();
    if (throwable != null) {
      event.setExceptions(sentryExceptionFactory.getSentryExceptions(throwable));
    }
  }

  private void setThreads(final @NotNull SentryEvent event, final @NotNull Hint hint) {
    if (event.getThreads() == null) {
      // collecting threadIds that came from the exception mechanism, so we can mark threads as
      // crashed properly
      List<Long> mechanismThreadIds = null;

      final List<SentryException> eventExceptions = event.getExceptions();

      if (eventExceptions != null && !eventExceptions.isEmpty()) {
        for (final SentryException item : eventExceptions) {
          if (item.getMechanism() != null && item.getThreadId() != null) {
            if (mechanismThreadIds == null) {
              mechanismThreadIds = new ArrayList<>();
            }
            mechanismThreadIds.add(item.getThreadId());
          }
        }
      }

      // typically Abnormal exits can be tackled by looking at the thread dump (e.g. ANRs), hence
      // we force attach threads regardless of the config
      if (options.isAttachThreads() || HintUtils.hasType(hint, AbnormalExit.class)) {
        final Object sentrySdkHint = HintUtils.getSentrySdkHint(hint);
        boolean ignoreCurrentThread = false;
        if (sentrySdkHint instanceof AbnormalExit) {
          ignoreCurrentThread = ((AbnormalExit) sentrySdkHint).ignoreCurrentThread();
        }
        event.setThreads(
            sentryThreadFactory.getCurrentThreads(mechanismThreadIds, ignoreCurrentThread));
      } else if (options.isAttachStacktrace()
          && (eventExceptions == null || eventExceptions.isEmpty())
          && !isCachedHint(hint)) {
        // when attachStacktrace is enabled, we attach only the current thread and its stack traces,
        // if there are no exceptions, exceptions have its own stack traces.
        event.setThreads(sentryThreadFactory.getCurrentThread());
      }
    }
  }

  /**
   * If the event has a Cached Hint, it means that it came from the EnvelopeFileObserver. We don't
   * want to append the current thread to the event.
   *
   * @param hint the Hints
   * @return true if Cached or false otherwise
   */
  private boolean isCachedHint(final @NotNull Hint hint) {
    return HintUtils.hasType(hint, Cached.class);
  }

  @Override
  public void close() throws IOException {
    if (hostnameCache != null) {
      hostnameCache.close();
    }
  }

  boolean isClosed() {
    if (hostnameCache != null) {
      return hostnameCache.isClosed();
    } else {
      return true;
    }
  }

  @VisibleForTesting
  @Nullable
  HostnameCache getHostnameCache() {
    return hostnameCache;
  }
}

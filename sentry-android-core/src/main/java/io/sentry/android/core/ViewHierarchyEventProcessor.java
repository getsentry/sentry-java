package io.sentry.android.core;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import io.sentry.Attachment;
import io.sentry.EventProcessor;
import io.sentry.Hint;
import io.sentry.ILogger;
import io.sentry.ISerializer;
import io.sentry.IntegrationName;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.android.core.internal.gestures.ViewUtils;
import io.sentry.android.core.internal.util.AndroidCurrentDateProvider;
import io.sentry.android.core.internal.util.AndroidMainThreadChecker;
import io.sentry.android.core.internal.util.Debouncer;
import io.sentry.internal.viewhierarchy.ViewHierarchyExporter;
import io.sentry.protocol.ViewHierarchy;
import io.sentry.protocol.ViewHierarchyNode;
import io.sentry.util.HintUtils;
import io.sentry.util.JsonSerializationUtils;
import io.sentry.util.Objects;
import io.sentry.util.thread.IMainThreadChecker;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** ViewHierarchyEventProcessor responsible for taking a snapshot of the current view hierarchy. */
@ApiStatus.Internal
public final class ViewHierarchyEventProcessor implements EventProcessor, IntegrationName {

  private final @NotNull SentryAndroidOptions options;
  private final @NotNull Debouncer debouncer;

  private static final long CAPTURE_TIMEOUT_MS = 1000;
  private static final long DEBOUNCE_WAIT_TIME_MS = 2000;

  public ViewHierarchyEventProcessor(final @NotNull SentryAndroidOptions options) {
    this.options = Objects.requireNonNull(options, "SentryAndroidOptions is required");
    this.debouncer = new Debouncer(AndroidCurrentDateProvider.getInstance(), DEBOUNCE_WAIT_TIME_MS);

    if (options.isAttachViewHierarchy()) {
      addIntegrationToSdkVersion();
    }
  }

  @Override
  public @NotNull SentryEvent process(final @NotNull SentryEvent event, final @NotNull Hint hint) {
    if (!event.isErrored()) {
      return event;
    }

    if (!options.isAttachViewHierarchy()) {
      options.getLogger().log(SentryLevel.DEBUG, "attachViewHierarchy is disabled.");
      return event;
    }

    if (HintUtils.isFromHybridSdk(hint)) {
      return event;
    }

    // skip capturing in case of debouncing (=too many frequent capture requests)
    // the BeforeCaptureCallback may overrules the debouncing decision
    final boolean shouldDebounce = debouncer.checkForDebounce();
    final @Nullable SentryAndroidOptions.BeforeCaptureCallback beforeCaptureCallback =
        options.getBeforeViewHierarchyCaptureCallback();
    if (beforeCaptureCallback != null) {
      if (!beforeCaptureCallback.execute(event, hint, shouldDebounce)) {
        return event;
      }
    } else if (shouldDebounce) {
      return event;
    }

    final @Nullable Activity activity = CurrentActivityHolder.getInstance().getActivity();
    final @Nullable ViewHierarchy viewHierarchy =
        snapshotViewHierarchy(
            activity,
            options.getViewHierarchyExporters(),
            options.getMainThreadChecker(),
            options.getLogger());

    if (viewHierarchy != null) {
      hint.setViewHierarchy(Attachment.fromViewHierarchy(viewHierarchy));
    }

    return event;
  }

  public static byte[] snapshotViewHierarchyAsData(
      @Nullable Activity activity,
      @NotNull IMainThreadChecker mainThreadChecker,
      @NotNull ISerializer serializer,
      @NotNull ILogger logger) {

    @Nullable
    ViewHierarchy viewHierarchy =
        snapshotViewHierarchy(activity, new ArrayList<>(0), mainThreadChecker, logger);

    if (viewHierarchy == null) {
      logger.log(SentryLevel.ERROR, "Could not get ViewHierarchy.");
      return null;
    }

    final @Nullable byte[] bytes =
        JsonSerializationUtils.bytesFrom(serializer, logger, viewHierarchy);
    if (bytes == null) {
      logger.log(SentryLevel.ERROR, "Could not serialize ViewHierarchy.");
      return null;
    }
    if (bytes.length < 1) {
      logger.log(SentryLevel.ERROR, "Got empty bytes array after serializing ViewHierarchy.");
      return null;
    }

    return bytes;
  }

  @Nullable
  public static ViewHierarchy snapshotViewHierarchy(
      final @Nullable Activity activity, final @NotNull ILogger logger) {
    return snapshotViewHierarchy(
        activity, new ArrayList<>(0), AndroidMainThreadChecker.getInstance(), logger);
  }

  @Nullable
  public static ViewHierarchy snapshotViewHierarchy(
      final @Nullable Activity activity,
      final @NotNull List<ViewHierarchyExporter> exporters,
      final @NotNull IMainThreadChecker mainThreadChecker,
      final @NotNull ILogger logger) {

    if (activity == null) {
      logger.log(SentryLevel.INFO, "Missing activity for view hierarchy snapshot.");
      return null;
    }

    final @Nullable Window window = activity.getWindow();
    if (window == null) {
      logger.log(SentryLevel.INFO, "Missing window for view hierarchy snapshot.");
      return null;
    }

    final @Nullable View decorView = window.peekDecorView();
    if (decorView == null) {
      logger.log(SentryLevel.INFO, "Missing decor view for view hierarchy snapshot.");
      return null;
    }

    try {
      if (mainThreadChecker.isMainThread()) {
        return snapshotViewHierarchy(decorView, exporters);
      } else {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<ViewHierarchy> viewHierarchy = new AtomicReference<>(null);
        activity.runOnUiThread(
            () -> {
              try {
                viewHierarchy.set(snapshotViewHierarchy(decorView, exporters));
                latch.countDown();
              } catch (Throwable t) {
                logger.log(SentryLevel.ERROR, "Failed to process view hierarchy.", t);
              }
            });
        if (latch.await(CAPTURE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
          return viewHierarchy.get();
        }
      }
    } catch (Throwable t) {
      logger.log(SentryLevel.ERROR, "Failed to process view hierarchy.", t);
    }
    return null;
  }

  @NotNull
  public static ViewHierarchy snapshotViewHierarchy(final @NotNull View view) {
    return snapshotViewHierarchy(view, new ArrayList<>(0));
  }

  @NotNull
  public static ViewHierarchy snapshotViewHierarchy(
      final @NotNull View view, final @NotNull List<ViewHierarchyExporter> exporters) {
    final List<ViewHierarchyNode> windows = new ArrayList<>(1);
    final ViewHierarchy viewHierarchy = new ViewHierarchy("android_view_system", windows);

    final @NotNull ViewHierarchyNode node = viewToNode(view);
    windows.add(node);
    addChildren(view, node, exporters);

    return viewHierarchy;
  }

  private static void addChildren(
      final @NotNull View view,
      final @NotNull ViewHierarchyNode parentNode,
      final @NotNull List<ViewHierarchyExporter> exporters) {
    if (!(view instanceof ViewGroup)) {
      return;
    }

    // In case any external exporter recognizes it's own widget (e.g. AndroidComposeView)
    // we can immediately return
    for (ViewHierarchyExporter exporter : exporters) {
      if (exporter.export(parentNode, view)) {
        return;
      }
    }

    final @NotNull ViewGroup viewGroup = ((ViewGroup) view);
    final int childCount = viewGroup.getChildCount();
    if (childCount == 0) {
      return;
    }

    final @NotNull List<ViewHierarchyNode> childNodes = new ArrayList<>(childCount);
    for (int i = 0; i < childCount; i++) {
      final @Nullable View child = viewGroup.getChildAt(i);
      if (child != null) {
        final @NotNull ViewHierarchyNode childNode = viewToNode(child);
        childNodes.add(childNode);
        addChildren(child, childNode, exporters);
      }
    }
    parentNode.setChildren(childNodes);
  }

  @NotNull
  private static ViewHierarchyNode viewToNode(@NotNull final View view) {
    @NotNull final ViewHierarchyNode node = new ViewHierarchyNode();

    @Nullable String className = view.getClass().getCanonicalName();
    if (className == null) {
      className = view.getClass().getSimpleName();
    }
    node.setType(className);

    try {
      final String identifier = ViewUtils.getResourceId(view);
      node.setIdentifier(identifier);
    } catch (Throwable e) {
      // ignored
    }
    node.setX((double) view.getX());
    node.setY((double) view.getY());
    node.setWidth((double) view.getWidth());
    node.setHeight((double) view.getHeight());
    node.setAlpha((double) view.getAlpha());

    switch (view.getVisibility()) {
      case View.VISIBLE:
        node.setVisibility("visible");
        break;
      case View.INVISIBLE:
        node.setVisibility("invisible");
        break;
      case View.GONE:
        node.setVisibility("gone");
        break;
      default:
        // ignored
        break;
    }

    return node;
  }
}

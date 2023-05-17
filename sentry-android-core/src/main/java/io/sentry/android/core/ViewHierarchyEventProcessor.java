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
import io.sentry.android.core.internal.util.AndroidMainThreadChecker;
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
  private static final long CAPTURE_TIMEOUT_MS = 1000;

  public ViewHierarchyEventProcessor(final @NotNull SentryAndroidOptions options) {
    this.options = Objects.requireNonNull(options, "SentryAndroidOptions is required");
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

    final @Nullable Activity activity = CurrentActivityHolder.getInstance().getActivity();
    final @Nullable ViewHierarchy viewHierarchy =
        snapshotViewHierarchy(activity, options.getMainThreadChecker(), options.getLogger());

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
    ViewHierarchy viewHierarchy = snapshotViewHierarchy(activity, mainThreadChecker, logger);

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
      @Nullable Activity activity, @NotNull ILogger logger) {
    return snapshotViewHierarchy(activity, AndroidMainThreadChecker.getInstance(), logger);
  }

  @Nullable
  public static ViewHierarchy snapshotViewHierarchy(
      @Nullable Activity activity,
      @NotNull IMainThreadChecker mainThreadChecker,
      @NotNull ILogger logger) {
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
        return snapshotViewHierarchy(decorView);
      } else {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<ViewHierarchy> viewHierarchy = new AtomicReference<>(null);
        activity.runOnUiThread(
            () -> {
              try {
                viewHierarchy.set(snapshotViewHierarchy(decorView));
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
  public static ViewHierarchy snapshotViewHierarchy(@NotNull final View view) {
    final List<ViewHierarchyNode> windows = new ArrayList<>(1);
    final ViewHierarchy viewHierarchy = new ViewHierarchy("android_view_system", windows);

    final @NotNull ViewHierarchyNode node = viewToNode(view);
    windows.add(node);
    addChildren(view, node);

    return viewHierarchy;
  }

  private static void addChildren(
      @NotNull final View view, @NotNull final ViewHierarchyNode parentNode) {
    if (!(view instanceof ViewGroup)) {
      return;
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
        addChildren(child, childNode);
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

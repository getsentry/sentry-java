package io.sentry.android.core;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import io.sentry.Attachment;
import io.sentry.EventProcessor;
import io.sentry.Hint;
import io.sentry.ILogger;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.android.core.internal.gestures.ViewUtils;
import io.sentry.protocol.ViewHierarchy;
import io.sentry.protocol.ViewHierarchyNode;
import io.sentry.util.Objects;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** ViewHierarchyEventProcessor responsible for taking a snapshot of the current view hierarchy. */
@ApiStatus.Internal
public final class ViewHierarchyEventProcessor implements EventProcessor {

  private final @NotNull SentryAndroidOptions options;

  public ViewHierarchyEventProcessor(final @NotNull SentryAndroidOptions options) {
    this.options = Objects.requireNonNull(options, "SentryAndroidOptions is required");
  }

  @Override
  public @NotNull SentryEvent process(final @NotNull SentryEvent event, @NotNull Hint hint) {
    if (!event.isErrored()) {
      return event;
    }

    if (!options.isAttachViewHierarchy()) {
      this.options.getLogger().log(SentryLevel.DEBUG, "attachViewHierarchy is disabled.");
      return event;
    }

    final Activity activity = CurrentActivityHolder.getInstance().getActivity();
    if (activity == null) {
      return event;
    }

    final ViewHierarchy viewHierarchy = snapshotViewHierarchy(activity, options.getLogger());
    if (viewHierarchy != null) {
      hint.setViewHierarchy(Attachment.fromViewHierarchy(viewHierarchy));
    }

    return event;
  }

  @Nullable
  private static ViewHierarchy snapshotViewHierarchy(Activity activity, ILogger logger) {
    final List<ViewHierarchyNode> windows = new ArrayList<>();
    final ViewHierarchy viewHierarchy = new ViewHierarchy("android_view_system", windows);

    final @Nullable View decorView = activity.getWindow().peekDecorView();
    if (decorView == null) {
      return viewHierarchy;
    }

    final @NotNull ViewHierarchyNode decorNode = viewToNode(decorView);
    windows.add(decorNode);
    addChildren(decorView, decorNode);

    return viewHierarchy;
  }

  private static void addChildren(View view, ViewHierarchyNode viewNode) {
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
    viewNode.setChildren(childNodes);
  }

  @NotNull
  private static ViewHierarchyNode viewToNode(final View view) {
    @NotNull final ViewHierarchyNode node = new ViewHierarchyNode();

    @Nullable String className = view.getClass().getCanonicalName();
    if (className == null) {
      className = view.getClass().getSimpleName();
    }
    node.setType(className);

    try {
      final String identifier = ViewUtils.getResourceId(view);
      node.setIdentifier(identifier);
    } catch (Exception e) {
      // ignored
    }
    node.setX((double) view.getX());
    node.setY((double) view.getY());
    node.setWidth((double) view.getWidth());
    node.setHeight((double) view.getHeight());
    node.setAlpha((double) view.getAlpha());
    node.setVisible(view.getVisibility() == View.VISIBLE);

    return node;
  }
}

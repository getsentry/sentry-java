package io.sentry.android.core;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import androidx.annotation.NonNull;
import io.sentry.Attachment;
import io.sentry.EventProcessor;
import io.sentry.Hint;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.android.core.internal.gestures.ViewUtils;
import io.sentry.protocol.ViewHierarchy;
import io.sentry.protocol.ViewHierarchyNode;
import io.sentry.util.Objects;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** ViewHierarchyEventProcessor responsible for taking a snapshot of the current view hierarchy. */
@ApiStatus.Internal
public final class ViewHierarchyEventProcessor implements EventProcessor {

  @SuppressWarnings("CharsetObjectCanBeUsed")
  private static final @NotNull Charset UTF_8 = Charset.forName("UTF-8");

  private final @NotNull SentryAndroidOptions options;

  public ViewHierarchyEventProcessor(final @NotNull SentryAndroidOptions options) {
    this.options = Objects.requireNonNull(options, "SentryAndroidOptions is required");
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

    final @Nullable Activity activity = CurrentActivityHolder.getInstance().getActivity();
    if (activity == null) {
      options.getLogger().log(SentryLevel.INFO, "Missing activity for view hierarchy snapshot.");
      return event;
    }

    final @Nullable Window window = activity.getWindow();
    if (window == null) {
      options.getLogger().log(SentryLevel.INFO, "Missing window for view hierarchy snapshot.");
      return event;
    }

    final @Nullable View decorView = window.peekDecorView();
    options.getLogger().log(SentryLevel.INFO, "Missing decor view for view hierarchy snapshot.");
    if (decorView == null) {
      return event;
    }

    try {
      final @NotNull ViewHierarchy viewHierarchy = snapshotViewHierarchy(decorView);
      attachViewHierarchy(viewHierarchy, hint);
    } catch (Throwable t) {
      options.getLogger().log(SentryLevel.ERROR, "Failed to process view hierarchy.", t);
    }
    return event;
  }

  private void attachViewHierarchy(@NonNull ViewHierarchy viewHierarchy, @NonNull Hint hint) {
    hint.setViewHierarchy(
        Attachment.fromViewHierarchy(
            () -> {
              try {
                try (final ByteArrayOutputStream stream = new ByteArrayOutputStream();
                    final Writer writer =
                        new BufferedWriter(new OutputStreamWriter(stream, UTF_8))) {

                  options.getSerializer().serialize(viewHierarchy, writer);
                  return stream.toByteArray();
                }
              } catch (Throwable t) {
                options.getLogger().log(SentryLevel.ERROR, "Could not serialize ViewHierarchy", t);
                throw t;
              }
            }));
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

package io.sentry.compose.viewhierarchy;

import androidx.compose.runtime.collection.MutableVector;
import androidx.compose.ui.geometry.Rect;
import androidx.compose.ui.layout.ModifierInfo;
import androidx.compose.ui.node.LayoutNode;
import androidx.compose.ui.node.Owner;
import io.sentry.ILogger;
import io.sentry.ISentryLifecycleToken;
import io.sentry.compose.SentryComposeHelper;
import io.sentry.internal.viewhierarchy.ViewHierarchyExporter;
import io.sentry.protocol.ViewHierarchyNode;
import io.sentry.util.AutoClosableReentrantLock;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("KotlinInternalInJava")
public final class ComposeViewHierarchyExporter implements ViewHierarchyExporter {

  @NotNull private final ILogger logger;
  @Nullable private volatile SentryComposeHelper composeHelper;
  private final @NotNull AutoClosableReentrantLock lock = new AutoClosableReentrantLock();

  public ComposeViewHierarchyExporter(@NotNull final ILogger logger) {
    this.logger = logger;
  }

  @Override
  public boolean export(@NotNull final ViewHierarchyNode parent, @NotNull final Object element) {

    if (!(element instanceof Owner)) {
      return false;
    }

    // lazy init composeHelper as it's using some reflection under the hood
    if (composeHelper == null) {
      try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
        if (composeHelper == null) {
          composeHelper = new SentryComposeHelper(logger);
        }
      }
    }

    final @NotNull LayoutNode rootNode = ((Owner) element).getRoot();
    addChild(composeHelper, parent, null, rootNode);
    return true;
  }

  private static void addChild(
      @NotNull final SentryComposeHelper composeHelper,
      @NotNull final ViewHierarchyNode parent,
      @Nullable final LayoutNode parentNode,
      @NotNull final LayoutNode node) {
    if (node.isPlaced()) {
      final ViewHierarchyNode vhNode = new ViewHierarchyNode();
      setTag(composeHelper, node, vhNode);
      setBounds(composeHelper, node, parentNode, vhNode);

      if (vhNode.getTag() != null) {
        vhNode.setType(vhNode.getTag());
      } else {
        vhNode.setType("@Composable");
      }

      if (parent.getChildren() == null) {
        parent.setChildren(new ArrayList<>());
      }
      parent.getChildren().add(vhNode);

      final MutableVector<LayoutNode> children = node.getZSortedChildren();
      final int childrenCount = children.getSize();
      for (int i = 0; i < childrenCount; i++) {
        final LayoutNode child = children.get(i);
        addChild(composeHelper, vhNode, node, child);
      }
    }
  }

  private static void setTag(
      final @NotNull SentryComposeHelper helper,
      final @NotNull LayoutNode node,
      final @NotNull ViewHierarchyNode vhNode) {
    // needs to be in-sync with ComposeGestureTargetLocator
    final List<ModifierInfo> modifiers = node.getModifierInfo();
    for (ModifierInfo modifierInfo : modifiers) {
      final @Nullable String tag = helper.extractTag(modifierInfo.getModifier());
      if (tag != null) {
        vhNode.setTag(tag);
      }
    }
  }

  private static void setBounds(
      final @NotNull SentryComposeHelper composeHelper,
      final @NotNull LayoutNode node,
      final @Nullable LayoutNode parentNode,
      final @NotNull ViewHierarchyNode vhNode) {

    final int nodeHeight = node.getHeight();
    final int nodeWidth = node.getWidth();

    vhNode.setHeight((double) nodeHeight);
    vhNode.setWidth((double) nodeWidth);

    final Rect bounds = composeHelper.getLayoutNodeBoundsInWindow(node);
    if (bounds != null) {
      double x = bounds.getLeft();
      double y = bounds.getTop();
      // layout coordinates for view hierarchy are relative to the parent node
      if (parentNode != null) {
        final @Nullable Rect parentBounds = composeHelper.getLayoutNodeBoundsInWindow(parentNode);
        if (parentBounds != null) {
          x -= parentBounds.getLeft();
          y -= parentBounds.getTop();
        }
      }

      vhNode.setX(x);
      vhNode.setY(y);
    }
  }
}

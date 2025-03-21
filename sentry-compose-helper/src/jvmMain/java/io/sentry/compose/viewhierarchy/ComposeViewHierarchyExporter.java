package io.sentry.compose.viewhierarchy;

import androidx.compose.runtime.collection.MutableVector;
import androidx.compose.ui.Modifier;
import androidx.compose.ui.geometry.Rect;
import androidx.compose.ui.layout.ModifierInfo;
import androidx.compose.ui.node.LayoutNode;
import androidx.compose.ui.node.Owner;
import androidx.compose.ui.semantics.SemanticsConfiguration;
import androidx.compose.ui.semantics.SemanticsModifier;
import androidx.compose.ui.semantics.SemanticsPropertyKey;
import io.sentry.ILogger;
import io.sentry.ISentryLifecycleToken;
import io.sentry.compose.SentryComposeHelper;
import io.sentry.internal.viewhierarchy.ViewHierarchyExporter;
import io.sentry.protocol.ViewHierarchyNode;
import io.sentry.util.AutoClosableReentrantLock;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
      setTag(node, vhNode);
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
      final @NotNull LayoutNode node, final @NotNull ViewHierarchyNode vhNode) {
    // needs to be in-sync with ComposeGestureTargetLocator
    final List<ModifierInfo> modifiers = node.getModifierInfo();
    for (ModifierInfo modifierInfo : modifiers) {
      final @NotNull Modifier modifier = modifierInfo.getModifier();
      // Newer Jetpack Compose 1.5 uses Node modifier elements
      final @Nullable String type = modifier.getClass().getCanonicalName();
      if (modifier instanceof SemanticsModifier) {
        final SemanticsModifier semanticsModifierCore =
            (SemanticsModifier) modifierInfo.getModifier();
        final SemanticsConfiguration semanticsConfiguration =
            semanticsModifierCore.getSemanticsConfiguration();
        for (Map.Entry<? extends SemanticsPropertyKey<?>, ?> entry : semanticsConfiguration) {
          final @Nullable String key = entry.getKey().getName();
          if ("SentryTag".equals(key) || "TestTag".equals(key)) {
            if (entry.getValue() instanceof String) {
              vhNode.setTag((String) entry.getValue());
            }
          }
        }
      } else if ("androidx.compose.ui.platform.TestTagElement".equals(type)
          || "io.sentry.compose.SentryModifier.SentryTagModifierNodeElement".equals(type)) {
        // Newer Jetpack Compose uses TestTagElement as node elements
        // See
        // https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/ui/ui/src/commonMain/kotlin/androidx/compose/ui/platform/TestTag.kt;l=34;drc=dcaa116fbfda77e64a319e1668056ce3b032469f
        try {
          final Field tagField = modifier.getClass().getDeclaredField("tag");
          tagField.setAccessible(true);
          final @Nullable Object value = tagField.get(modifier);
          if (value instanceof String) {
            vhNode.setTag((String) value);
          }
        } catch (Throwable e) {
          // ignored
        }
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

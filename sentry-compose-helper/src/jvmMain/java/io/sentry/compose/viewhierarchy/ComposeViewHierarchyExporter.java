package io.sentry.compose.viewhierarchy;

import androidx.compose.runtime.collection.MutableVector;
import androidx.compose.ui.layout.ModifierInfo;
import androidx.compose.ui.node.LayoutNode;
import androidx.compose.ui.node.Owner;
import androidx.compose.ui.semantics.SemanticsConfiguration;
import androidx.compose.ui.semantics.SemanticsModifier;
import androidx.compose.ui.semantics.SemanticsPropertyKey;
import io.sentry.compose.SentryComposeUtil;
import io.sentry.internal.viewhierarchy.ViewHierarchyExporter;
import io.sentry.protocol.ViewHierarchyNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("KotlinInternalInJava")
public final class ComposeViewHierarchyExporter implements ViewHierarchyExporter {

  @Override
  public boolean export(@NotNull final ViewHierarchyNode parent, @NotNull final Object element) {

    if (!(element instanceof Owner)) {
      return false;
    }

    final @NotNull LayoutNode rootNode = ((Owner) element).getRoot();
    addChild(parent, rootNode);
    return true;
  }

  private static void addChild(
      @NotNull final ViewHierarchyNode parent, @NotNull final LayoutNode node) {
    if (node.isPlaced()) {
      final ViewHierarchyNode vhNode = new ViewHierarchyNode();
      setBounds(node, vhNode);
      setTag(node, vhNode);
      if (parent.getChildren() == null) {
        parent.setChildren(new ArrayList<>());
      }
      parent.getChildren().add(vhNode);

      final MutableVector<LayoutNode> children = node.getZSortedChildren();
      final int childrenCount = children.getSize();
      for (int i = 0; i < childrenCount; i++) {
        final LayoutNode child = children.get(i);
        addChild(vhNode, child);
      }
    }
  }

  private static void setTag(
      @NotNull final LayoutNode node, @NotNull final ViewHierarchyNode vhNode) {
    final List<ModifierInfo> modifiers = node.getModifierInfo();
    for (ModifierInfo modifierInfo : modifiers) {
      if (modifierInfo.getModifier() instanceof SemanticsModifier) {
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
      }
    }
  }

  private static void setBounds(@NotNull LayoutNode node, @NotNull ViewHierarchyNode vhNode) {
    final int nodeHeight = node.getHeight();
    final int nodeWidth = node.getWidth();

    final int[] xy = SentryComposeUtil.getLayoutNodeXY(node);

    vhNode.setHeight(Double.valueOf(nodeHeight));
    vhNode.setWidth(Double.valueOf(nodeWidth));
    vhNode.setX(Double.valueOf(xy[0]));
    vhNode.setY(Double.valueOf(xy[1]));
  }
}

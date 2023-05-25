package io.sentry.compose.viewhierarchy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import androidx.compose.runtime.collection.MutableVector;
import androidx.compose.ui.layout.LayoutCoordinates;
import androidx.compose.ui.layout.ModifierInfo;
import androidx.compose.ui.node.LayoutNode;
import androidx.compose.ui.node.Owner;
import androidx.compose.ui.semantics.SemanticsConfiguration;
import androidx.compose.ui.semantics.SemanticsModifier;
import androidx.compose.ui.semantics.SemanticsPropertyKey;
import io.sentry.NoOpLogger;
import io.sentry.internal.viewhierarchy.ViewHierarchyExporter;
import io.sentry.protocol.ViewHierarchyNode;
import java.util.ArrayList;
import java.util.List;
import kotlin.jvm.functions.Function2;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import org.mockito.Mockito;

public class ComposeViewHierarchyExporterTest {

  @Test
  public void testComposeViewHierarchyExport() {
    final ViewHierarchyNode rootVhNode = new ViewHierarchyNode();

    final LayoutNode childA = mockLayoutNode(true, "childA", 10, 20);
    final LayoutNode childB = mockLayoutNode(true, null, 10, 20);
    final LayoutNode childC = mockLayoutNode(false, null, 10, 20);
    final LayoutNode parent = mockLayoutNode(true, "root", 30, 40, childA, childB, childC);

    final Owner node = Mockito.mock(Owner.class);
    Mockito.when(node.getRoot()).thenReturn(parent);

    final ViewHierarchyExporter exporter =
        new ComposeViewHierarchyExporter(NoOpLogger.getInstance());
    exporter.export(rootVhNode, node);

    assertEquals(1, rootVhNode.getChildren().size());
    final ViewHierarchyNode parentVhNode = rootVhNode.getChildren().get(0);

    assertEquals("root", parentVhNode.getTag());
    assertEquals(30.0, parentVhNode.getWidth().doubleValue(), 0.001);
    assertEquals(40.0, parentVhNode.getHeight().doubleValue(), 0.001);

    // ensure not placed elements (childC) are not part of the view hierarchy
    assertEquals(2, parentVhNode.getChildren().size());

    final ViewHierarchyNode childAVhNode = parentVhNode.getChildren().get(0);
    assertEquals("childA", childAVhNode.getTag());
    assertEquals(10.0, childAVhNode.getWidth().doubleValue(), 0.001);
    assertEquals(20.0, childAVhNode.getHeight().doubleValue(), 0.001);
    assertNull(childAVhNode.getChildren());

    final ViewHierarchyNode childBVhNode = parentVhNode.getChildren().get(1);
    assertNull(childBVhNode.getTag());
  }

  private static LayoutNode mockLayoutNode(
      final boolean isPlaced,
      final @Nullable String tag,
      final int width,
      final int height,
      LayoutNode... children) {
    final LayoutNode nodeA = Mockito.mock(LayoutNode.class);
    Mockito.when(nodeA.isPlaced()).thenReturn(isPlaced);
    Mockito.when((nodeA.getWidth())).thenReturn(width);
    Mockito.when((nodeA.getHeight())).thenReturn(height);

    final ModifierInfo modifierInfo = Mockito.mock(ModifierInfo.class);
    Mockito.when(modifierInfo.getModifier())
        .thenReturn(
            new SemanticsModifier() {
              @NotNull
              @Override
              public SemanticsConfiguration getSemanticsConfiguration() {
                final SemanticsConfiguration config = new SemanticsConfiguration();
                config.set(
                    new SemanticsPropertyKey<>(
                        "SentryTag",
                        new Function2<String, String, String>() {
                          @Override
                          public String invoke(String s, String s2) {
                            return s;
                          }
                        }),
                    tag);
                return config;
              }
            });
    final List<ModifierInfo> modifierInfoList = new ArrayList<>();
    modifierInfoList.add(modifierInfo);
    Mockito.when((nodeA.getModifierInfo())).thenReturn(modifierInfoList);

    Mockito.when((nodeA.getZSortedChildren()))
        .thenReturn(new MutableVector<>(children, children.length));

    final LayoutCoordinates coordinates = Mockito.mock(LayoutCoordinates.class);
    Mockito.when(nodeA.getCoordinates()).thenReturn(coordinates);
    return nodeA;
  }
}

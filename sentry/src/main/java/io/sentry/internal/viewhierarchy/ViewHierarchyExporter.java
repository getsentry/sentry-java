package io.sentry.internal.viewhierarchy;

import io.sentry.protocol.ViewHierarchyNode;
import org.jetbrains.annotations.NotNull;

public interface ViewHierarchyExporter {
  /**
   * Exports the view hierarchy
   *
   * @param parent the parent view hierarchy node to which element should be attached to
   * @param element The UI widget
   * @return true if element was processed and the corresponding view hierarchy was attached to
   *     parent, false otherwise
   */
  boolean export(@NotNull final ViewHierarchyNode parent, @NotNull final Object element);
}

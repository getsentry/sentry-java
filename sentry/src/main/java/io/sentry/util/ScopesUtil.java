package io.sentry.util;

import io.sentry.IScopes;
import io.sentry.Scopes;
import io.sentry.ScopesAdapter;
import io.sentry.Sentry;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * A util for printing the scopes chain from passed in scopes all the way up to its initial
 * ancestor.
 *
 * <p>It walks up the parents of each scopes instance until it reaches a parent of null. Parent
 * references are weak, so collected intermediate parents are skipped and traversal continues from
 * the root Scopes created in Sentry.init.
 */
@ApiStatus.Internal
public final class ScopesUtil {

  public static void printScopesChain(final @Nullable IScopes scopes) {
    System.out.println("==========================================");
    System.out.println("=============== v Scopes v ===============");
    System.out.println("==========================================");

    printScopesChainInternal(scopes);

    System.out.println("==========================================");
    System.out.println("=============== ^ Scopes ^ ===============");
    System.out.println("==========================================");
  }

  @SuppressWarnings("ObjectToString")
  private static void printScopesChainInternal(final @Nullable IScopes someScopes) {
    @Nullable IScopes current = someScopes;
    while (current != null) {
      if (current instanceof Scopes) {
        final Scopes scopes = (Scopes) current;
        final String info =
            String.format(
                "%-25s {g=%-25s, i=%-25s, c=%-25s} [%s]",
                scopes,
                scopes.getGlobalScope(),
                scopes.getIsolationScope(),
                scopes.getScope(),
                scopes.getCreator());
        System.out.println(info);
        current = current.getParentScopes();
      } else if (current instanceof ScopesAdapter) {
        current = Sentry.getCurrentScopes();
      } else {
        System.out.println("Hit unhandled Scopes class" + current.getClass());
        return;
      }
    }
    System.out.println("-");
  }
}

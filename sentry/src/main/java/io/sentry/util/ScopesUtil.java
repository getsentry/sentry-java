package io.sentry.util;

import io.sentry.IScopes;
import io.sentry.Scopes;
import io.sentry.ScopesAdapter;
import io.sentry.Sentry;
import org.jetbrains.annotations.Nullable;

/**
 * A util for printing the scopes chain from passed in scopes all the way up to its initial
 * ancestor.
 *
 * <p>It walks up the parents of each scopes instance until it reaches a parent of null. The scopes
 * without a parent are the ones created in Sentry.init.
 */
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

  @SuppressWarnings({"ObjectToString", "deprecation"})
  private static void printScopesChainInternal(final @Nullable IScopes someScopes) {
    if (someScopes != null) {
      if (someScopes instanceof Scopes) {
        Scopes scopes = (Scopes) someScopes;
        String info =
            String.format(
                "%-25s {g=%-25s, i=%-25s, c=%-25s} [%s]",
                scopes,
                scopes.getGlobalScope(),
                scopes.getIsolationScope(),
                scopes.getScope(),
                scopes.getCreator());
        System.out.println(info);
        printScopesChainInternal(someScopes.getParentScopes());
      } else if (someScopes instanceof ScopesAdapter
          || someScopes instanceof io.sentry.HubAdapter) {
        printScopesChainInternal(Sentry.getCurrentScopes());
      } else if (someScopes instanceof io.sentry.HubScopesWrapper) {
        io.sentry.HubScopesWrapper wrapper = (io.sentry.HubScopesWrapper) someScopes;
        printScopesChainInternal(wrapper.getScopes());
      } else {
        System.out.println("Hit unhandled Scopes class" + someScopes.getClass());
      }
    } else {
      System.out.println("-");
    }
  }
}

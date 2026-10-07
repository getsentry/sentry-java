package io.sentry.hints;

import java.io.File;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * A hint that can take over the decision whether the cached file it was created for is deleted.
 *
 * <p>The decision needs the outcome of the send, which the transport reports asynchronously. A
 * caller that stops waiting for that outcome hands the file over to the hint instead of guessing,
 * because a guess either loses the envelope or sends it twice.
 *
 * <p>Implementations delegate to {@link io.sentry.util.DeferredDeleteDecision}.
 */
@ApiStatus.Internal
public interface DeferredDelete {

  /**
   * Hands the file over to this hint.
   *
   * @param file the cached file backing this hint
   * @return true if the hint took over, and will delete or keep the file once the outcome of the
   *     send is known. false if the outcome is already known, in which case the caller keeps the
   *     decision.
   */
  boolean deferDelete(@NotNull File file);
}

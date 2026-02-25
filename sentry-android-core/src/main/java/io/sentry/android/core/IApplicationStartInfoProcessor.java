package io.sentry.android.core;

import androidx.annotation.RequiresApi;
import io.sentry.IScopes;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Interface for processing ApplicationStartInfo data from Android system.
 *
 * <p>Processors are registered with {@link ApplicationStartInfoIntegration} and are called when app
 * start data becomes available, either from historical data or the current app start.
 *
 * <p>Requires API level 35 (Android 15) or higher.
 */
@ApiStatus.Internal
interface IApplicationStartInfoProcessor {

  /**
   * Process the ApplicationStartInfo data.
   *
   * @param startInfo The ApplicationStartInfo from Android system
   * @param tags Extracted tags (start.reason, start.type, start.launch_mode)
   * @param scopes The Sentry scopes for capturing events
   */
  @RequiresApi(api = 35)
  void process(
      @NotNull android.app.ApplicationStartInfo startInfo,
      @NotNull Map<String, String> tags,
      @NotNull IScopes scopes);
}

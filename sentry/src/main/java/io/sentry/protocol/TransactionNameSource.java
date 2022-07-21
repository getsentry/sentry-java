package io.sentry.protocol;

import io.sentry.ILogger;
import io.sentry.JsonDeserializer;
import io.sentry.JsonObjectReader;
import io.sentry.JsonObjectWriter;
import io.sentry.JsonSerializable;
import java.io.IOException;
import java.util.Locale;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public enum TransactionNameSource implements JsonSerializable {
  /**
   * User-defined name
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>my_transaction
   * </ul>
   */
  CUSTOM,

  /**
   * Raw URL, potentially containing identifiers.
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>/auth/login/john123/
   *   <li>GET /auth/login/john123/
   * </ul>
   */
  URL,

  /**
   * Parametrized URL / route
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>/auth/login/:userId/
   *   <li>GET /auth/login/{user}/
   * </ul>
   */
  ROUTE,

  /**
   * Name of the view handling the request.
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>UserListView
   * </ul>
   */
  VIEW,

  /**
   * Named after a software component, such as a function or class name.
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>AuthLogin.login
   *   <li>LoginActivity.login_button
   * </ul>
   */
  COMPONENT,

  /**
   * Name of a background task
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>sentry.tasks.do_something
   * </ul>
   */
  TASK

//  /**
//   * This is the default value set by Relay for legacy SDKs.
//   */
//  UNKNOWN
;

  @Override
  public void serialize(@NotNull JsonObjectWriter writer, @NotNull ILogger logger)
      throws IOException {
    writer.value(name().toLowerCase(Locale.ROOT));
  }

  static final class Deserializer implements JsonDeserializer<TransactionNameSource> {

    @Override
    public @NotNull TransactionNameSource deserialize(
        @NotNull JsonObjectReader reader, @NotNull ILogger logger) throws Exception {
      return TransactionNameSource.valueOf(reader.nextString().toUpperCase(Locale.ROOT));
    }
  }
}

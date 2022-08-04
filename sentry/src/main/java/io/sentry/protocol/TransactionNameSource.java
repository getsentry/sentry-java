package io.sentry.protocol;

import java.util.Locale;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public enum TransactionNameSource {
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

  public String apiName() {
    return name().toLowerCase(Locale.ROOT);
  }
}

package io.sentry;

public enum ScopeBindingMode {
  /**
   * Let the SDK decide whether to add the span to the current scope.
   *
   * <p>The SDK will try to find the parent span on the scope to make the decision. If the parent
   * span is on the scope, the SDK will put the new span on the scope as well.
   *
   * <p>This does not apply to transactions. If you want to have a transaction bound to scope,
   * please use ON instead.
   */
  AUTO,
  /** Set the new span on the current scope. */
  ON,
  /** Do not set the new span on the scope. */
  OFF
}

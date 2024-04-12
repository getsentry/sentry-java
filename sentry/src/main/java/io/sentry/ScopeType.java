package io.sentry;

public enum ScopeType {
  CURRENT,
  ISOLATION,
  GLOBAL,

  // TODO do we need a combined as well so configureScope
  COMBINED;
}

package io.sentry;

import io.sentry.protocol.User;

public class Scope {
  private SentryLevel level;
  private String transaction;
  private String environment;
  private User user;
}

package io.sentry;

public interface ISpan {
  ISpan startChild();
  String toTraceparent();
  void finish();
}

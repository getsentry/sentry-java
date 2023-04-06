package io.sentry.android.core.internal.threaddump;

public class Line {
  public int lineno;
  public String text;

  public Line() {
  }

  public Line(int lineno, String text) {
    this.lineno = lineno;
    this.text = text;
  }
}

package io.sentry;

import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

final public class FilterString {
  private final @NotNull String filterString;
  private final @NotNull Pattern pattern;

  public FilterString(@NotNull String filterString) {
    this.filterString = filterString;
    this.pattern = Pattern.compile(filterString);
  }

  public @NotNull String getFilterString() {
    return filterString;
  }

  public boolean matches(String input) {
    return pattern.matcher(input).matches();
  }
}

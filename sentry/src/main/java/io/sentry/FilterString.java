package io.sentry;

import java.util.Objects;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;

public final class FilterString {
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

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    FilterString that = (FilterString) o;
    return Objects.equals(filterString, that.filterString);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(filterString);
  }
}

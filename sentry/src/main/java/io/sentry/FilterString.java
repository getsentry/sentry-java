package io.sentry;

import java.util.Objects;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class FilterString {
  private final @NotNull String filterString;
  private final @Nullable Pattern pattern;

  public FilterString(@NotNull String filterString) {
    this.filterString = filterString;
    @Nullable Pattern pattern = null;
    try {
      pattern = Pattern.compile(filterString);
    } catch (Throwable t) {
      Sentry.getCurrentScopes()
          .getOptions()
          .getLogger()
          .log(
              SentryLevel.DEBUG,
              "Only using filter string for String comparison as it could not be parsed as regex: %s",
              filterString);
    }
    this.pattern = pattern;
  }

  public @NotNull String getFilterString() {
    return filterString;
  }

  public boolean matches(String input) {
    if (pattern == null) {
      return false;
    }
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
    return Objects.hash(filterString);
  }
}

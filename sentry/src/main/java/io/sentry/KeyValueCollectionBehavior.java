package io.sentry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/** Controls how automatically collected key-value data is filtered. */
public final class KeyValueCollectionBehavior {

  /** The collection strategy applied to key-value data. */
  public enum Mode {
    /** Do not collect keys or values. */
    OFF,
    /** Collect keys and filter values whose keys match a deny-list term. */
    DENY_LIST,
    /** Collect keys and filter values unless their keys match an allow-list term. */
    ALLOW_LIST
  }

  private @NotNull Mode mode = Mode.DENY_LIST;
  private @NotNull List<String> terms = Collections.emptyList();

  /** Creates a behavior that collects values using the built-in sensitive deny-list. */
  public KeyValueCollectionBehavior() {}

  private KeyValueCollectionBehavior(final @NotNull Mode mode, final @NotNull List<String> terms) {
    setMode(mode);
    setTerms(terms);
  }

  /** Disables collection of the category. */
  public static @NotNull KeyValueCollectionBehavior off() {
    return new KeyValueCollectionBehavior(Mode.OFF, Collections.<String>emptyList());
  }

  /**
   * Collects the category and filters values whose keys match the built-in sensitive deny-list or
   * one of {@code terms}.
   */
  public static @NotNull KeyValueCollectionBehavior denyList(final @NotNull String... terms) {
    return new KeyValueCollectionBehavior(Mode.DENY_LIST, Arrays.asList(terms));
  }

  /**
   * Collects the category and only includes plaintext values whose keys match one of {@code terms}.
   * Values matching the built-in sensitive deny-list are still filtered.
   */
  public static @NotNull KeyValueCollectionBehavior allowList(final @NotNull String... terms) {
    return new KeyValueCollectionBehavior(Mode.ALLOW_LIST, Arrays.asList(terms));
  }

  public @NotNull Mode getMode() {
    return mode;
  }

  public void setMode(final @NotNull Mode mode) {
    this.mode = mode;
  }

  public @NotNull List<String> getTerms() {
    return terms;
  }

  public void setTerms(final @NotNull List<String> terms) {
    this.terms = Collections.unmodifiableList(new ArrayList<>(terms));
  }

  @Override
  public boolean equals(final Object other) {
    if (this == other) {
      return true;
    }
    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    final KeyValueCollectionBehavior that = (KeyValueCollectionBehavior) other;
    return mode == that.mode && terms.equals(that.terms);
  }

  @Override
  public int hashCode() {
    return Objects.hash(mode, terms);
  }
}

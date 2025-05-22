package io.sentry;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SentryAttribute {

  private final @NotNull String name;
  private final @Nullable SentryAttributeType type;
  private final @Nullable Object value;
  private final int flattenDepth;

  private SentryAttribute(
      final @NotNull String name,
      final @Nullable SentryAttributeType type,
      final @Nullable Object value,
      final int flattenDepth) {
    this.name = name;
    this.type = type;
    this.value = value;
    this.flattenDepth = flattenDepth;
  }

  public @NotNull String getName() {
    return name;
  }

  public @Nullable SentryAttributeType getType() {
    return type;
  }

  public @Nullable Object getValue() {
    return value;
  }

  public int getFlattenDepth() {
    return flattenDepth;
  }

  public static @NotNull SentryAttribute named(
      final @NotNull String name, final @Nullable Object value) {
    return new SentryAttribute(name, null, value, 0);
  }

  public static @NotNull SentryAttribute flattened(
      final @NotNull String name, final @Nullable Object value) {
    return new SentryAttribute(name, null, value, 1);
  }

  public static @NotNull SentryAttribute booleanAttribute(
      final @NotNull String name, final @Nullable Boolean value) {
    return new SentryAttribute(name, SentryAttributeType.BOOLEAN, value, 0);
  }

  public static @NotNull SentryAttribute integerAttribute(
      final @NotNull String name, final @Nullable Integer value) {
    return new SentryAttribute(name, SentryAttributeType.INTEGER, value, 0);
  }

  public static @NotNull SentryAttribute doubleAttribute(
      final @NotNull String name, final @Nullable Double value) {
    return new SentryAttribute(name, SentryAttributeType.DOUBLE, value, 0);
  }

  public static @NotNull SentryAttribute stringAttribute(
      final @NotNull String name, final @Nullable String value) {
    return new SentryAttribute(name, SentryAttributeType.STRING, value, 0);
  }
}

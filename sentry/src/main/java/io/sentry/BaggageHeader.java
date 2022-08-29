package io.sentry;

import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Experimental
public final class BaggageHeader {
  public static final @NotNull String BAGGAGE_HEADER = "baggage";

  private final @NotNull String value;

  public BaggageHeader(final @NotNull String value) {
    this.value = value;
  }

  public BaggageHeader(
      final @NotNull Baggage baggage, final @Nullable List<String> thirdPartyBaggageHeaders) {
    final Baggage thirdPartyBaggage =
        Baggage.fromHeader(thirdPartyBaggageHeaders, true, baggage.logger);
    this.value = baggage.toHeaderString(thirdPartyBaggage.getThirdPartyHeader());
  }

  public @NotNull String getName() {
    return BAGGAGE_HEADER;
  }

  public @NotNull String getValue() {
    return value;
  }
}

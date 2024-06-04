package io.sentry;

import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Experimental
public final class BaggageHeader {
  public static final @NotNull String BAGGAGE_HEADER = "baggage";

  private final @NotNull String value;

  @Nullable
  public static BaggageHeader fromBaggageAndOutgoingHeader(
      final @NotNull Baggage baggage, final @Nullable List<String> outgoingBaggageHeaders) {
    final Baggage thirdPartyBaggage =
        Baggage.fromHeader(outgoingBaggageHeaders, true, baggage.logger);
    String headerValue = baggage.toHeaderString(thirdPartyBaggage.getThirdPartyHeader());

    if (headerValue.isEmpty()) {
      return null;
    } else {
      return new BaggageHeader(headerValue);
    }
  }

  public BaggageHeader(final @NotNull String value) {
    this.value = value;
  }

  public @NotNull String getName() {
    return BAGGAGE_HEADER;
  }

  public @NotNull String getPropertyName() {
    return BAGGAGE_HEADER;
  }

  public @NotNull String getValue() {
    return value;
  }
}

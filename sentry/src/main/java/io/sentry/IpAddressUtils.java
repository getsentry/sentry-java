package io.sentry;

import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class IpAddressUtils {
  public static final String DEFAULT_IP_ADDRESS = "{{auto}}";
  private static final List<String> DEFAULT_IP_ADDRESS_VALID_VALUES =
      Arrays.asList(DEFAULT_IP_ADDRESS, "{{ auto }}");

  private IpAddressUtils() {}

  public static boolean isDefault(final @Nullable String ipAddress) {
    return ipAddress != null && DEFAULT_IP_ADDRESS_VALID_VALUES.contains(ipAddress);
  }
}

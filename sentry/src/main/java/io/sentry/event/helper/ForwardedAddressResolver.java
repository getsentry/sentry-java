package io.sentry.event.helper;

import io.sentry.util.Util;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;

/**
 * A {@link RemoteAddressResolver} that uses the first address found in the
 * <tt>X-FORWARDED-FOR</tt> header if one is available, otherwise falls back to
 * the {@link BasicRemoteAddressResolver}.
 */
public class ForwardedAddressResolver implements RemoteAddressResolver {

    private BasicRemoteAddressResolver basicRemoteAddressResolver;

    /**
     * Default constructor, creates fallback {@link BasicRemoteAddressResolver} instance.
     */
    public ForwardedAddressResolver() {
        this.basicRemoteAddressResolver = new BasicRemoteAddressResolver();
    }

    private static String firstAddress(String csvAddrs) {
        List<String> ips = Arrays.asList(csvAddrs.split(","));
        return ips.get(0).trim();
    }

    @Override
    public String getRemoteAddress(HttpServletRequest request) {
        String forwarded = request.getHeader("X-FORWARDED-FOR");
        if (!Util.isNullOrEmpty(forwarded)) {
            return firstAddress(forwarded);
        }
        return basicRemoteAddressResolver.getRemoteAddress(request);
    }

}

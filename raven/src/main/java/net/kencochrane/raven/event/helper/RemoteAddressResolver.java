package net.kencochrane.raven.event.helper;

import javax.servlet.http.HttpServletRequest;

public interface RemoteAddressResolver {

    String getRemoteAddress(HttpServletRequest request);

}

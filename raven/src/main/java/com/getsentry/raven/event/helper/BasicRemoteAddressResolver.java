package com.getsentry.raven.event.helper;

import javax.servlet.http.HttpServletRequest;

public class BasicRemoteAddressResolver implements RemoteAddressResolver {

    public String getRemoteAddress(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

}

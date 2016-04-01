package com.getsentry.raven.helper;

import com.getsentry.raven.dsn.Dsn;
import com.getsentry.raven.event.helper.BasicRemoteAddressResolver;
import com.getsentry.raven.event.helper.ForwardedAddressResolver;
import mockit.Expectations;
import mockit.Mocked;
import org.testng.annotations.Test;

import javax.servlet.http.HttpServletRequest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class RemoteAddressResolverTest {

    @Mocked
    HttpServletRequest request;

    @Test
    public void testBasicRemoteAddressResolver() {
        BasicRemoteAddressResolver resolver = new BasicRemoteAddressResolver();

        new Expectations() {{
            request.getRemoteAddr();
            result = "1.2.3.4";
        }};

        String remoteAddress = resolver.getRemoteAddress(request);
        assertThat(remoteAddress, is("1.2.3.4"));
    }

    @Test
    public void testBasicRemoteAddressResolverWithXForwardedFor() {
        BasicRemoteAddressResolver resolver = new BasicRemoteAddressResolver();

        new Expectations() {{
            request.getRemoteAddr();
            result = "1.2.3.4";
            request.getHeader("X-FORWARDED-FOR");
            result = "9.9.9.9";
        }};

        String remoteAddress = resolver.getRemoteAddress(request);
        assertThat(remoteAddress, is("1.2.3.4"));

        String xForwardedFor = request.getHeader("X-FORWARDED-FOR");
        assertThat(xForwardedFor, is("9.9.9.9"));
    }

    @Test
    public void testForwardedAddressResolver() {
        ForwardedAddressResolver resolver = new ForwardedAddressResolver();

        new Expectations() {{
            request.getHeader("X-FORWARDED-FOR");
            result = "9.9.9.9";
        }};

        String remoteAddress = resolver.getRemoteAddress(request);
        assertThat(remoteAddress, is("9.9.9.9"));
    }

    @Test
    public void testForwardedAddressResolverFallthrough() {
        ForwardedAddressResolver resolver = new ForwardedAddressResolver();

        new Expectations() {{
            request.getRemoteAddr();
            result = "1.2.3.4";
        }};

        String remoteAddress = resolver.getRemoteAddress(request);
        assertThat(remoteAddress, is("1.2.3.4"));
    }

}

package io.sentry.helper;

import io.sentry.BaseTest;
import io.sentry.event.helper.BasicRemoteAddressResolver;
import io.sentry.event.helper.ForwardedAddressResolver;
import io.sentry.event.helper.HttpServletRequestWrapper;

import org.junit.Before;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RemoteAddressResolverTest extends BaseTest {

    private HttpServletRequest request;

    @Before
    public void setup() {
        request = mock(HttpServletRequest.class);
    }

    @Test
    public void testBasicRemoteAddressResolver() {
        BasicRemoteAddressResolver resolver = new BasicRemoteAddressResolver();

        when(request.getRemoteAddr()).thenReturn("1.2.3.4");

        String remoteAddress = resolver.getRemoteAddress(new HttpServletRequestWrapper(request));
        assertThat(remoteAddress, is("1.2.3.4"));
    }

    @Test
    public void testBasicRemoteAddressResolverWithXForwardedFor() {
        BasicRemoteAddressResolver resolver = new BasicRemoteAddressResolver();
        when(request.getRemoteAddr()).thenReturn("1.2.3.4");
        when(request.getHeader(eq("X-FORWARDED-FOR"))).thenReturn("9.9.9.9");

        String remoteAddress = resolver.getRemoteAddress(new HttpServletRequestWrapper(request));
        assertThat(remoteAddress, is("1.2.3.4"));

        String xForwardedFor = request.getHeader("X-FORWARDED-FOR");
        assertThat(xForwardedFor, is("9.9.9.9"));
    }

    @Test
    public void testForwardedAddressResolver() {
        ForwardedAddressResolver resolver = new ForwardedAddressResolver();

        when(request.getHeader(eq("X-FORWARDED-FOR"))).thenReturn("9.9.9.9");

        String remoteAddress = resolver.getRemoteAddress(new HttpServletRequestWrapper(request));
        assertThat(remoteAddress, is("9.9.9.9"));
    }

    @Test
    public void testForwardedAddressResolverFallthrough() {
        ForwardedAddressResolver resolver = new ForwardedAddressResolver();

        when(request.getRemoteAddr()).thenReturn("1.2.3.4");

        String remoteAddress = resolver.getRemoteAddress(new HttpServletRequestWrapper(request));
        assertThat(remoteAddress, is("1.2.3.4"));
    }
}

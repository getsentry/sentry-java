package io.sentry.event.interfaces;

import static java.util.Collections.enumeration;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonMap;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

import io.sentry.BaseTest;
import io.sentry.event.helper.HttpServletRequestWrapper;

import org.junit.Before;
import org.junit.Test;

public class HttpInterfaceTest extends BaseTest {
    private HttpServletRequest mockHttpServletRequest = null;
    private Cookie mockCookie = null;

    @Before
    public void setUp() throws Exception {
        mockHttpServletRequest = mock(HttpServletRequest.class);
        when(mockHttpServletRequest.getRequestURL()).thenReturn(new StringBuffer());
        when(mockHttpServletRequest.getMethod()).thenReturn("method");
        when(mockHttpServletRequest.getParameterMap()).thenReturn(Collections.<String, String[]>emptyMap());
        when(mockHttpServletRequest.getQueryString()).thenReturn("queryString");
        when(mockHttpServletRequest.getCookies()).thenReturn(null);
        when(mockHttpServletRequest.getRemoteAddr()).thenReturn("remoteAddr");
        when(mockHttpServletRequest.getServerName()).thenReturn("serverName");
        when(mockHttpServletRequest.getServerPort()).thenReturn(24);
        when(mockHttpServletRequest.getLocalAddr()).thenReturn("localAddr");
        when(mockHttpServletRequest.getLocalName()).thenReturn("localName");
        when(mockHttpServletRequest.getLocalPort()).thenReturn(42);
        when(mockHttpServletRequest.getProtocol()).thenReturn("protocol");
        when(mockHttpServletRequest.isSecure()).thenReturn(false);
        when(mockHttpServletRequest.isAsyncStarted()).thenReturn(false);
        when(mockHttpServletRequest.getAuthType()).thenReturn("authType");
        when(mockHttpServletRequest.getRemoteUser()).thenReturn("remoteUser");
        when(mockHttpServletRequest.getHeaderNames()).thenReturn(Collections.<String>emptyEnumeration());
        when(mockHttpServletRequest.getHeaders(anyString())).thenReturn(Collections.<String>emptyEnumeration());

        mockCookie = mock(Cookie.class);
    }

    @Test
    public void testHttpServletCopied() throws Exception {
        final String requestUrl = "713d97ff-bda1-4bbe-85bd-42a7bc203551";
        final String method = "afee226d-1c77-41f3-8711-cec1f611af25";
        final String parameterName = "dbb204d7-6332-43d6-bfac-3f112d5f290d";
        final String parameterValue = "00ec3c3e-5ded-4bca-a49b-f7bc9987a914";
        final String queryString = "31497680-12ce-41a6-8285-5de5d06968d3";
        final String cookieName = "451cd683-f7cd-4691-a73f-64829412b5bb";
        final String cookieValue = "5f9a9117-d806-4fd5-b472-a58529e913cd";
        final String remoteAddr = "718d7ac1-d41a-4aa7-84c5-3a877ed2f41c";
        final String serverName = "bf376b4e-a69c-4a2c-988e-5675096a028e";
        final int serverPort = 123;
        final String localAddr = "1d4a9df3-9a2f-46f4-a913-96fe4220bd8a";
        final String localName = "0698cd7f-5d8f-4ecd-8954-e25ac21e6161";
        final int localPort = 321;
        final String protocol = "f4261066-8588-43d3-a71f-9e95fd3e0d65";
        final boolean secure = true;
        final boolean asyncStarted = true;
        final String authType = "b4ec1983-06d1-4f0a-b467-435d2322d69f";
        final String remoteUser = "beae8915-1162-425e-afda-687146b3e3df";
        final String headerKey = "2c4a28c6-cef6-4847-92be-bf161ec4edc6";
        final String headerValue = "2327b4fe-c35f-4bbb-842a-a89c718f5f01";

        when(mockHttpServletRequest.getRequestURL()).thenReturn(new StringBuffer(requestUrl));
        when(mockHttpServletRequest.getMethod()).thenReturn(method);
        when(mockHttpServletRequest.getParameterMap())
                .thenReturn(singletonMap(parameterName, new String[]{parameterValue}));

        when(mockHttpServletRequest.getQueryString()).thenReturn(queryString);

        when(mockCookie.getName()).thenReturn(cookieName);
        when(mockCookie.getValue()).thenReturn(cookieValue);
        when(mockHttpServletRequest.getCookies()).thenReturn(new Cookie[]{mockCookie});

        when(mockHttpServletRequest.getRemoteAddr()).thenReturn(remoteAddr);
        when(mockHttpServletRequest.getServerName()).thenReturn(serverName);
        when(mockHttpServletRequest.getServerPort()).thenReturn(serverPort);
        when(mockHttpServletRequest.getLocalAddr()).thenReturn(localAddr);
        when(mockHttpServletRequest.getLocalName()).thenReturn(localName);
        when(mockHttpServletRequest.getLocalPort()).thenReturn(localPort);
        when(mockHttpServletRequest.getProtocol()).thenReturn(protocol);
        when(mockHttpServletRequest.isSecure()).thenReturn(secure);
        when(mockHttpServletRequest.isAsyncStarted()).thenReturn(asyncStarted);
        when(mockHttpServletRequest.getAuthType()).thenReturn(authType);
        when(mockHttpServletRequest.getRemoteUser()).thenReturn(remoteUser);
        when(mockHttpServletRequest.getHeaderNames()).thenReturn(enumeration(singleton(headerKey)));
        when(mockHttpServletRequest.getHeaders(eq(headerKey))).thenReturn(enumeration(singleton(headerValue)));

        HttpInterface httpInterface = new HttpInterface(new HttpServletRequestWrapper(mockHttpServletRequest));

        assertThat(httpInterface.getRequestUrl(), is(requestUrl));
        assertThat(httpInterface.getMethod(), is(method));
        assertThat(httpInterface.getQueryString(), is(queryString));
        assertThat(httpInterface.getCookies(), hasEntry(cookieName, cookieValue));
        assertThat(httpInterface.getRemoteAddr(), is(remoteAddr));
        assertThat(httpInterface.getServerName(), is(serverName));
        assertThat(httpInterface.getServerPort(), is(serverPort));
        assertThat(httpInterface.getLocalAddr(), is(localAddr));
        assertThat(httpInterface.getLocalName(), is(localName));
        assertThat(httpInterface.getLocalPort(), is(localPort));
        assertThat(httpInterface.getProtocol(), is(protocol));
        assertThat(httpInterface.isSecure(), is(secure));
        assertThat(httpInterface.isAsyncStarted(), is(asyncStarted));
        assertThat(httpInterface.getAuthType(), is(authType));
        assertThat(httpInterface.getRemoteUser(), is(remoteUser));
        assertThat(httpInterface.getHeaders(), hasEntry(is(headerKey), contains(headerValue)));
    }

    @Test
    public void testNullCookies() throws Exception {
        HttpInterface httpInterface = new HttpInterface(new HttpServletRequestWrapper(mockHttpServletRequest));

        assertThat(httpInterface.getCookies().size(), is(0));
    }
}

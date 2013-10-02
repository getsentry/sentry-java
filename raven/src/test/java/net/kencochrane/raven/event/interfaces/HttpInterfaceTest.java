package net.kencochrane.raven.event.interfaces;

import mockit.Injectable;
import mockit.NonStrictExpectations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.util.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class HttpInterfaceTest {
    @Injectable
    private HttpServletRequest mockHttpServletRequest;

    @BeforeMethod
    public void setUp() throws Exception {
        new NonStrictExpectations() {{
            mockHttpServletRequest.getRequestURL();
            result = new StringBuffer();
            mockHttpServletRequest.getMethod();
            result = "method";
            mockHttpServletRequest.getParameterMap();
            result = Collections.emptyMap();
            mockHttpServletRequest.getQueryString();
            result = "queryString";
            mockHttpServletRequest.getCookies();//Could be null
            result = null;
            mockHttpServletRequest.getRemoteAddr();
            result = "remoteAddr";
            mockHttpServletRequest.getServerName();
            result = "serverName";
            mockHttpServletRequest.getServerPort();
            result = 24;
            mockHttpServletRequest.getLocalAddr();
            result = "localAddr";
            mockHttpServletRequest.getLocalName();
            result = "localName";
            mockHttpServletRequest.getLocalPort();
            result = 42;
            mockHttpServletRequest.getProtocol();
            result = "protocol";
            mockHttpServletRequest.isSecure();
            result = false;
            mockHttpServletRequest.isAsyncStarted();
            result = false;
            mockHttpServletRequest.getAuthType();
            result = "authType";
            mockHttpServletRequest.getRemoteUser();
            result = "remoteUser";
            mockHttpServletRequest.getHeaderNames();
            result = new EmptyEnumeration<String>();
            mockHttpServletRequest.getHeaders(anyString);
            result = new EmptyEnumeration<String>();
        }};
    }

    @Test
    public void testHttpServletCopied() throws Exception {
        final String requestUrl = UUID.randomUUID().toString();
        final String method = UUID.randomUUID().toString();
        final String parameterName = UUID.randomUUID().toString();
        final String parameterValue = UUID.randomUUID().toString();
        final String queryString = UUID.randomUUID().toString();
        final String cookieName = UUID.randomUUID().toString();
        final String cookieValue = UUID.randomUUID().toString();
        final String remoteAddr = UUID.randomUUID().toString();
        final String serverName = UUID.randomUUID().toString();
        final int serverPort = 123;
        final String localAddr = UUID.randomUUID().toString();
        final String localName = UUID.randomUUID().toString();
        final int localPort = 321;
        final String protocol = UUID.randomUUID().toString();
        final boolean secure = true;
        final boolean asyncStarted = true;
        final String authType = UUID.randomUUID().toString();
        final String remoteUser = UUID.randomUUID().toString();
        final String headerKey = UUID.randomUUID().toString();
        final String headerValue = UUID.randomUUID().toString();

        new NonStrictExpectations() {
            @Injectable
            private Cookie mockCookie;

            {
                mockHttpServletRequest.getRequestURL();
                result = new StringBuffer(requestUrl);
                mockHttpServletRequest.getMethod();
                result = method;
                mockHttpServletRequest.getParameterMap();
                result = Collections.singletonMap(parameterName, new String[]{parameterValue});
                mockHttpServletRequest.getQueryString();
                result = queryString;
                mockCookie.getName();
                result = cookieName;
                mockCookie.getValue();
                result = cookieValue;
                mockHttpServletRequest.getCookies();
                result = new Cookie[]{mockCookie};
                mockHttpServletRequest.getRemoteAddr();
                result = remoteAddr;
                mockHttpServletRequest.getServerName();
                result = serverName;
                mockHttpServletRequest.getServerPort();
                result = serverPort;
                mockHttpServletRequest.getLocalAddr();
                result = localAddr;
                mockHttpServletRequest.getLocalName();
                result = localName;
                mockHttpServletRequest.getLocalPort();
                result = localPort;
                mockHttpServletRequest.getProtocol();
                result = protocol;
                mockHttpServletRequest.isSecure();
                result = secure;
                mockHttpServletRequest.isAsyncStarted();
                result = asyncStarted;
                mockHttpServletRequest.getAuthType();
                result = authType;
                mockHttpServletRequest.getRemoteUser();
                result = remoteUser;
                mockHttpServletRequest.getHeaderNames();
                result = Collections.enumeration(Arrays.asList(headerKey));
                mockHttpServletRequest.getHeaders(headerKey);
                result = Collections.enumeration(Arrays.asList(headerValue));
            }
        };

        HttpInterface httpInterface = new HttpInterface(mockHttpServletRequest);

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
        new NonStrictExpectations() {{
            mockHttpServletRequest.getCookies();
            result = null;
        }};

        HttpInterface httpInterface = new HttpInterface(mockHttpServletRequest);

        assertThat(httpInterface.getCookies().size(), is(0));
    }

    private static class EmptyEnumeration<E> implements Enumeration<E> {
        public boolean hasMoreElements() { return false; }
        public E nextElement() { throw new NoSuchElementException(); }
    }
}

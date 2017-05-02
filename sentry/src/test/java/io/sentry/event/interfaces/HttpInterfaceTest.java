package io.sentry.event.interfaces;

import io.sentry.BaseTest;
import mockit.Injectable;
import mockit.NonStrictExpectations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class HttpInterfaceTest extends BaseTest {
    @Injectable
    private HttpServletRequest mockHttpServletRequest = null;
    @Injectable
    private Cookie mockCookie = null;

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
            result = Collections.emptyEnumeration();
            mockHttpServletRequest.getHeaders(anyString);
            result = Collections.emptyEnumeration();
        }};
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

        new NonStrictExpectations() {{
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
        }};

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
}

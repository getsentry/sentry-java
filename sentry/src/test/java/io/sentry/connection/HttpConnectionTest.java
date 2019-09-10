package io.sentry.connection;

import io.sentry.BaseTest;
import io.sentry.environment.SentryEnvironment;
import io.sentry.event.Event;
import io.sentry.marshaller.Marshaller;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Proxy;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class HttpConnectionTest extends BaseTest {
    private HttpConnection httpConnection = null;
    private HttpsURLConnection mockUrlConnection = null;
    private Marshaller mockMarshaller = null;

    @Before
    public void setUp() throws Exception {
        EventSampler eventSampler = mock(EventSampler.class);
        when(eventSampler.shouldSendEvent(any(Event.class))).thenReturn(true);

        mockMarshaller = mock(Marshaller.class);

        OutputStream mockOutputStream = mock(OutputStream.class);
        InputStream mockInputStream = mock(InputStream.class);

        mockUrlConnection = mock(HttpsURLConnection.class);
        when(mockUrlConnection.getOutputStream()).thenReturn(mockOutputStream);
        when(mockUrlConnection.getInputStream()).thenReturn(mockInputStream);

        URL mockUrl = new URL("sentry", "host", 80, "/some/file", new URLStreamHandler() {
            @Override
            protected URLConnection openConnection(URL u) throws IOException {
                return mockUrlConnection;
            }

            @Override
            protected URLConnection openConnection(URL u, Proxy p) throws IOException {
                return mockUrlConnection;
            }
        });
        Proxy proxy = Proxy.NO_PROXY;

        String secretKey = "e30cca23-3f97-470b-a8c2-e29b33dd25e0";
        String publicKey = "6cc48e8f-380c-44cc-986b-f566247a2af5";
        httpConnection = new HttpConnection(mockUrl, publicKey, secretKey, proxy, eventSampler);
        httpConnection.setMarshaller(mockMarshaller);
    }

    @Test
    public void testConnectionTimeout() throws Exception {
        final int timeout = 12;
        httpConnection.setConnectionTimeout(timeout);

        httpConnection.send(mock(Event.class));

        verify(mockUrlConnection).setConnectTimeout(eq(timeout));
    }

    @Test
    public void testReadTimeout() throws Exception {
        final int timeout = 42;
        httpConnection.setReadTimeout(timeout);

        httpConnection.send(mock(Event.class));

        verify(mockUrlConnection).setReadTimeout(eq(timeout));
    }


    @Test
    public void testByPassSecurityDefaultsToFalse() throws Exception {
        httpConnection.send(mock(Event.class));
        verify(mockUrlConnection, never()).setHostnameVerifier(any(HostnameVerifier.class));
    }

    @Test
    public void testByPassSecurity() throws Exception {
        httpConnection.setBypassSecurity(true);

        httpConnection.send(mock(Event.class));

        ArgumentCaptor<HostnameVerifier> captor = ArgumentCaptor.forClass(HostnameVerifier.class);
        verify(mockUrlConnection).setHostnameVerifier(captor.capture());
        assertThat(captor.getValue().verify("fakehostna.me", mock(SSLSession.class)), is(true));
    }

    @Test
    public void testDontByPassSecurity() throws Exception {
        httpConnection.setBypassSecurity(false);

        httpConnection.send(mock(Event.class));

        verify(mockUrlConnection, never()).setHostnameVerifier(any(HostnameVerifier.class));
    }

    @Test
    public void testContentMarshalled() throws Exception {
        Event mockEvent = mock(Event.class);
        httpConnection.send(mockEvent);
        verify(mockMarshaller).marshall(eq(mockEvent), any(OutputStream.class));
    }

    @Test
    public void testAuthHeaderSent() throws Exception {
        httpConnection.send(mock(Event.class));
        verify(mockUrlConnection).setRequestProperty(eq("User-Agent"), eq(SentryEnvironment.getSentryName()));
        verify(mockUrlConnection).setRequestProperty(eq("X-Sentry-Auth"), eq(httpConnection.getAuthHeader()));
    }

    @Test(expected = ConnectionException.class)
    public void testHttpErrorThrowsAnException() throws Exception {
        final String httpErrorMessage = "93e3ddb1-c4f3-46c3-9900-529de83678b7";
        when(mockUrlConnection.getOutputStream()).thenThrow(new IOException());
        when(mockUrlConnection.getErrorStream()).thenReturn(new ByteArrayInputStream(httpErrorMessage.getBytes()));

        httpConnection.doSend(mock(Event.class));
    }

    @Test
    public void testHttp403DoesntThrow() throws Exception {
        when(mockUrlConnection.getOutputStream()).thenThrow(new IOException());
        when(mockUrlConnection.getResponseCode()).thenReturn(403);

        httpConnection.doSend(mock(Event.class));
    }

    @Test
    public void testRetryAfterHeader() throws Exception {
        final String httpErrorMessage = "93e3ddb1-c4f3-46c3-9900-529de83678b7";
        when(mockUrlConnection.getOutputStream()).thenThrow(new IOException());
        when(mockUrlConnection.getErrorStream()).thenReturn(new ByteArrayInputStream(httpErrorMessage.getBytes()));
        when(mockUrlConnection.getHeaderField(eq("Retry-After"))).thenReturn("12345.25");

        try {
            httpConnection.doSend(mock(Event.class));
            fail();
        } catch (ConnectionException e) {
            assertThat(e.getRecommendedLockdownTime(), is(12345250L));
        }
    }

    @Test
    public void testApiUrlCreation() throws Exception {
        String uri = "http://host/sentry/";
        URI sentryUri = URI.create(uri);
        final String projectId = "293b4958-71f8-40a9-b588-96f004f64463";

        URL sentryApiUrl = HttpConnection.getSentryApiUrl(sentryUri, projectId);

        assertThat(sentryApiUrl.toString(), is(uri + "api/" + projectId + "/store/"));
    }

    @Test
    public void testEmptyStringDoesNotSIOOBE() throws Exception {
        when(mockUrlConnection.getOutputStream()).thenThrow(new IOException());
        when(mockUrlConnection.getErrorStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        try {
            httpConnection.doSend(mock(Event.class));
            fail("Should not exit normally with IOE");
        } catch (ConnectionException ce) {
            assertThat(ce.getMessage(), not(isEmptyOrNullString()));
        }
    }
}

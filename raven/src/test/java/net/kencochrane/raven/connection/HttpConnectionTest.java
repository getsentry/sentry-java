package net.kencochrane.raven.connection;

import net.kencochrane.raven.Raven;
import net.kencochrane.raven.dsn.Dsn;
import net.kencochrane.raven.event.Event;
import net.kencochrane.raven.event.EventBuilder;
import net.kencochrane.raven.marshaller.Marshaller;
import org.hamcrest.CustomTypeSafeMatcher;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.core.Is.is;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class HttpConnectionTest {
    private HttpConnection httpConnection;
    private String publicKey = UUID.randomUUID().toString();
    private String secretKey = UUID.randomUUID().toString();
    @Mock(answer = Answers.RETURNS_MOCKS)
    private HttpsURLConnection mockUrlConnection;
    @Mock
    private Marshaller mockMarshaller;

    @Before
    public void setUp() throws Exception {
        URLStreamHandler stubUrlHandler = new URLStreamHandler() {
            @Override
            protected URLConnection openConnection(URL u) throws IOException {
                return mockUrlConnection;
            }
        };

        URL mockUrl = new URL(null, "http://", stubUrlHandler);
        httpConnection = new HttpConnection(mockUrl, publicKey, secretKey);
        httpConnection.setMarshaller(mockMarshaller);
    }

    @Test
    public void testTimeout() throws Exception {
        int timeout = 12;
        httpConnection.setTimeout(timeout);
        httpConnection.send(new EventBuilder().build());

        verify(mockUrlConnection).setConnectTimeout(timeout);
    }

    @Test
    public void testByPassSecurity() throws Exception {
        ArgumentCaptor<HostnameVerifier> hostnameVerifierCaptor = ArgumentCaptor.forClass(HostnameVerifier.class);
        httpConnection.send(new EventBuilder().build());
        verify(mockUrlConnection, never()).setHostnameVerifier(any(HostnameVerifier.class));

        reset(mockUrlConnection);
        httpConnection.setBypassSecurity(true);
        httpConnection.send(new EventBuilder().build());
        verify(mockUrlConnection).setHostnameVerifier(hostnameVerifierCaptor.capture());
        assertThat(hostnameVerifierCaptor.getValue().verify(null, null), is(true));

        reset(mockUrlConnection);
        httpConnection.setBypassSecurity(false);
        httpConnection.send(new EventBuilder().build());
        verify(mockUrlConnection, never()).setHostnameVerifier(any(HostnameVerifier.class));
    }

    @Test
    public void testContentMarshalled() throws Exception {
        Event event = new EventBuilder().build();

        httpConnection.send(event);

        verify(mockMarshaller).marshall(eq(event), any(OutputStream.class));
    }

    @Test
    public void testAuthHeaderSent() throws Exception {
        httpConnection.send(new EventBuilder().build());

        verify(mockUrlConnection).setRequestProperty("User-Agent", Raven.NAME);
        String expectedAuthRequest = "Sentry sentry_version=4,"
                + "sentry_client=" + Raven.NAME + ","
                + "sentry_key=" + publicKey + ","
                + "sentry_secret=" + secretKey;
        verify(mockUrlConnection).setRequestProperty("X-Sentry-Auth", expectedAuthRequest);
    }

    //TODO: This test is ignored since the logs are sent through SLF4J rather than JUL.
    @Ignore
    @Test
    public void testHttpErrorLogged() throws Exception {
        final String httpErrorMessage = UUID.randomUUID().toString();
        ArgumentCaptor<LogRecord> logRecordCaptor = ArgumentCaptor.forClass(LogRecord.class);
        Handler handler = mock(Handler.class);
        Logger.getLogger(HttpConnection.class.getCanonicalName()).addHandler(handler);
        when(mockUrlConnection.getOutputStream()).thenThrow(new IOException());
        when(mockUrlConnection.getErrorStream()).thenReturn(new ByteArrayInputStream(httpErrorMessage.getBytes()));

        httpConnection.send(new EventBuilder().build());

        verify(handler).publish(logRecordCaptor.capture());
        assertThat(logRecordCaptor.getAllValues(),
                hasItem(new CustomTypeSafeMatcher<LogRecord>("Looks for message '" + httpErrorMessage + "'") {
                    @Override
                    protected boolean matchesSafely(LogRecord logRecord) {
                        return httpErrorMessage.equals(logRecord.getMessage());
                    }
                }));

        Logger.getLogger(HttpConnection.class.getCanonicalName()).removeHandler(handler);
    }

    @Test
    public void testApiUrlCreation() throws Exception {
        Dsn dsn = mock(Dsn.class);
        String projectId = UUID.randomUUID().toString();
        String uri = "http://host/sentry/";
        when(dsn.getUri()).thenReturn(new URI(uri));
        when(dsn.getProjectId()).thenReturn(projectId);

        URL sentryApiUrl = HttpConnection.getSentryApiUrl(dsn.getUri(), dsn.getProjectId());

        assertThat(sentryApiUrl.toString(), is(uri + "api/" + projectId + "/store/"));
    }
}

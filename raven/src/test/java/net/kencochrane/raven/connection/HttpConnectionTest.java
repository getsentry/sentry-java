package net.kencochrane.raven.connection;

import mockit.Expectations;
import mockit.Injectable;
import mockit.NonStrictExpectations;
import mockit.Verifications;
import net.kencochrane.raven.Raven;
import net.kencochrane.raven.event.Event;
import net.kencochrane.raven.marshaller.Marshaller;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class HttpConnectionTest {
    private final String publicKey = UUID.randomUUID().toString();
    private final String secretKey = UUID.randomUUID().toString();
    private HttpConnection httpConnection;
    @Injectable
    private HttpsURLConnection mockUrlConnection;
    @Injectable
    private Marshaller mockMarshaller;

    @BeforeMethod
    public void setUp() throws Exception {
        new NonStrictExpectations() {
            @Injectable
            private URL mockUrl;
            @Injectable
            private OutputStream mockOutputStream;
            @Injectable
            private InputStream mockInputStream;

            {
                mockUrl.openConnection();
                result = mockUrlConnection;
                mockUrlConnection.getOutputStream();
                result = mockOutputStream;
                mockUrlConnection.getInputStream();
                result = mockInputStream;

                httpConnection = new HttpConnection(mockUrl, publicKey, secretKey);
                httpConnection.setMarshaller(mockMarshaller);
            }
        };
    }

    @Test
    public void testTimeout(@Injectable final Event mockEvent) throws Exception {
        final int timeout = 12;
        httpConnection.setTimeout(timeout);

        httpConnection.send(mockEvent);

        new Verifications() {{
            mockUrlConnection.setConnectTimeout(timeout);
        }};
    }

    @Test
    public void testByPassSecurityDefaultsToFalse(@Injectable final Event mockEvent) throws Exception {
        httpConnection.send(mockEvent);

        new Verifications() {{
            mockUrlConnection.setHostnameVerifier((HostnameVerifier) any);
            times = 0;
        }};
    }

    @Test
    public void testByPassSecurity(@Injectable final Event mockEvent) throws Exception {
        httpConnection.setBypassSecurity(true);

        httpConnection.send(mockEvent);

        new Verifications() {
            @Injectable
            private String mockString;
            @Injectable
            private SSLSession mockSslSession;

            {
                HostnameVerifier hostnameVerifier;
                mockUrlConnection.setHostnameVerifier(hostnameVerifier = withCapture());
                assertThat(hostnameVerifier.verify(mockString, mockSslSession), is(true));
            }
        };
    }

    @Test
    public void testDontByPassSecurity(@Injectable final Event mockEvent) throws Exception {
        httpConnection.setBypassSecurity(false);

        httpConnection.send(mockEvent);

        new Verifications() {{
            mockUrlConnection.setHostnameVerifier((HostnameVerifier) any);
            times = 0;
        }};
    }

    @Test
    public void testContentMarshalled(@Injectable final Event mockEvent) throws Exception {
        httpConnection.send(mockEvent);

        new Verifications() {{
            mockMarshaller.marshall(mockEvent, (OutputStream) any);
        }};
    }

    @Test
    public void testAuthHeaderSent(@Injectable final Event mockEvent) throws Exception {
        httpConnection.send(mockEvent);


        new Verifications() {{
            mockUrlConnection.setRequestProperty("User-Agent", Raven.NAME);

            String expectedAuthRequest = "Sentry sentry_version=4,"
                    + "sentry_client=" + Raven.NAME + ","
                    + "sentry_key=" + publicKey + ","
                    + "sentry_secret=" + secretKey;
            mockUrlConnection.setRequestProperty("X-Sentry-Auth", expectedAuthRequest);
        }};
    }

    @Test(expectedExceptions = {ConnectionException.class})
    public void testHttpErrorThrowsAnException(@Injectable final Event mockEvent) throws Exception {
        final String httpErrorMessage = UUID.randomUUID().toString();
        new NonStrictExpectations() {{
            mockUrlConnection.getOutputStream();
            result = new IOException();
            mockUrlConnection.getErrorStream();
            result = new ByteArrayInputStream(httpErrorMessage.getBytes());
        }};

        httpConnection.doSend(mockEvent);
    }

    @Test
    public void testApiUrlCreation(@Injectable final URI sentryUri) throws Exception {
        final String uri = "http://host/sentry/";
        final String projectId = UUID.randomUUID().toString();
        new Expectations() {{
            sentryUri.toString();
            result = "http://host/sentry/";
        }};

        URL sentryApiUrl = HttpConnection.getSentryApiUrl(sentryUri, projectId);

        assertThat(sentryApiUrl.toString(), is(uri + "api/" + projectId + "/store/"));
    }
}

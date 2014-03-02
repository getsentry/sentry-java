package net.kencochrane.raven.connection;

import mockit.*;
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class HttpConnectionTest {
    @Injectable
    private final String publicKey = "6cc48e8f-380c-44cc-986b-f566247a2af5";
    @Injectable
    private final String secretKey = "e30cca23-3f97-470b-a8c2-e29b33dd25e0";
    @Tested
    private HttpConnection httpConnection;
    @Injectable
    private HttpsURLConnection mockUrlConnection;
    @Injectable
    private Marshaller mockMarshaller;
    @Injectable
    private URL mockUrl;
    @Injectable
    private OutputStream mockOutputStream;
    @Injectable
    private InputStream mockInputStream;

    @BeforeMethod
    public void setUp() throws Exception {
        httpConnection = null;
        new NonStrictExpectations() {{
            mockUrl.openConnection();
            result = mockUrlConnection;
            mockUrlConnection.getOutputStream();
            result = mockOutputStream;
            mockUrlConnection.getInputStream();
            result = mockInputStream;
        }};
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
    public void testByPassSecurity(@Injectable final Event mockEvent,
                                   @Injectable("fakehostna.me") final String mockHostname,
                                   @Injectable final SSLSession mockSslSession) throws Exception {
        httpConnection.setBypassSecurity(true);

        httpConnection.send(mockEvent);

        new Verifications() {{
            HostnameVerifier hostnameVerifier;
            mockUrlConnection.setHostnameVerifier(hostnameVerifier = withCapture());
            assertThat(hostnameVerifier.verify(mockHostname, mockSslSession), is(true));
        }};
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
            mockUrlConnection.setRequestProperty("X-Sentry-Auth", httpConnection.getAuthHeader());
        }};
    }

    @Test(expectedExceptions = {ConnectionException.class})
    public void testHttpErrorThrowsAnException(@Injectable final Event mockEvent) throws Exception {
        final String httpErrorMessage = "93e3ddb1-c4f3-46c3-9900-529de83678b7";
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
        final String projectId = "293b4958-71f8-40a9-b588-96f004f64463";
        new Expectations() {{
            sentryUri.toString();
            result = uri;
        }};

        URL sentryApiUrl = HttpConnection.getSentryApiUrl(sentryUri, projectId);

        assertThat(sentryApiUrl.toString(), is(uri + "api/" + projectId + "/store/"));
    }
}

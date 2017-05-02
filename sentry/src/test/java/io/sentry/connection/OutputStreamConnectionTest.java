package io.sentry.connection;

import io.sentry.BaseTest;
import mockit.*;
import io.sentry.environment.SentryEnvironment;
import io.sentry.event.Event;
import io.sentry.marshaller.Marshaller;
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

public class OutputStreamConnectionTest extends BaseTest {
    @Tested
    private OutputStreamConnection outputStreamConnection = null;
    @Injectable
    private Marshaller mockMarshaller = null;
    @Injectable
    private OutputStream mockOutputStream = null;

    @Test
    public void testContentMarshalled(@Injectable final Event mockEvent) throws Exception {
        outputStreamConnection.send(mockEvent);

        new Verifications() {{
            mockMarshaller.marshall(mockEvent, mockOutputStream);
        }};
    }

    @Test
    public void testClose() throws Exception {
        outputStreamConnection.close();

        new Verifications() {{
            mockOutputStream.close();
        }};
    }
}

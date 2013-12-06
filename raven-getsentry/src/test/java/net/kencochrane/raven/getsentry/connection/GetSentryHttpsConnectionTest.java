package net.kencochrane.raven.getsentry.connection;

import mockit.Deencapsulation;
import mockit.Injectable;
import mockit.Tested;
import org.testng.annotations.Test;

import javax.net.ssl.SSLHandshakeException;
import java.net.URL;
import java.net.URLConnection;

import static org.testng.Assert.fail;

public class GetSentryHttpsConnectionTest {
    @Tested
    private GetSentryHttpsConnection connection;
    @Injectable("projectId")
    private String projectId;
    @Injectable("privateKey")
    private String publicKey;
    @Injectable("secretKey")
    private String secretKey;

    @Test
    public void ensureHttpsConnectionToGetSentryIsSecure() throws Exception {
        connection.getConnection().connect();
    }

    @Test(expectedExceptions = SSLHandshakeException.class)
    public void ensureHttpsConnectionToGoogleComIsNotSecure() throws Exception {
        final URL url = new URL("https://www.google.com");
        Deencapsulation.setField(connection, "sentryUrl", url);

        connection.getConnection().connect();
    }

    /*
     * Test disabled as it will fail when the CA is available in the default KeyStore.
     * It is useful to be able to run it to ensure that it's indeed necessary to keep the entire module.
     */
    @Test(expectedExceptions = SSLHandshakeException.class, enabled = false)
    public void ensureHttpsConnectionToGetSentryRequiresCustomSslFactory() throws Exception {
        final URLConnection httpsConnection = GetSentryHttpsConnection.getSentryUrl(projectId).openConnection();

        httpsConnection.connect();

        fail("Ensure that the StartCom certificate hasn't been added manually to the KeyStore or skip this test.");
    }
}

package net.kencochrane.raven.connection;

import mockit.Injectable;
import mockit.NonStrictExpectations;
import mockit.Tested;
import mockit.Verifications;
import net.kencochrane.raven.Raven;
import net.kencochrane.raven.event.Event;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.concurrent.locks.ReentrantLock;

import static mockit.Deencapsulation.setField;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.verify;

public class AbstractConnectionTest {
    @Injectable
    private final String publicKey = "9bcf4a8c-f353-4f25-9dda-76a873fff905";
    @Injectable
    private final String secretKey = "56a9d05e-9032-4fdd-8f67-867d526422f9";
    @Tested
    private AbstractConnection abstractConnection;
    //Spying with mockito as jMockit doesn't support mocks of ReentrantLock
    @Spy
    private ReentrantLock reentrantLock = new ReentrantLock();

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        setField(Raven.class, "NAME", "Raven-Java/Test");
        // Reset tested
        abstractConnection = null;
    }

    @Test
    public void testAuthHeader() throws Exception {
        String authHeader = abstractConnection.getAuthHeader();

        assertThat(authHeader, is("Sentry sentry_version=5,"
                + "sentry_client=Raven-Java/Test,"
                + "sentry_key=" + publicKey + ","
                + "sentry_secret=" + secretKey));
    }

    @Test
    public void testSuccessfulSendCallsDoSend(@Injectable final Event mockEvent) throws Exception {
        setField(abstractConnection, "lock", reentrantLock);

        abstractConnection.send(mockEvent);

        new Verifications() {{
            abstractConnection.doSend(mockEvent);
        }};
    }

    @Test
    public void testExceptionOnSendStartLockDown(@Injectable final Event mockEvent) throws Exception {
        setField(abstractConnection, "lock", reentrantLock);
        new NonStrictExpectations() {{
            abstractConnection.doSend((Event) any);
            result = new ConnectionException();
        }};

        abstractConnection.send(mockEvent);

        verify(reentrantLock).tryLock();
        verify(reentrantLock).unlock();
    }
}

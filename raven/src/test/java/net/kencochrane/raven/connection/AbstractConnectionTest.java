package net.kencochrane.raven.connection;

import mockit.*;
import net.kencochrane.raven.event.Event;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

import static mockit.Deencapsulation.setField;
import static org.mockito.Mockito.verify;

public class AbstractConnectionTest {
    private final String publicKey = UUID.randomUUID().toString();
    private final String secretKey = UUID.randomUUID().toString();
    private AbstractConnection abstractConnection;
    //Spying with mockito as jMockit doesn't support mocks of ReentrantLock
    @Spy
    private ReentrantLock reentrantLock = new ReentrantLock();

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        abstractConnection = new DummyAbstractConnection(publicKey, secretKey);
        setField(abstractConnection, "lock", reentrantLock);
    }

    @Test
    public void testExceptionOnSendStartLockDown(@Injectable final Event mockEvent) throws Exception {
        new MockUp<DummyAbstractConnection>() {
            @SuppressWarnings("unused")
            @Mock
            protected void doSend(Event event) throws ConnectionException {
                throw new ConnectionException();
            }
        };

        abstractConnection.send(mockEvent);

        verify(reentrantLock).tryLock();
    }

    private static final class DummyAbstractConnection extends AbstractConnection {
        public DummyAbstractConnection(String publicKey, String secretKey) {
            super(publicKey, secretKey);
        }

        @Override
        protected void doSend(Event event) throws ConnectionException {
        }

        @Override
        public void close() throws IOException {
        }
    }
}

package net.kencochrane.raven.connection;

import mockit.*;
import net.kencochrane.raven.event.Event;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public class AsyncConnectionTest {
    private AsyncConnection asyncConnection;
    @Injectable
    private Connection mockConnection;
    @Injectable
    private ExecutorService mockExecutorService;
    @Mocked
    private Runtime mockRuntime;

    @BeforeMethod
    public void setUp() throws Exception {
        new NonStrictExpectations() {{
            Runtime.getRuntime();
            result = mockRuntime;
        }};
        asyncConnection = new AsyncConnection(mockConnection);
        asyncConnection.setExecutorService(mockExecutorService);
    }

    @Test
    public void verifyShutdownHookIsAdded() throws Exception {
        new AsyncConnection(mockConnection);

        new Verifications() {{
            mockRuntime.addShutdownHook((Thread) any);
        }};
    }

    @Test
    public void verifyShutdownHookClosesConnection() throws Exception {
        new NonStrictExpectations() {{
            mockRuntime.addShutdownHook((Thread) any);
            result = new Delegate<Void>() {
                public void addShutdownHook(Thread thread) {
                    thread.run();
                }
            };
        }};

        new AsyncConnection(mockConnection);

        new Verifications() {{
            mockConnection.close();
        }};
    }

    @Test
    public void testCloseOperation() throws Exception {
        asyncConnection.close();

        new Verifications() {{
            mockConnection.close();
            mockExecutorService.awaitTermination(anyLong, (TimeUnit) any);
        }};
    }

    @Test
    public void testSendEventQueued(@Injectable final Event mockEvent) throws Exception {
        asyncConnection.send(mockEvent);

        new Verifications() {{
            mockExecutorService.execute((Runnable) any);
        }};
    }

    @Test
    public void testQueuedEventExecuted(@Injectable final Event mockEvent) throws Exception {
        new Expectations() {{
            mockExecutorService.execute((Runnable) any);
            result = new Delegate() {
                public void execute(Runnable runnable) {
                    runnable.run();
                }
            };
        }};

        asyncConnection.send(mockEvent);

        new Verifications() {{
            mockConnection.send(mockEvent);
        }};
    }
}

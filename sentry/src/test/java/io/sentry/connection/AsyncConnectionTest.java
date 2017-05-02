package io.sentry.connection;

import io.sentry.BaseTest;
import io.sentry.SentryClient;
import mockit.*;
import io.sentry.environment.SentryEnvironment;
import io.sentry.event.Event;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public class AsyncConnectionTest extends BaseTest {
    @Tested
    private AsyncConnection asyncConnection = null;
    @Injectable
    private Connection mockConnection = null;
    @Injectable
    private ExecutorService mockExecutorService = null;
    @Injectable("false")
    private boolean mockGracefulShutdown = false;
    @Injectable
    private long mockTimeout = 10000L;
    @SuppressWarnings("unused")
    @Mocked("addShutdownHook")
    private Runtime mockRuntime = null;

    @BeforeMethod
    public void setUp() throws Exception {
        new NonStrictExpectations() {{
            mockExecutorService.awaitTermination(anyLong, (TimeUnit) any);
            result = true;
        }};
    }

    @Test
    public void verifyShutdownHookIsAddedWhenGraceful() throws Exception {
        // Ensure that the shutdown hooks for the unused @Tested instance are removed
        asyncConnection.close();

        new AsyncConnection(mockConnection, mockExecutorService, true, mockTimeout);

        new Verifications() {{
            mockRuntime.addShutdownHook((Thread) any);
        }};
    }

    @Test
    public void verifyShutdownHookNotAddedWhenNotGraceful() throws Exception {
        // Ensure that the shutdown hooks for the unused @Tested instance are removed
        asyncConnection.close();

        new AsyncConnection(mockConnection, mockExecutorService, false, mockTimeout);

        new Verifications() {{
            mockRuntime.addShutdownHook((Thread) any);
            times = 0;
        }};
    }

    @Test
    public void verifyShutdownHookSetManagedBySentryAndCloseConnection(
            @SuppressWarnings("unused") @Mocked({"startManagingThread", "stopManagingThread"}) SentryClient mockSentryClient)
            throws Exception {
        // Ensure that the shutdown hooks for the unused @Tested instance are removed
        asyncConnection.close();

        new NonStrictExpectations() {{
            mockRuntime.addShutdownHook((Thread) any);
            result = new Delegate<Void>() {
                @SuppressWarnings("unused")
                public void addShutdownHook(Thread hook) {
                    hook.run();
                }
            };
        }};

        new AsyncConnection(mockConnection, mockExecutorService, true, mockTimeout);

        new VerificationsInOrder() {{
            SentryEnvironment.startManagingThread();
            mockConnection.close();
            SentryEnvironment.stopManagingThread();
        }};
    }

    @Test
    public void ensureFailingShutdownHookStopsBeingManaged(
            @SuppressWarnings("unused") @Mocked({"startManagingThread", "stopManagingThread"}) SentryClient mockSentryClient)
            throws Exception {
        // Ensure that the shutdown hooks for the unused @Tested instance are removed
        asyncConnection.close();

        new NonStrictExpectations() {{
            mockRuntime.addShutdownHook((Thread) any);
            result = new Delegate<Void>() {
                @SuppressWarnings("unused")
                public void addShutdownHook(Thread hook) {
                    hook.run();
                }
            };
            mockConnection.close();
            result = new RuntimeException("Close operation failed");
        }};

        new AsyncConnection(mockConnection, mockExecutorService, true, mockTimeout);

        new Verifications() {{
            SentryEnvironment.stopManagingThread();
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

        // Ensure that the shutdown hooks for the used @Tested instance are removed
        asyncConnection.close();
    }

    @Test
    public void testQueuedEventExecuted(@Injectable final Event mockEvent) throws Exception {
        new NonStrictExpectations() {{
            mockExecutorService.execute((Runnable) any);
            result = new Delegate<Void>() {
                @SuppressWarnings("unused")
                public void execute(Runnable command) {
                    command.run();
                }
            };
        }};

        asyncConnection.send(mockEvent);

        new Verifications() {{
            mockConnection.send(mockEvent);
        }};

        // Ensure that the shutdown hooks for the used @Tested instance are removed
        asyncConnection.close();
    }
}

package net.kencochrane.raven.connection;

import mockit.*;
import net.kencochrane.raven.Raven;
import net.kencochrane.raven.event.Event;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public class AsyncConnectionTest {
    @Tested
    private AsyncConnection asyncConnection = null;
    @Injectable
    private Connection mockConnection = null;
    @Injectable
    private ExecutorService mockExecutorService = null;
    @Injectable("false")
    private boolean mockGracefulShutdown = false;
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

        new AsyncConnection(mockConnection, mockExecutorService, true);

        new Verifications() {{
            mockRuntime.addShutdownHook((Thread) any);
        }};
    }

    @Test
    public void verifyShutdownHookNotAddedWhenNotGraceful() throws Exception {
        // Ensure that the shutdown hooks for the unused @Tested instance are removed
        asyncConnection.close();

        new AsyncConnection(mockConnection, mockExecutorService, false);

        new Verifications() {{
            mockRuntime.addShutdownHook((Thread) any);
            times = 0;
        }};
    }

    @Test
    public void verifyShutdownHookSetManagedByRavenAndCloseConnection(
            @SuppressWarnings("unused") @Mocked({"startManagingThread", "stopManagingThread"}) Raven mockRaven)
            throws Exception {
        // Ensure that the shutdown hooks for the unused @Tested instance are removed
        asyncConnection.close();

        new NonStrictExpectations() {{
            mockRuntime.addShutdownHook((Thread) any);
            result = new Delegate<Void>() {
                public void addShutdownHook(Thread thread) {
                    thread.run();
                }
            };
        }};

        new AsyncConnection(mockConnection, mockExecutorService, true);

        new VerificationsInOrder() {{
            Raven.startManagingThread();
            mockConnection.close();
            Raven.stopManagingThread();
        }};
    }

    @Test
    public void ensureFailingShutdownHookStopsBeingManaged(
            @SuppressWarnings("unused") @Mocked({"startManagingThread", "stopManagingThread"}) Raven mockRaven)
            throws Exception {
        // Ensure that the shutdown hooks for the unused @Tested instance are removed
        asyncConnection.close();

        new NonStrictExpectations() {{
            mockRuntime.addShutdownHook((Thread) any);
            result = new Delegate<Void>() {
                public void addShutdownHook(Thread thread) {
                    thread.run();
                }
            };
            mockConnection.close();
            result = new RuntimeException("Close operation failed");
        }};

        new AsyncConnection(mockConnection, mockExecutorService, true);

        new Verifications() {{
            Raven.stopManagingThread();
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

        // Ensure that the shutdown hooks for the used @Tested instance are removed
        asyncConnection.close();
    }
}

package io.sentry.connection;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import io.sentry.BaseJUnitTest;
import io.sentry.environment.SentryEnvironment;
import io.sentry.event.Event;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class AsyncConnectionTest extends BaseJUnitTest {
    private AsyncConnection asyncConnection = null;
    private Connection mockConnection = null;
    private ExecutorService mockExecutorService = null;
    private long mockTimeout = 10000L;

    @Before
    public void setUp() throws Exception {
        mockExecutorService = mock(ExecutorService.class);
        when(mockExecutorService.awaitTermination(anyLong(), any(TimeUnit.class))).thenReturn(true);

        mockConnection = mock(Connection.class);

        asyncConnection = mock(AsyncConnection.class, withSettings()
                .useConstructor(mockConnection, mockExecutorService, false, mockTimeout)
                .defaultAnswer(CALLS_REAL_METHODS));
    }

    @Test
    public void verifyShutdownHookIsAddedWhenGraceful() throws Exception {
        AsyncConnection conn = new AsyncConnection(mockConnection, mockExecutorService, true, mockTimeout);

        boolean registered = Runtime.getRuntime().removeShutdownHook(conn.shutDownHook);

        assertTrue(registered);
    }

    @Test
    public void verifyShutdownHookNotAddedWhenNotGraceful() throws Exception {
        AsyncConnection conn = new AsyncConnection(mockConnection, mockExecutorService, false, mockTimeout);

        boolean registered = Runtime.getRuntime().removeShutdownHook(conn.shutDownHook);

        assertFalse(registered);
    }

    @Test
    public void verifyShutdownHookSetManagedBySentryAndCloseConnection()
            throws Exception {
        //instantiate a new async connection installing the shutdown hook
        AsyncConnection conn = new AsyncConnection(mockConnection, mockExecutorService, true, mockTimeout);

        assertFalse(SentryEnvironment.isManagingThread());

        // the shutdown hook should start managing the thread and call close on the connection
        // we take advantage of that and we check below that during the call to mockConnection.close()
        // the thread is indeed managed.
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                assertTrue(SentryEnvironment.isManagingThread());
                return null;
            }
        }).when(mockConnection).close();

        // this simulates the running of the shutdown hook
        //noinspection CallToThreadRun
        ((Thread) conn.shutDownHook).run();;

        verify(mockConnection).close();

        // the thread should no longer be managed after the shutdown hook ran
        assertFalse(SentryEnvironment.isManagingThread());
    }

    @Test
    public void ensureFailingShutdownHookStopsBeingManaged()
            throws Exception {
        //instantiate a new async connection installing the shutdown hook
        AsyncConnection conn = new AsyncConnection(mockConnection, mockExecutorService, true, mockTimeout);

        assertFalse(SentryEnvironment.isManagingThread());

        // the shutdown hook should start managing the thread and call close on the connection
        // we take advantage of that and we check below that during the call to mockConnection.close()
        // the thread is indeed managed. The close() operation then fails.
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                assertTrue(SentryEnvironment.isManagingThread());
                throw new RuntimeException("Close operation failed.");
            }
        }).when(mockConnection).close();

        // this simulates the running of the shutdown hook
        //noinspection CallToThreadRun
        ((Thread) conn.shutDownHook).run();;

        verify(mockConnection).close();

        // the thread should no longer be managed after the shutdown hook ran even if the close() failed.
        assertFalse(SentryEnvironment.isManagingThread());
    }

    @Test
    public void testCloseOperation() throws Exception {
        asyncConnection.close();

        verify(mockConnection).close();
        verify(mockExecutorService).awaitTermination(anyLong(), any(TimeUnit.class));
    }

    @Test
    public void testSendEventQueued() throws Exception {
        asyncConnection.send(mock(Event.class));

        verify(mockExecutorService).execute(any(Runnable.class));
    }

    @Test
    public void testQueuedEventExecuted() throws Exception {
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                ((Runnable) invocation.getArgument(0)).run();
                return null;
            }
        }).when(mockExecutorService).execute(any(Runnable.class));

        Event ev = mock(Event.class);

        asyncConnection.send(ev);

        verify(mockConnection).send(eq(ev));
    }
}

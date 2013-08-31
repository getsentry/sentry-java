package net.kencochrane.raven.connection;

import mockit.*;
import net.kencochrane.raven.event.Event;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public class AsyncConnectionTest {
    @Tested
    private AsyncConnection asyncConnection;
    @Injectable
    private Connection mockConnection;
    @Injectable
    private ExecutorService mockExecutorService;

    @BeforeMethod
    public void setUp() throws Exception {
        asyncConnection = new AsyncConnection(mockConnection);
        asyncConnection.setExecutorService(mockExecutorService);
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

        new Verifications(){{
            mockExecutorService.execute((Runnable) any);
        }};
    }

    @Test
    public void testQueuedEventExecuted(@Injectable final Event mockEvent) throws Exception {
        new Expectations(){{
            mockExecutorService.execute((Runnable) any);
            result = new Delegate() {
                public void execute(Runnable runnable){
                    runnable.run();
                }
            };
        }};

        asyncConnection.send(mockEvent);

        new Verifications(){{
            mockConnection.send(mockEvent);
        }};
    }
}

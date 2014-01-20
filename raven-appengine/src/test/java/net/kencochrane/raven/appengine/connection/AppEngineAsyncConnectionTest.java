package net.kencochrane.raven.appengine.connection;

import com.google.appengine.api.taskqueue.*;
import com.google.appengine.api.taskqueue.Queue;
import mockit.*;
import net.kencochrane.raven.connection.Connection;
import net.kencochrane.raven.event.Event;
import org.hamcrest.Matchers;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class AppEngineAsyncConnectionTest {
    private AppEngineAsyncConnection asyncConnection;
    @Injectable
    private Connection mockConnection;
    @Injectable
    private Queue mockQueue;
    @Mocked(methods = "getDefaultQueue")
    private QueueFactory queueFactory;

    private static DeferredTask extractDeferredTask(TaskOptions taskOptions) throws Exception {
        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(taskOptions.getPayload()));
        return (DeferredTask) ois.readObject();
    }

    private static AppEngineAsyncConnection getTaskConnection(DeferredTask deferredTask) throws Exception {
        Map<UUID, AppEngineAsyncConnection> appEngineAsyncConnectionRegister
                = Deencapsulation.getField(AppEngineAsyncConnection.class, "APP_ENGINE_ASYNC_CONNECTIONS");
        return appEngineAsyncConnectionRegister.get(Deencapsulation.<UUID>getField(deferredTask, "connectionId"));
    }

    @BeforeMethod
    public void setUp() throws Exception {
        new NonStrictExpectations() {{
            QueueFactory.getDefaultQueue();
            result = mockQueue;
        }};
        asyncConnection = new AppEngineAsyncConnection(mockConnection);
    }

    @Test
    public void testRegisterNewInstance(@Injectable final UUID mockUuid, @Mocked("randomUUID") UUID uuid)
            throws Exception {
        new NonStrictExpectations() {{
            UUID.randomUUID();
            result = mockUuid;
        }};

        AppEngineAsyncConnection asyncConnection2 = new AppEngineAsyncConnection(mockConnection);

        Map<UUID, AppEngineAsyncConnection> appEngineAsyncConnectionRegister
                = Deencapsulation.getField(AppEngineAsyncConnection.class, "APP_ENGINE_ASYNC_CONNECTIONS");
        assertThat(appEngineAsyncConnectionRegister, hasEntry(mockUuid, asyncConnection2));
    }

    @Test
    public void testUnregisterInstance(@Injectable final UUID mockUuid, @Mocked("randomUUID") UUID uuid)
            throws Exception {
        new NonStrictExpectations() {{
            UUID.randomUUID();
            result = mockUuid;
        }};

        new AppEngineAsyncConnection(mockConnection).close();

        Map<UUID, AppEngineAsyncConnection> appEngineAsyncConnectionRegister
                = Deencapsulation.getField(AppEngineAsyncConnection.class, "APP_ENGINE_ASYNC_CONNECTIONS");
        assertThat(appEngineAsyncConnectionRegister, not(hasKey(mockUuid)));
    }

    @Test
    public void testCloseOperation(@Injectable final List<TaskHandle> mockTaskHandleList) throws Exception {
        new NonStrictExpectations() {{
            mockQueue.leaseTasksByTag(anyLong, (TimeUnit) any, anyLong, "RavenTask");
            result = mockTaskHandleList;
            result = Collections.emptyList();
            mockTaskHandleList.isEmpty();
            result = false;
        }};

        asyncConnection.close();

        new Verifications() {{
            mockConnection.close();
            mockQueue.leaseTasksByTag(anyLong, (TimeUnit) any, anyLong, "RavenTask");
            times = 2;
            mockQueue.deleteTask(mockTaskHandleList);
        }};
    }

    @Test
    public void testSendEventQueued(@Injectable final Event mockEvent) throws Exception {
        new NonStrictExpectations() {{
            setField(mockEvent, "id", UUID.randomUUID());
        }};

        asyncConnection.send(mockEvent);

        new Verifications() {{
            TaskOptions taskOptions;
            DeferredTask deferredTask;
            mockQueue.add(taskOptions = withCapture());

            assertThat(taskOptions.getTag(), is("RavenTask"));
            deferredTask = extractDeferredTask(taskOptions);
            assertThat(getField(deferredTask, "event"), Matchers.<Object>equalTo(mockEvent));
        }};
    }

    @Test
    public void testQueuedEventSubmitted(@Injectable final Event mockEvent,
                                         @Mocked("setDoNotRetry") DeferredTaskContext deferredTaskContext)
            throws Exception {
        new NonStrictExpectations() {{
            mockQueue.add((TaskOptions) any);
            result = new Delegate<Void>() {
                void add(TaskOptions taskOptions) throws Exception {
                    extractDeferredTask(taskOptions).run();
                }
            };
        }};

        asyncConnection.send(mockEvent);

        new Verifications() {{
            DeferredTaskContext.setDoNotRetry(true);
            mockConnection.send((Event) any);
        }};
    }

    @Test
    public void testEventLinkedToCorrectConnection(@Injectable final Event mockEvent) throws Exception {
        final AppEngineAsyncConnection asyncConnection2 = new AppEngineAsyncConnection(mockConnection);

        asyncConnection.send(mockEvent);
        asyncConnection2.send(mockEvent);

        new Verifications() {{
            List<TaskOptions> taskOptionsList = new ArrayList<TaskOptions>();
            DeferredTask deferredTask;

            mockQueue.add(withCapture(taskOptionsList));

            deferredTask = extractDeferredTask(taskOptionsList.get(0));
            assertThat(getTaskConnection(deferredTask), is(asyncConnection));

            deferredTask = extractDeferredTask(taskOptionsList.get(1));
            assertThat(getTaskConnection(deferredTask), is(asyncConnection2));
        }};
    }
}

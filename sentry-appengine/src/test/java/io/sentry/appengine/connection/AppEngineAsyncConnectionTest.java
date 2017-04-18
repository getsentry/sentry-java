package io.sentry.appengine.connection;

import com.google.appengine.api.taskqueue.*;
import mockit.*;
import io.sentry.connection.Connection;
import io.sentry.event.Event;
import io.sentry.event.EventBuilder;
import org.hamcrest.Matchers;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static mockit.Deencapsulation.getField;
import static mockit.Deencapsulation.setField;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class AppEngineAsyncConnectionTest {
    @Tested
    private AppEngineAsyncConnection asyncConnection = null;
    @Injectable
    private Connection mockConnection = null;
    @Injectable
    private Queue mockQueue = null;
    @SuppressWarnings("unused")
    @Mocked("getDefaultQueue")
    private QueueFactory queueFactory = null;
    @Injectable("7b55a129-6975-4434-8edc-29ceefd38c95")
    private String mockConnectionId = null;

    private static DeferredTask extractDeferredTask(TaskOptions taskOptions) throws Exception {
        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(taskOptions.getPayload()));
        return (DeferredTask) ois.readObject();
    }

    private static AppEngineAsyncConnection getTaskConnection(DeferredTask deferredTask) throws Exception {
        Map<UUID, AppEngineAsyncConnection> appEngineAsyncConnectionRegister
                = getField(AppEngineAsyncConnection.class, "APP_ENGINE_ASYNC_CONNECTIONS");
        return appEngineAsyncConnectionRegister.get(Deencapsulation.<UUID>getField(deferredTask, "connectionId"));
    }

    @BeforeMethod
    public void setUp() throws Exception {
        asyncConnection = new AppEngineAsyncConnection(mockConnectionId, mockConnection);
        new NonStrictExpectations() {{
            QueueFactory.getDefaultQueue();
            result = mockQueue;
        }};
    }

    @Test
    public void testRegisterNewInstance(
            @Injectable("1bac02f7-c9ed-41b8-9126-e2da257a06ef") final String mockConnectionId) throws Exception {
        AppEngineAsyncConnection asyncConnection2 = new AppEngineAsyncConnection(mockConnectionId, mockConnection);

        Map<String, AppEngineAsyncConnection> appEngineAsyncConnectionRegister
                = getField(AppEngineAsyncConnection.class, "APP_ENGINE_ASYNC_CONNECTIONS");
        assertThat(appEngineAsyncConnectionRegister, hasEntry(mockConnectionId, asyncConnection2));
    }

    @Test
    public void testUnregisterInstance(
            @Injectable("648f76e2-39ed-40e0-91a2-b1887a03b782") final String mockConnectionId) throws Exception {
        new AppEngineAsyncConnection(mockConnectionId, mockConnection).close();

        Map<String, AppEngineAsyncConnection> appEngineAsyncConnectionRegister
                = getField(AppEngineAsyncConnection.class, "APP_ENGINE_ASYNC_CONNECTIONS");
        assertThat(appEngineAsyncConnectionRegister, not(hasKey(mockConnectionId)));
    }

    @Test
    public void testSendEventQueued() throws Exception {
        final Event event = new EventBuilder().build();

        asyncConnection.send(event);

        new Verifications() {{
            TaskOptions taskOptions;
            DeferredTask deferredTask;
            mockQueue.add(taskOptions = withCapture());

            deferredTask = extractDeferredTask(taskOptions);
            assertThat(getField(deferredTask, "event"), Matchers.<Object>equalTo(event));
        }};
    }

    @Test
    public void testQueuedEventSubmitted(@SuppressWarnings("unused")
                                         @Mocked("setDoNotRetry") DeferredTaskContext deferredTaskContext)
            throws Exception {
        final Event event = new EventBuilder().build();
        new NonStrictExpectations() {{
            mockQueue.add((TaskOptions) any);
            result = new Delegate<TaskHandle>() {
                @SuppressWarnings("unused")
                TaskHandle add(TaskOptions taskOptions) {
                    try {
                        extractDeferredTask(taskOptions).run();
                    } catch (Exception e) {
                        throw new RuntimeException("Couldn't extract the task", e);
                    }
                    return null;
                }
            };
        }};

        asyncConnection.send(event);

        new Verifications() {{
            DeferredTaskContext.setDoNotRetry(true);
            mockConnection.send((Event) any);
        }};
    }

    @Test
    public void testEventLinkedToCorrectConnection(
            @Injectable("eb37bfe4-7316-47e8-94e4-073aefd0fbf8") final String mockConnectionId) throws Exception {
        final AppEngineAsyncConnection asyncConnection2 = new AppEngineAsyncConnection(mockConnectionId, mockConnection);
        final Event event = new EventBuilder().build();

        asyncConnection.send(event);
        asyncConnection2.send(event);

        new Verifications() {{
            List<TaskOptions> taskOptionsList = new ArrayList<>();
            DeferredTask deferredTask;

            mockQueue.add(withCapture(taskOptionsList));

            deferredTask = extractDeferredTask(taskOptionsList.get(0));
            assertThat(getTaskConnection(deferredTask), is(asyncConnection));

            deferredTask = extractDeferredTask(taskOptionsList.get(1));
            assertThat(getTaskConnection(deferredTask), is(asyncConnection2));
        }};
    }
}

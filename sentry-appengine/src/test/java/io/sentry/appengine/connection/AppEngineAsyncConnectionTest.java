package io.sentry.appengine.connection;

import com.google.appengine.api.taskqueue.*;
import com.google.apphosting.api.ApiProxy;
import io.sentry.appengine.TestQueueFactoryProvider;
import io.sentry.connection.Connection;
import io.sentry.event.Event;
import io.sentry.event.EventBuilder;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AppEngineAsyncConnectionTest {
    private AppEngineAsyncConnection asyncConnection = null;
    private Connection mockConnection = null;
    private Queue mockQueue = null;
    private String mockConnectionId = null;

    private static DeferredTask extractDeferredTask(TaskOptions taskOptions) throws Exception {
        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(taskOptions.getPayload()));
        return (DeferredTask) ois.readObject();
    }

    private static AppEngineAsyncConnection getTaskConnection(DeferredTask deferredTask) throws Exception {
        AppEngineAsyncConnection.EventSubmitter submitter = (AppEngineAsyncConnection.EventSubmitter)  deferredTask;
        String connectionId = submitter.getConnectionId();
        return AppEngineAsyncConnection.APP_ENGINE_ASYNC_CONNECTIONS.get(connectionId);
    }

    @Before
    public void setUp() throws Exception {
        TestQueueFactoryProvider.reset();
        mockQueue = mock(Queue.class);
        TestQueueFactoryProvider.registerQueue(getClass().getSimpleName(), mockQueue);

        mockConnection = mock(Connection.class);
        mockConnectionId = "7b55a129-6975-4434-8edc-29ceefd38c95";
        asyncConnection = new AppEngineAsyncConnection(mockConnectionId, mockConnection);
        asyncConnection.setQueue(getClass().getSimpleName());

        Map<String, Object> attrs = new HashMap<>();
        ApiProxy.Environment env = mock(ApiProxy.Environment.class);
        when(env.getAttributes()).thenReturn(attrs);

        ApiProxy.setEnvironmentForCurrentThread(env);
    }

    @After
    public void tearDown() {
        ApiProxy.setEnvironmentForCurrentThread(null);
    }

    @Test
    public void testRegisterNewInstance() throws Exception {
        String mockConnectionId = "1bac02f7-c9ed-41b8-9126-e2da257a06ef";
        AppEngineAsyncConnection asyncConnection2 = new AppEngineAsyncConnection(mockConnectionId, mockConnection);

        Map<String, AppEngineAsyncConnection> appEngineAsyncConnectionRegister =
                AppEngineAsyncConnection.APP_ENGINE_ASYNC_CONNECTIONS;
        assertThat(appEngineAsyncConnectionRegister, hasEntry(mockConnectionId, asyncConnection2));
    }

    @Test
    public void testUnregisterInstance() throws Exception {
        String mockConnectionId = "648f76e2-39ed-40e0-91a2-b1887a03b782";
        new AppEngineAsyncConnection(mockConnectionId, mockConnection).close();

        Map<String, AppEngineAsyncConnection> appEngineAsyncConnectionRegister =
                AppEngineAsyncConnection.APP_ENGINE_ASYNC_CONNECTIONS;
        assertThat(appEngineAsyncConnectionRegister, not(hasKey(mockConnectionId)));
    }

    @Test
    public void testSendEventQueued() throws Exception {
        final Event event = new EventBuilder().build();

        asyncConnection.send(event);

        ArgumentCaptor<TaskOptions> taskOptionsArgumentCaptor = ArgumentCaptor.forClass(TaskOptions.class);
        verify(mockQueue).add(taskOptionsArgumentCaptor.capture());
        TaskOptions taskOptions = taskOptionsArgumentCaptor.getValue();
        DeferredTask deferredTask = extractDeferredTask(taskOptions);
        AppEngineAsyncConnection.EventSubmitter eventSubmitter = (AppEngineAsyncConnection.EventSubmitter) deferredTask;
        assertThat(eventSubmitter.getEvent(), Matchers.<Object>equalTo(event));
    }

    @Test
    public void testQueuedEventSubmitted() throws Exception {
        final Event event = new EventBuilder().build();
        when(mockQueue.add(any(TaskOptions.class))).thenAnswer(new Answer<TaskHandle>() {
            @Override
            public TaskHandle answer(InvocationOnMock invocation) throws Throwable {
                TaskOptions taskOptions = invocation.getArgument(0);
                try {
                    extractDeferredTask(taskOptions).run();
                } catch (RuntimeException e) {
                    throw new RuntimeException("Couldn't extract the task", e);
                }
                return null;
            }
        });

        asyncConnection.send(event);

        verify(mockConnection).send(any(Event.class));

        // the below verifies that we called DeferredTask.setDoNotRetry(true)
        ApiProxy.Environment env = ApiProxy.getCurrentEnvironment();
        Map<String, Object> attrs = env.getAttributes();
        String key = String.valueOf(DeferredTaskContext.class.getName()).concat(".doNotRetry");
        assertThat(attrs.get(key), is((Object) true));
    }

    @Test
    public void testEventLinkedToCorrectConnection() throws Exception {
        final AppEngineAsyncConnection asyncConnection2 =
                new AppEngineAsyncConnection("eb37bfe4-7316-47e8-94e4-073aefd0fbf8", mockConnection);
        asyncConnection2.setQueue(getClass().getSimpleName());

        final Event event = new EventBuilder().build();

        asyncConnection.send(event);
        asyncConnection2.send(event);

        ArgumentCaptor<TaskOptions> taskOptionsCaptor = ArgumentCaptor.forClass(TaskOptions.class);
        verify(mockQueue, times(2)).add(taskOptionsCaptor.capture());

        DeferredTask deferredTask = extractDeferredTask(taskOptionsCaptor.getAllValues().get(0));
        assertThat(getTaskConnection(deferredTask), is(asyncConnection));

        deferredTask = extractDeferredTask(taskOptionsCaptor.getAllValues().get(1));
        assertThat(getTaskConnection(deferredTask), is(asyncConnection2));
    }
}

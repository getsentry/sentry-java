package io.sentry.event;

import io.sentry.BaseTest;
import io.sentry.SentryClient;
import io.sentry.connection.Connection;
import io.sentry.context.ContextManager;
import io.sentry.context.SingletonContextManager;
import io.sentry.event.helper.ContextBuilderHelper;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class BreadcrumbTest extends BaseTest {
    private SentryClient sentryClient = null;
    private Connection mockConnection = null;
    private ContextManager contextManager = new SingletonContextManager();

    @Before
    public void setup() {
        contextManager.clear();
        mockConnection = mock(Connection.class);
        sentryClient = new SentryClient(mockConnection, contextManager);
    }

    @Test
    public void testBreadcrumbsViaContextRecording() {
        sentryClient.addBuilderHelper(new ContextBuilderHelper(sentryClient));

        final Breadcrumb breadcrumb = new BreadcrumbBuilder()
            .setLevel(Breadcrumb.Level.INFO)
            .setMessage("message")
            .setCategory("step")
            .build();

        sentryClient.getContext().recordBreadcrumb(breadcrumb);

        sentryClient.sendEvent(new EventBuilder()
            .withMessage("Some random message")
            .withLevel(Event.Level.INFO));

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(mockConnection).send(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getBreadcrumbs().size(), equalTo(1));
    }

    @Test
    public void testBreadcrumbsViaEventBuilder() {
        sentryClient.addBuilderHelper(new ContextBuilderHelper(sentryClient));

        final Breadcrumb breadcrumb = new BreadcrumbBuilder()
            .setLevel(Breadcrumb.Level.INFO)
            .setMessage("message")
            .setCategory("step")
            .build();

        ArrayList<Breadcrumb> breadcrumbs = new ArrayList<>();
        breadcrumbs.add(breadcrumb);

        sentryClient.sendEvent(new EventBuilder()
            .withBreadcrumbs(breadcrumbs)
            .withMessage("Some random message")
            .withLevel(Event.Level.INFO));

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(mockConnection).send(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getBreadcrumbs().size(), equalTo(1));
    }

    @Test
    public void testBreadcrumbsViaEvent() {
        sentryClient.addBuilderHelper(new ContextBuilderHelper(sentryClient));

        final Breadcrumb breadcrumb = new BreadcrumbBuilder()
            .setLevel(Breadcrumb.Level.INFO)
            .setMessage("message")
            .setCategory("step")
            .build();

        ArrayList<Breadcrumb> breadcrumbs = new ArrayList<>();
        breadcrumbs.add(breadcrumb);

        sentryClient.sendEvent(new EventBuilder()
            .withBreadcrumbs(breadcrumbs)
            .withMessage("Some random message")
            .withLevel(Event.Level.INFO)
            .build());

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(mockConnection).send(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getBreadcrumbs().size(), equalTo(1));
    }
}

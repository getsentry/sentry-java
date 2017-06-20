package io.sentry.event;

import io.sentry.BaseTest;
import io.sentry.SentryClient;
import io.sentry.connection.Connection;
import io.sentry.context.ContextManager;
import io.sentry.context.SingletonContextManager;
import io.sentry.event.helper.ContextBuilderHelper;
import mockit.Injectable;
import mockit.Tested;
import mockit.Verifications;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;

public class BreadcrumbTest extends BaseTest {
    @Tested
    private SentryClient sentryClient = null;

    @Injectable
    private Connection mockConnection = null;
    @Injectable
    private ContextManager contextManager = new SingletonContextManager();

    @BeforeMethod
    public void setup() {
        contextManager.clear();
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

        new Verifications() {{
            Event event;
            mockConnection.send(event = withCapture());
            assertThat(event.getBreadcrumbs().size(), equalTo(1));
        }};
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

        new Verifications() {{
            Event event;
            mockConnection.send(event = withCapture());
            assertThat(event.getBreadcrumbs().size(), equalTo(1));
        }};
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

        new Verifications() {{
            Event event;
            mockConnection.send(event = withCapture());
            assertThat(event.getBreadcrumbs().size(), equalTo(1));
        }};
    }
}

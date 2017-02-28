package com.getsentry.raven.event;

import com.getsentry.raven.Raven;
import com.getsentry.raven.connection.Connection;
import com.getsentry.raven.event.helper.ContextBuilderHelper;
import mockit.Injectable;
import mockit.Tested;
import mockit.Verifications;
import org.testng.annotations.Test;

import java.util.ArrayList;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;

public class BreadcrumbTest {
    @Tested
    private Raven raven = null;

    @Injectable
    private Connection mockConnection = null;

    @Test
    public void testBreadcrumbsViaContextRecording() {
        raven.addBuilderHelper(new ContextBuilderHelper(raven));

        final Breadcrumb breadcrumb = new BreadcrumbBuilder()
            .setLevel("info")
            .setMessage("message")
            .setCategory("step")
            .build();

        raven.getContext().recordBreadcrumb(breadcrumb);

        raven.sendEvent(new EventBuilder()
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
        raven.addBuilderHelper(new ContextBuilderHelper(raven));

        final Breadcrumb breadcrumb = new BreadcrumbBuilder()
            .setLevel("info")
            .setMessage("message")
            .setCategory("step")
            .build();

        ArrayList<Breadcrumb> breadcrumbs = new ArrayList<>();
        breadcrumbs.add(breadcrumb);

        raven.sendEvent(new EventBuilder()
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
        raven.addBuilderHelper(new ContextBuilderHelper(raven));

        final Breadcrumb breadcrumb = new BreadcrumbBuilder()
            .setLevel("info")
            .setMessage("message")
            .setCategory("step")
            .build();

        ArrayList<Breadcrumb> breadcrumbs = new ArrayList<>();
        breadcrumbs.add(breadcrumb);

        raven.sendEvent(new EventBuilder()
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
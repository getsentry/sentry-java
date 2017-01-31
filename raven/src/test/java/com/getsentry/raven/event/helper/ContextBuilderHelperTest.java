package com.getsentry.raven.event.helper;

import com.getsentry.raven.Raven;
import com.getsentry.raven.connection.Connection;
import com.getsentry.raven.event.Breadcrumb;
import com.getsentry.raven.event.BreadcrumbBuilder;
import com.getsentry.raven.event.Breadcrumbs;
import com.getsentry.raven.event.Event;
import com.getsentry.raven.event.EventBuilder;
import com.getsentry.raven.event.helper.ContextBuilderHelper;
import com.getsentry.raven.event.helper.EventBuilderHelper;
import mockit.Injectable;
import mockit.Tested;
import mockit.Verifications;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;

/**
 * Created by matjaz on 1/31/17.
 */
public class ContextBuilderHelperTest {

    @Tested
    private Raven raven = null;

    @Injectable
    private Connection mockConnection = null;

    @Test
    public void testBreadcrumbsPropagation() {
        Breadcrumb breadcrumb = new BreadcrumbBuilder()
                .setCategory("myCat")
                .setLevel("ERROR")
                .setMessage("Message")
                .build();
        final List<Breadcrumb> match = new ArrayList<>();
        raven.addBuilderHelper(new ContextBuilderHelper(raven));
        Breadcrumbs.record(breadcrumb);
        assertThat(raven, equalTo(Raven.getStoredInstance()));
        raven.sendEvent(new EventBuilder()
                .withMessage("Some random message")
                .withLevel(Event.Level.INFO));

        match.add(breadcrumb);
        new Verifications() {{
            Event event;
            mockConnection.send(event = withCapture());

            assertThat(event.getBreadcrumbs(), equalTo(match));
        }};
    }
}

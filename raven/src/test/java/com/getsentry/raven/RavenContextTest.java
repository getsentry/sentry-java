package com.getsentry.raven;

import com.getsentry.raven.event.Breadcrumb;
import com.getsentry.raven.event.BreadcrumbBuilder;
import org.hamcrest.Matchers;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyCollectionOf;
import static org.hamcrest.Matchers.equalTo;

@Test(singleThreaded = true)
public class RavenContextTest {

    @Test
    public void testActivateDeactivate() {
        for (RavenContext context : RavenContext.getActiveContexts()) {
            context.deactivate();
        }

        RavenContext context = new RavenContext();

        assertThat(RavenContext.getActiveContexts(), emptyCollectionOf(RavenContext.class));

        context.activate();

        List<RavenContext> match = new ArrayList<>(1);
        match.add(context);
        assertThat(RavenContext.getActiveContexts(), equalTo(match));

        context.deactivate();

        assertThat(RavenContext.getActiveContexts(), emptyCollectionOf(RavenContext.class));
    }

    @Test
    public void testBreadcrumbs() {
        RavenContext context = new RavenContext();

        Breadcrumb breadcrumb = new BreadcrumbBuilder()
            .setLevel("info")
            .setCategory("foo")
            .setMessage("test")
            .build();
        context.recordBreadcrumb(breadcrumb);

        List<Breadcrumb> breadcrumbMatch = new ArrayList<>();
        breadcrumbMatch.add(breadcrumb);

        List<Breadcrumb> breadcrumbs = new ArrayList<>();
        Iterator<Breadcrumb> iter = context.getBreadcrumbs();
        while (iter.hasNext()) {
            breadcrumbs.add(iter.next());
        }

        assertThat(breadcrumbs, equalTo(breadcrumbMatch));
    }

    @Test
    public void breadcrumbLimit() {
        RavenContext context = new RavenContext(1);

        Breadcrumb breadcrumb1 = new BreadcrumbBuilder()
            .setLevel("info")
            .setCategory("foo")
            .setMessage("test1")
            .build();
        Breadcrumb breadcrumb2 = new BreadcrumbBuilder()
            .setLevel("info")
            .setCategory("foo")
            .setMessage("test2")
            .build();

        context.recordBreadcrumb(breadcrumb1);
        context.recordBreadcrumb(breadcrumb2);

        List<Breadcrumb> breadcrumbMatch = new ArrayList<>();
        breadcrumbMatch.add(breadcrumb2);

        List<Breadcrumb> breadcrumbs = new ArrayList<>();
        Iterator<Breadcrumb> iter = context.getBreadcrumbs();
        while (iter.hasNext()) {
            breadcrumbs.add(iter.next());
        }

        assertThat(breadcrumbs, equalTo(breadcrumbMatch));

    }

}

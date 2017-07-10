package io.sentry;

import io.sentry.context.Context;
import io.sentry.event.Breadcrumb;
import io.sentry.event.BreadcrumbBuilder;
import io.sentry.event.User;
import io.sentry.event.UserBuilder;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

@Test(singleThreaded = true)
public class ContextTest extends BaseTest {
    @Test
    public void testBreadcrumbs() {
        Context context = new Context();

        Breadcrumb breadcrumb = new BreadcrumbBuilder()
            .setLevel(Breadcrumb.Level.INFO)
            .setCategory("foo")
            .setMessage("test")
            .build();
        context.recordBreadcrumb(breadcrumb);

        List<Breadcrumb> breadcrumbMatch = new ArrayList<>();
        breadcrumbMatch.add(breadcrumb);

        assertThat(context.getBreadcrumbs(), equalTo(breadcrumbMatch));
    }

    @Test
    public void breadcrumbLimit() {
        Context context = new Context(1);

        Breadcrumb breadcrumb1 = new BreadcrumbBuilder()
            .setLevel(Breadcrumb.Level.INFO)
            .setCategory("foo")
            .setMessage("test1")
            .build();
        Breadcrumb breadcrumb2 = new BreadcrumbBuilder()
            .setLevel(Breadcrumb.Level.INFO)
            .setCategory("foo")
            .setMessage("test2")
            .build();

        context.recordBreadcrumb(breadcrumb1);
        context.recordBreadcrumb(breadcrumb2);

        List<Breadcrumb> breadcrumbMatch = new ArrayList<>();
        breadcrumbMatch.add(breadcrumb2);

        assertThat(context.getBreadcrumbs(), equalTo(breadcrumbMatch));

    }

    @Test
    public void testUser() {
        Context context = new Context();

        User user = new UserBuilder()
            .setEmail("test@example.com")
            .setId("1234")
            .setIpAddress("192.168.0.1")
            .setUsername("testUser_123").build();

        context.setUser(user);
        assertThat(context.getUser(), equalTo(user));

        context.clearUser();
        assertThat(context.getUser(), equalTo(null));
    }
}

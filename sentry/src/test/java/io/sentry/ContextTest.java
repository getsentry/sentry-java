package io.sentry;

import io.sentry.context.Context;
import io.sentry.event.Breadcrumb;
import io.sentry.event.BreadcrumbBuilder;
import io.sentry.event.User;
import io.sentry.event.UserBuilder;
import org.testng.annotations.Test;

import java.util.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

@Test(singleThreaded = true)
public class ContextTest extends BaseTest {
    @Test
    public void testBreadcrumbs() {
        Context context = new Context();
        assertThat(context.getBreadcrumbs(), equalTo(Collections.<Breadcrumb>emptyList()));

        Breadcrumb breadcrumb = new BreadcrumbBuilder()
            .setLevel(Breadcrumb.Level.INFO)
            .setCategory("foo")
            .setMessage("test")
            .build();
        context.recordBreadcrumb(breadcrumb);

        List<Breadcrumb> breadcrumbMatch = new ArrayList<>();
        breadcrumbMatch.add(breadcrumb);

        assertThat(context.getBreadcrumbs(), equalTo(breadcrumbMatch));

        context.clearBreadcrumbs();
        assertThat(context.getBreadcrumbs(), equalTo(Collections.<Breadcrumb>emptyList()));
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

    @Test
    public void testTags() {
        Context context = new Context();

        assertThat(context.getTags(), equalTo(Collections.<String, String>emptyMap()));

        context.removeTag("nonExistant");
        assertThat(context.getTags(), equalTo(Collections.<String, String>emptyMap()));

        context.addTag("foo", "bar");
        Map<String, String> matchingTags = new HashMap<>();
        matchingTags.put("foo", "bar");
        assertThat(context.getTags(), equalTo(matchingTags));

        context.removeTag("foo");
        assertThat(context.getTags(), equalTo(Collections.<String, String>emptyMap()));
    }

    @Test
    public void testExtras() {
        Context context = new Context();

        assertThat(context.getExtra(), equalTo(Collections.<String, Object>emptyMap()));

        context.removeExtra("nonExistant");
        assertThat(context.getExtra(), equalTo(Collections.<String, Object>emptyMap()));

        context.addExtra("foo", "bar");
        Map<String, Object> matchingExtras = new HashMap<>();
        matchingExtras.put("foo", "bar");
        assertThat(context.getExtra(), equalTo(matchingExtras));

        context.removeExtra("foo");
        assertThat(context.getExtra(), equalTo(Collections.<String, Object>emptyMap()));
    }

}

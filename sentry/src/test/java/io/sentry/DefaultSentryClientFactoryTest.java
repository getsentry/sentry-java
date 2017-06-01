package io.sentry;

import org.testng.annotations.Test;

import java.util.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

public class DefaultSentryClientFactoryTest extends BaseTest {
    @Test
    public void testFieldsFromDsn() throws Exception {
        String release = "rel";
        String dist = "dis";
        String environment = "env";
        String serverName = "serv";
        String tags = "foo:bar,qux:baz";
        String extraTags = "aaa,bbb";

        String dsn = String.format("https://user:pass@example.com/1?" +
            "release=%s&dist=%s&environment=%s&servername=%s&tags=%s&extratags=%s",
            release, dist, environment, serverName, tags, extraTags);
        SentryClient sentryClient = DefaultSentryClientFactory.sentryClient(dsn);

        assertThat(sentryClient.getRelease(), is(release));
        assertThat(sentryClient.getDist(), is(dist));
        assertThat(sentryClient.getEnvironment(), is(environment));
        assertThat(sentryClient.getServerName(), is(serverName));

        Map<String, String> tagsMap = new HashMap<>();
        tagsMap.put("foo", "bar");
        tagsMap.put("qux", "baz");
        assertThat(sentryClient.getTags(), is(tagsMap));

        Set<String> extraTagsSet = new HashSet<>();
        extraTagsSet.add("aaa");
        extraTagsSet.add("bbb");
        assertThat(sentryClient.getExtraTags(), is(extraTagsSet));

    }
}

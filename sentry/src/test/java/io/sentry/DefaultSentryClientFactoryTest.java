package io.sentry;

import java.util.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import org.junit.After;
import org.junit.Test;

public class DefaultSentryClientFactoryTest extends BaseTest {
    
    @After
    public void after() {
        System.clearProperty("sentry.tags");
        System.clearProperty("sentry.properties.file");
    }
    
    @Test
    public void testFieldsFromDsn() throws Exception {
        String release = "rel";
        String dist = "dis";
        String environment = "env";
        String serverName = "serv";
        String tags = "foo:bar,qux:baz";
        String extraTags = "aaa,bbb";
        String extras = "red:blue,green:yellow";

        String dsn = String.format("https://user:pass@example.com/1?" +
            "release=%s&dist=%s&environment=%s&servername=%s&tags=%s&extratags=%s&extra=%s",
            release, dist, environment, serverName, tags, extraTags, extras);
        SentryClient sentryClient = SentryClientFactory.sentryClient(dsn);

        assertThat(sentryClient, is(notNullValue()));
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
        assertThat(sentryClient.getMdcTags(), is(extraTagsSet));

        Map<String, Object> extrasMap = new HashMap<>();
        extrasMap.put("red", "blue");
        extrasMap.put("green", "yellow");
        assertThat(sentryClient.getExtra(), is(extrasMap));
    }

    @Test
    public void testBadDataInitializesWithoutException() {
        String badTags = "foo:";

        String dsn = String.format("https://user:pass@example.com/1?tags=%s", badTags);
        SentryClient sentryClient = SentryClientFactory.sentryClient(dsn);

        assertThat(sentryClient, is(notNullValue()));
        assertThat(sentryClient, isA(SentryClient.class));
        assertThat(sentryClient.getContext(), notNullValue());
    }
    
    @Test
    public void tagsAtMultipleLevel() {
        System.setProperty("sentry.properties.file", "io/sentry/sentry-tagsAtMultipleLevel.properties");
        System.setProperty("sentry.tags", "foo:system");
        String dsnTags = "foo:dns,qux:baz";
        String dsn =  String.format("https://user:pass@example.com/1?tags=%s", dsnTags);
        SentryClient sentryClient = SentryClientFactory.sentryClient(dsn);
        
        Map<String, String> tagsMap = new HashMap<>();
        tagsMap.put("foo", "system");
        tagsMap.put("qux", "baz");
        tagsMap.put("mode", "file");
        assertThat(sentryClient.getTags(), is(tagsMap));
    }
}

package io.sentry.spring.autoconfigure;

import io.sentry.DefaultSentryClientFactory;
import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;

import static org.assertj.core.api.Assertions.assertThat;

public class SpringBootConfigurationProviderTest {

    @Test
    public void getAppPackages() {
        SentryProperties properties = new SentryProperties();
        properties.getStacktrace().setAppPackages(new LinkedHashSet<>(Arrays.asList("a", "b", "c")));
        SpringBootConfigurationProvider provider = new SpringBootConfigurationProvider(properties);
        assertThat(provider.getProperty(DefaultSentryClientFactory.IN_APP_FRAMES_OPTION))
                .isEqualTo("a,b,c");
    }
}

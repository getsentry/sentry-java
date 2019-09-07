package io.sentry.marshaller.json;

import io.sentry.BaseTest;
import io.sentry.event.interfaces.SentryStackTraceElement;
import io.sentry.event.interfaces.StackTraceInterface;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

import static io.sentry.marshaller.json.JsonComparisonUtil.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class StackTraceInterfaceBindingTest extends BaseTest {
    private StackTraceInterfaceBinding interfaceBinding = null;
    private StackTraceInterface mockStackTraceInterface = null;

    @Before
    public void setup() {
        mockStackTraceInterface = mock(StackTraceInterface.class);
        interfaceBinding = new StackTraceInterfaceBinding();
    }

    @Test
    public void testSingleSentryStackFrame() throws Exception {
        final JsonGeneratorParser jsonGeneratorParser = newJsonGenerator();
        final SentryStackTraceElement sentryStackTraceElement = new SentryStackTraceElement(
            "", "throwError", "index.js", 100, 10,
            "http://localhost", "javascript", null);

        when(mockStackTraceInterface.getStackTrace()).thenReturn(new SentryStackTraceElement[]{sentryStackTraceElement});

        interfaceBinding.writeInterface(jsonGeneratorParser.generator(), mockStackTraceInterface);

        assertThat(jsonGeneratorParser.value(), is(jsonResource("/io/sentry/marshaller/json/SentryStackTrace.json")));
    }

    @Test
    public void testFramesCommonWithEnclosing() throws Exception {
        final JsonGeneratorParser jsonGeneratorParser = newJsonGenerator();
        final SentryStackTraceElement stackTraceElement = new SentryStackTraceElement("", "",
            "File.java", 0, null, null, null, null);

        when(mockStackTraceInterface.getStackTrace())
                .thenReturn(new SentryStackTraceElement[]{stackTraceElement, stackTraceElement});
        when(mockStackTraceInterface.getFramesCommonWithEnclosing()).thenReturn(1);

        interfaceBinding.setRemoveCommonFramesWithEnclosing(true);
        interfaceBinding.writeInterface(jsonGeneratorParser.generator(), mockStackTraceInterface);

        assertThat(jsonGeneratorParser.value(), is(jsonResource("/io/sentry/marshaller/json/StackTrace2.json")));
    }

    @Test
    public void testFramesCommonWithEnclosingDisabled() throws Exception {
        final JsonGeneratorParser jsonGeneratorParser = newJsonGenerator();
        final SentryStackTraceElement stackTraceElement = new SentryStackTraceElement("", "",
            "File.java", 0, null, null, null, null);

        when(mockStackTraceInterface.getStackTrace())
                .thenReturn(new SentryStackTraceElement[]{stackTraceElement, stackTraceElement});
        when(mockStackTraceInterface.getFramesCommonWithEnclosing()).thenReturn(1);

        interfaceBinding.setRemoveCommonFramesWithEnclosing(false);

        interfaceBinding.writeInterface(jsonGeneratorParser.generator(), mockStackTraceInterface);

        assertThat(jsonGeneratorParser.value(), is(jsonResource("/io/sentry/marshaller/json/StackTrace3.json")));
    }

    @Test
    public void testInAppFrames() throws Exception {
        final JsonGeneratorParser jsonGeneratorParser = newJsonGenerator();

        final List<SentryStackTraceElement> sentryStackTraceElements = Arrays.asList(
            new SentryStackTraceElement(
                "inAppModule.foo", "inAppMethod", "File.java",
                1, null, null, null, null),
            new SentryStackTraceElement(
                "notInAppModule.bar", "notInAppMethod",
                "File.java", 2, null, null, null, null),
            new SentryStackTraceElement(
                "inAppModule.Blacklisted$$FastClassBySpringCGLIB$$", "blacklisted",
                "File.java", 3, null, null, null, null),
            new SentryStackTraceElement(
                "inAppModule.Blacklisted$HibernateProxy$", "blacklisted",
                "File.java", 4, null, null, null, null),
            new SentryStackTraceElement(
                "inAppModule.Blacklisted$$EnhancerBySpringCGLIB$$", "blacklisted",
                "File.java", 5, null, null, null, null)
        );

        when(mockStackTraceInterface.getStackTrace())
                .thenReturn(sentryStackTraceElements.toArray(new SentryStackTraceElement[4]));

        List<String> inAppModules = new ArrayList<>();
        inAppModules.add("inAppModule");
        interfaceBinding.setInAppFrames(inAppModules);
        interfaceBinding.writeInterface(jsonGeneratorParser.generator(), mockStackTraceInterface);

        assertThat(jsonGeneratorParser.value(), is(jsonResource("/io/sentry/marshaller/json/StackTraceBlacklist.json")));
    }
}

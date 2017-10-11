package io.sentry.marshaller.json;

import io.sentry.BaseTest;
import io.sentry.event.interfaces.SentryStackTraceElement;
import mockit.Injectable;
import mockit.NonStrictExpectations;
import mockit.Tested;
import io.sentry.event.interfaces.StackTraceInterface;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static io.sentry.marshaller.json.JsonComparisonUtil.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class StackTraceInterfaceBindingTest extends BaseTest {
    @Tested
    private StackTraceInterfaceBinding interfaceBinding = null;
    @Injectable
    private StackTraceInterface mockStackTraceInterface = null;

    @Test
    public void testSingleSentryStackFrame() throws Exception {
        final JsonGeneratorParser jsonGeneratorParser = newJsonGenerator();
        final SentryStackTraceElement sentryStackTraceElement = new SentryStackTraceElement(
            "", "throwError", "index.js", 100, 10,
            "http://localhost","javascript", null);
        new NonStrictExpectations() {{
            mockStackTraceInterface.getStackTrace();
            result = new SentryStackTraceElement[]{sentryStackTraceElement};
        }};
        interfaceBinding.writeInterface(jsonGeneratorParser.generator(), mockStackTraceInterface);

        assertThat(jsonGeneratorParser.value(), is(jsonResource("/io/sentry/marshaller/json/SentryStackTrace.json")));
    }

    @Test
    public void testSingleStackFrame() throws Exception {
        final JsonGeneratorParser jsonGeneratorParser = newJsonGenerator();
        final String methodName = "0cce55c9-478f-4386-8ede-4b6f000da3e6";
        final String className = "31b26f01-9b97-442b-9f36-8a317f94ad76";
        final int lineNumber = 1;
        final SentryStackTraceElement stackTraceElement = new SentryStackTraceElement(className, methodName,
            "File.java", lineNumber, null, null, null, null);
        new NonStrictExpectations() {{
            mockStackTraceInterface.getStackTrace();
            result = new SentryStackTraceElement[]{stackTraceElement};
        }};

        interfaceBinding.writeInterface(jsonGeneratorParser.generator(), mockStackTraceInterface);

        assertThat(jsonGeneratorParser.value(), is(jsonResource("/io/sentry/marshaller/json/StackTrace1.json")));
    }

    @Test
    public void testFramesCommonWithEnclosing() throws Exception {
        final JsonGeneratorParser jsonGeneratorParser = newJsonGenerator();
        final SentryStackTraceElement stackTraceElement = new SentryStackTraceElement("", "",
            "File.java", 0, null, null, null, null);
        new NonStrictExpectations() {{
            mockStackTraceInterface.getStackTrace();
            result = new SentryStackTraceElement[]{stackTraceElement, stackTraceElement};
            mockStackTraceInterface.getFramesCommonWithEnclosing();
            result = 1;
        }};
        interfaceBinding.setRemoveCommonFramesWithEnclosing(true);

        interfaceBinding.writeInterface(jsonGeneratorParser.generator(), mockStackTraceInterface);

        assertThat(jsonGeneratorParser.value(), is(jsonResource("/io/sentry/marshaller/json/StackTrace2.json")));
    }

    @Test
    public void testFramesCommonWithEnclosingDisabled() throws Exception {
        final JsonGeneratorParser jsonGeneratorParser = newJsonGenerator();
        final SentryStackTraceElement stackTraceElement = new SentryStackTraceElement("", "",
            "File.java", 0, null, null, null, null);
        new NonStrictExpectations() {{
            mockStackTraceInterface.getStackTrace();
            result = new SentryStackTraceElement[]{stackTraceElement, stackTraceElement};
            mockStackTraceInterface.getFramesCommonWithEnclosing();
            result = 1;
        }};
        interfaceBinding.setRemoveCommonFramesWithEnclosing(false);

        interfaceBinding.writeInterface(jsonGeneratorParser.generator(), mockStackTraceInterface);

        assertThat(jsonGeneratorParser.value(), is(jsonResource("/io/sentry/marshaller/json/StackTrace3.json")));
    }

    @Test
    public void testInAppFrames() throws Exception {
        final JsonGeneratorParser jsonGeneratorParser = newJsonGenerator();

        final SentryStackTraceElement stackTraceElement1 = new SentryStackTraceElement(
            "inAppModule.foo", "inAppMethod",
            "File.java", 1, null, null, null, null);

        final SentryStackTraceElement stackTraceElement2 = new SentryStackTraceElement(
            "notInAppModule.bar", "notInAppMethod",
            "File.java", 2, null, null, null, null);

        final SentryStackTraceElement stackTraceElement3 = new SentryStackTraceElement(
            "inAppModule.Blacklisted$$FastClassBySpringCGLIB$$", "blacklisted",
            "File.java", 3, null, null, null, null);

        new NonStrictExpectations() {{
            mockStackTraceInterface.getStackTrace();
            result = new SentryStackTraceElement[]{stackTraceElement1, stackTraceElement2, stackTraceElement3};
        }};

        List<String> inAppModules = new ArrayList<>();
        inAppModules.add("inAppModule");
        interfaceBinding.setInAppFrames(inAppModules);
        interfaceBinding.writeInterface(jsonGeneratorParser.generator(), mockStackTraceInterface);

        assertThat(jsonGeneratorParser.value(), is(jsonResource("/io/sentry/marshaller/json/StackTraceBlacklist.json")));
    }

}

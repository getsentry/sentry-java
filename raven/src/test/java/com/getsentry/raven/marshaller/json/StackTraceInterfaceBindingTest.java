package com.getsentry.raven.marshaller.json;

import mockit.Injectable;
import mockit.NonStrictExpectations;
import mockit.Tested;
import com.getsentry.raven.event.interfaces.StackTraceInterface;
import org.testng.annotations.Test;

import static com.getsentry.raven.marshaller.json.JsonComparisonUtil.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class StackTraceInterfaceBindingTest {
    @Tested
    private StackTraceInterfaceBinding interfaceBinding = null;
    @Injectable
    private StackTraceInterface mockStackTraceInterface = null;

    @Test
    public void testSingleStackFrame() throws Exception {
        final JsonGeneratorParser jsonGeneratorParser = newJsonGenerator();
        final String methodName = "0cce55c9-478f-4386-8ede-4b6f000da3e6";
        final String className = "31b26f01-9b97-442b-9f36-8a317f94ad76";
        final int lineNumber = 1;
        final StackTraceElement stackTraceElement = new StackTraceElement(className, methodName, "File.java", lineNumber);
        new NonStrictExpectations() {{
            mockStackTraceInterface.getStackTrace();
            result = new StackTraceElement[]{stackTraceElement};
        }};

        interfaceBinding.writeInterface(jsonGeneratorParser.generator(), mockStackTraceInterface);

        assertThat(jsonGeneratorParser.value(), is(jsonResource("/com/getsentry/raven/marshaller/json/StackTrace1.json")));
    }

    @Test
    public void testFramesCommonWithEnclosing() throws Exception {
        final JsonGeneratorParser jsonGeneratorParser = newJsonGenerator();
        final StackTraceElement stackTraceElement = new StackTraceElement("", "", "File.java", 0);
        new NonStrictExpectations() {{
            mockStackTraceInterface.getStackTrace();
            result = new StackTraceElement[]{stackTraceElement, stackTraceElement};
            mockStackTraceInterface.getFramesCommonWithEnclosing();
            result = 1;
        }};
        interfaceBinding.setRemoveCommonFramesWithEnclosing(true);

        interfaceBinding.writeInterface(jsonGeneratorParser.generator(), mockStackTraceInterface);

        assertThat(jsonGeneratorParser.value(), is(jsonResource("/com/getsentry/raven/marshaller/json/StackTrace2.json")));
    }

    @Test
    public void testFramesCommonWithEnclosingDisabled() throws Exception {
        final JsonGeneratorParser jsonGeneratorParser = newJsonGenerator();
        final StackTraceElement stackTraceElement = new StackTraceElement("", "", "File.java", 0);
        new NonStrictExpectations() {{
            mockStackTraceInterface.getStackTrace();
            result = new StackTraceElement[]{stackTraceElement, stackTraceElement};
            mockStackTraceInterface.getFramesCommonWithEnclosing();
            result = 1;
        }};
        interfaceBinding.setRemoveCommonFramesWithEnclosing(false);

        interfaceBinding.writeInterface(jsonGeneratorParser.generator(), mockStackTraceInterface);

        assertThat(jsonGeneratorParser.value(), is(jsonResource("/com/getsentry/raven/marshaller/json/StackTrace3.json")));
    }
}

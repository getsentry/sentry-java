package net.kencochrane.raven.marshaller.json;

import mockit.Injectable;
import mockit.NonStrictExpectations;
import net.kencochrane.raven.event.interfaces.StackTraceInterface;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class StackTraceInterfaceBindingTest {
    private StackTraceInterfaceBinding interfaceBinding;
    @Injectable
    private StackTraceInterface mockStackTraceInterface;

    @BeforeMethod
    public void setUp() throws Exception {
        interfaceBinding = new StackTraceInterfaceBinding();
    }

    @Test
    public void testSingleStackFrame() throws Exception {
        final JsonComparator jsonComparator = new JsonComparator();
        final String methodName = "0cce55c9-478f-4386-8ede-4b6f000da3e6";
        final String className = "31b26f01-9b97-442b-9f36-8a317f94ad76";
        final int lineNumber = 1;
        final StackTraceElement stackTraceElement = new StackTraceElement(className, methodName, null, lineNumber);
        new NonStrictExpectations() {{
            mockStackTraceInterface.getStackTrace();
            result = new StackTraceElement[]{stackTraceElement};
        }};

        interfaceBinding.writeInterface(jsonComparator.getGenerator(), mockStackTraceInterface);

        jsonComparator.assertSameAsResource("/net/kencochrane/raven/marshaller/json/StackTrace1.json");
    }

    @Test
    public void testFramesCommonWithEnclosing() throws Exception {
        final JsonComparator jsonComparator = new JsonComparator();
        final StackTraceElement stackTraceElement = new StackTraceElement("", "", null, 0);
        new NonStrictExpectations() {{
            mockStackTraceInterface.getStackTrace();
            result = new StackTraceElement[]{stackTraceElement, stackTraceElement};
            mockStackTraceInterface.getFramesCommonWithEnclosing();
            result = 1;
        }};
        interfaceBinding.setRemoveCommonFramesWithEnclosing(true);

        interfaceBinding.writeInterface(jsonComparator.getGenerator(), mockStackTraceInterface);

        jsonComparator.assertSameAsResource("/net/kencochrane/raven/marshaller/json/StackTrace2.json");
    }

    @Test
    public void testFramesCommonWithEnclosingDisabled() throws Exception {
        final JsonComparator jsonComparator = new JsonComparator();
        final StackTraceElement stackTraceElement = new StackTraceElement("", "", null, 0);
        new NonStrictExpectations() {{
            mockStackTraceInterface.getStackTrace();
            result = new StackTraceElement[]{stackTraceElement, stackTraceElement};
            mockStackTraceInterface.getFramesCommonWithEnclosing();
            result = 1;
        }};
        interfaceBinding.setRemoveCommonFramesWithEnclosing(false);

        interfaceBinding.writeInterface(jsonComparator.getGenerator(), mockStackTraceInterface);

        jsonComparator.assertSameAsResource("/net/kencochrane/raven/marshaller/json/StackTrace3.json");
    }
}

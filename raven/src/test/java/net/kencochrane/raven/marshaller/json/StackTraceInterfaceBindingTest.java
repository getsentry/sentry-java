package net.kencochrane.raven.marshaller.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import net.kencochrane.raven.event.interfaces.StackTraceInterface;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;

public class StackTraceInterfaceBindingTest extends AbstractInterfaceBindingTest {
    private StackTraceInterfaceBinding interfaceBinding;
    @Mock
    private StackTraceInterface mockStackTraceInterface;

    @BeforeMethod
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.initMocks(this);
        interfaceBinding = new StackTraceInterfaceBinding();
    }

    @Test
    public void testSingleStackFrame() throws Exception {
        String methodName = UUID.randomUUID().toString();
        String className = UUID.randomUUID().toString();
        int lineNumber = 1;
        StackTraceElement stackTraceElement = new StackTraceElement(className, methodName, null, lineNumber);
        when(mockStackTraceInterface.getStackTrace()).thenReturn(new StackTraceElement[]{stackTraceElement});

        JsonGenerator jSonGenerator = getJsonGenerator();
        interfaceBinding.writeInterface(jSonGenerator, mockStackTraceInterface);
        jSonGenerator.close();

        JsonNode frames = getMapper().readValue(getJsonParser(), JsonNode.class).get("frames");
        assertThat(frames.size(), is(1));
        assertThat(frames.get(0).get("module").asText(), is(className));
        assertThat(frames.get(0).get("function").asText(), is(methodName));
        assertThat(frames.get(0).get("lineno").asInt(), is(lineNumber));
    }

    @Test
    public void testFramesCommonWithEnclosing() throws Exception {
        StackTraceElement stackTraceElement = new StackTraceElement("", "", null, 0);
        when(mockStackTraceInterface.getStackTrace()).thenReturn(new StackTraceElement[]{stackTraceElement, stackTraceElement});
        when(mockStackTraceInterface.getFramesCommonWithEnclosing()).thenReturn(1);

        JsonGenerator jSonGenerator = getJsonGenerator();
        interfaceBinding.setRemoveCommonFramesWithEnclosing(true);
        interfaceBinding.writeInterface(jSonGenerator, mockStackTraceInterface);
        jSonGenerator.close();

        JsonNode frames = getMapper().readValue(getJsonParser(), JsonNode.class).get("frames");
        assertThat(frames.size(), is(2));
        assertThat(frames.get(0).get("in_app").asBoolean(), is(false));
        assertThat(frames.get(1).get("in_app").asBoolean(), is(true));
    }

    @Test
    public void testFramesCommonWithEnclosingDisabled() throws Exception {
        StackTraceElement stackTraceElement = new StackTraceElement("", "", null, 0);
        when(mockStackTraceInterface.getStackTrace()).thenReturn(new StackTraceElement[]{stackTraceElement, stackTraceElement});
        when(mockStackTraceInterface.getFramesCommonWithEnclosing()).thenReturn(1);

        JsonGenerator jSonGenerator = getJsonGenerator();
        interfaceBinding.setRemoveCommonFramesWithEnclosing(false);
        interfaceBinding.writeInterface(jSonGenerator, mockStackTraceInterface);
        jSonGenerator.close();

        JsonNode frames = getMapper().readValue(getJsonParser(), JsonNode.class).get("frames");
        assertThat(frames.size(), is(2));
        assertThat(frames.get(0).get("in_app").asBoolean(), is(true));
        assertThat(frames.get(1).get("in_app").asBoolean(), is(true));
    }
}

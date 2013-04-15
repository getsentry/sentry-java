package net.kencochrane.raven.marshaller.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import net.kencochrane.raven.event.interfaces.ExceptionInterface;
import net.kencochrane.raven.event.interfaces.ImmutableThrowable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class TestExceptionInterfaceBinding extends AbstractTestInterfaceBinding {
    private ExceptionInterfaceBinding interfaceBinding;
    @Mock
    private ExceptionInterface mockExceptionInterface;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        interfaceBinding = new ExceptionInterfaceBinding();
    }

    @Test
    public void testSimpleException() throws Exception {
        String message = UUID.randomUUID().toString();
        Throwable throwable = new IllegalStateException(message);
        when(mockExceptionInterface.getThrowable()).thenReturn(new ImmutableThrowable(throwable));

        JsonGenerator jsonGenerator = getJsonGenerator();
        interfaceBinding.writeInterface(jsonGenerator, mockExceptionInterface);
        jsonGenerator.close();

        JsonNode rootNode = getMapper().readValue(getJsonParser(), JsonNode.class);
        assertThat(rootNode.get("module").asText(), is(throwable.getClass().getPackage().getName()));
        assertThat(rootNode.get("type").asText(), is(throwable.getClass().getSimpleName()));
        assertThat(rootNode.get("value").asText(), is(message));
    }
}

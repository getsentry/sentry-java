package io.sentry.marshaller.json;

import io.sentry.BaseTest;
import io.sentry.event.interfaces.DebugMetaInterface;
import org.junit.Test;

import java.util.ArrayList;

import static io.sentry.marshaller.json.JsonComparisonUtil.jsonResource;
import static io.sentry.marshaller.json.JsonComparisonUtil.newJsonGenerator;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DebugMetaInterfaceBindingTest  extends BaseTest {
    @Test
    public void testSimpleDebugImage() throws Exception {
        final JsonComparisonUtil.JsonGeneratorParser jsonGeneratorParser = newJsonGenerator();

        final ArrayList<DebugMetaInterface.DebugImage> images = new ArrayList<>();
        images.add(new DebugMetaInterface.DebugImage("abcd"));

        DebugMetaInterface mockDebugMetaInterface = mock(DebugMetaInterface.class);
        when(mockDebugMetaInterface.getDebugImages()).thenReturn(images);

        DebugMetaInterfaceBinding debugMetaInterfaceBinding = new DebugMetaInterfaceBinding();

        debugMetaInterfaceBinding.writeInterface(jsonGeneratorParser.generator(), mockDebugMetaInterface);

        assertThat(jsonGeneratorParser.value(), is(jsonResource("/io/sentry/marshaller/json/Proguard.json")));
    }
}

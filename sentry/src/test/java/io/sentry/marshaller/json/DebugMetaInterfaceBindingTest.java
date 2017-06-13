package io.sentry.marshaller.json;


import io.sentry.BaseTest;
import io.sentry.event.interfaces.DebugMetaInterface;
import mockit.Injectable;
import mockit.NonStrictExpectations;
import mockit.Tested;
import org.testng.annotations.Test;

import java.util.ArrayList;

import static io.sentry.marshaller.json.JsonComparisonUtil.jsonResource;
import static io.sentry.marshaller.json.JsonComparisonUtil.newJsonGenerator;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class DebugMetaInterfaceBindingTest  extends BaseTest {
    @Tested
    private DebugMetaInterfaceBinding debugMetaInterfaceBinding = null;
    @Injectable
    private DebugMetaInterface mockDebugMetaInterface = null;

    @Test
    public void testSimpleDebugImage() throws Exception {
        final JsonComparisonUtil.JsonGeneratorParser jsonGeneratorParser = newJsonGenerator();

        final ArrayList<DebugMetaInterface.DebugImage> images = new ArrayList<>();
        images.add(new DebugMetaInterface.DebugImage("abcd"));

        new NonStrictExpectations() {{
            mockDebugMetaInterface.getDebugImages();
            result = images;
        }};
        debugMetaInterfaceBinding.writeInterface(jsonGeneratorParser.generator(), mockDebugMetaInterface);

        assertThat(jsonGeneratorParser.value(), is(jsonResource("/io/sentry/marshaller/json/Proguard.json")));
    }
}

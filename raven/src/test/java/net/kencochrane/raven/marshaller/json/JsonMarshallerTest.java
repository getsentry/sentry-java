package net.kencochrane.raven.marshaller.json;

import mockit.Injectable;
import mockit.NonStrictExpectations;
import net.kencochrane.raven.event.Event;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Date;
import java.util.UUID;

import static net.kencochrane.raven.marshaller.json.JsonTestTool.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class JsonMarshallerTest {
    private final JsonMarshaller jsonMarshaller = new JsonMarshaller();
    @Injectable
    private Event mockEvent;

    @BeforeMethod
    public void setUp() throws Exception {
        // Do not compress by default during the tests
        jsonMarshaller.setCompression(false);
        new NonStrictExpectations() {{
            mockEvent.getId();
            result = UUID.fromString("00000000-0000-0000-0000-000000000000");
            mockEvent.getTimestamp();
            result = new Date(0);
        }};
    }

    @Test
    public void testEventIdWrittenProperly(@Injectable final UUID mockUuid) throws Exception {
        final JsonOutpuStreamTool outpuStreamTool = newJsonOutputStream();
        new NonStrictExpectations() {{
            mockEvent.getId();
            result = mockUuid;
            mockUuid.toString();
            result = "3b71fba5-413e-4022-ae98-5f0b80a155a5";
        }};

        jsonMarshaller.marshall(mockEvent, outpuStreamTool.outputStream());

        assertThat(outpuStreamTool.value(), is(jsonResource("/net/kencochrane/raven/marshaller/json/jsonmarshallertest/testEventId.json")));
    }
}

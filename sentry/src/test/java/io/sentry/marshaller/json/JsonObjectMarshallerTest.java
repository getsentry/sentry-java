package io.sentry.marshaller.json;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import io.sentry.BaseTest;
import mockit.Tested;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;

import static io.sentry.marshaller.json.JsonComparisonUtil.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;


import static io.sentry.marshaller.json.JsonComparisonUtil.newJsonOutputStream;

public class JsonObjectMarshallerTest extends BaseTest {
    @Tested
    private JsonObjectMarshaller jsonObjectMarshaller = new JsonObjectMarshaller();

    @Test
    public void testPath() throws Exception {
        final JsonComparisonUtil.JsonOutputStreamParser jsonOutputStreamParser = newJsonOutputStream();

        Path path = Paths.get("/home", "user", "tmp");
        write(jsonOutputStreamParser.outputStream, path);

        assertThat(jsonOutputStreamParser.value(),
            is(jsonResource("/io/sentry/marshaller/json/jsonobjectmarshallertest/testPath.json")));
    }

    private void write(OutputStream destination, Object object) throws IOException {
        final JsonFactory jsonFactory = new JsonFactory();

        try (JsonGenerator generator = jsonFactory.createGenerator(destination)) {
            generator.writeStartObject();
            generator.writeFieldName("output");
            jsonObjectMarshaller.writeObject(generator, object);
            generator.writeEndObject();
        } finally {
            destination.close();
        }
    }

}

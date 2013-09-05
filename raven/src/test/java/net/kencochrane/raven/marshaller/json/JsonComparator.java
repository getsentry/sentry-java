package net.kencochrane.raven.marshaller.json;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class JsonComparator {
    private static final Logger logger = LoggerFactory.getLogger(JsonComparator.class);
    private final StringWriter jsonOutput = new StringWriter();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JsonGenerator jsonGenerator;

    public JsonComparator() throws IOException {
        JsonFactory jsonFactory = new JsonFactory();
        jsonGenerator = jsonFactory.createGenerator(jsonOutput);
    }

    public JsonGenerator getGenerator() {
        return jsonGenerator;
    }

    public void assertSameAsResource(String resource) throws IOException {
        assertSameAs(JsonComparator.class.getResourceAsStream(resource));
    }

    public void assertSameAs(InputStream expected) throws IOException {
        assertSame(objectMapper.readTree(expected));
    }

    public void assertSameAs(String expected) throws IOException {
        assertSame(objectMapper.readTree(expected));
    }

    public void assertSameAs(File expected) throws IOException {
        assertSame(objectMapper.readTree(expected));
    }

    public void assertSame(JsonNode jsonNode) throws IOException {
        assertThat(objectMapper.readTree(getValue()), is(jsonNode));
    }

    public String getValue() {
        try {
            jsonGenerator.close();
        } catch (Exception e) {
            logger.warn("An error occurred while closing the JSon Generator");
        }
        return jsonOutput.toString();
    }
}

package net.kencochrane.raven.marshaller.json;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.StringWriter;

public final class JsonTestTool {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private JsonTestTool() {
    }

    public static JsonNode jsonResource(String resourcePath) throws Exception {
        return objectMapper.readTree(JsonTestTool.class.getResourceAsStream(resourcePath));
    }

    public static JsonGeneratorTool newJsonGenerator() throws Exception {
        return new JsonGeneratorTool();
    }

    public static JsonOutputStreamTool newJsonOutputStream() throws Exception {
        return new JsonOutputStreamTool();
    }

    public static class JsonGeneratorTool {
        private final JsonGenerator jsonGenerator;
        private final StringWriter stringWriter = new StringWriter();

        private JsonGeneratorTool() throws Exception {
            jsonGenerator = new JsonFactory().createGenerator(stringWriter);
        }

        public JsonGenerator generator() {
            return jsonGenerator;
        }

        public JsonNode value() throws Exception {
            return objectMapper.readTree(toString());
        }

        @Override
        public String toString() {
            try {
                jsonGenerator.close();
                stringWriter.close();
                return stringWriter.toString();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static class JsonOutputStreamTool {
        private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        public OutputStream outputStream() {
            return outputStream;
        }

        public JsonNode value() throws Exception {
            return objectMapper.readTree(toString());
        }

        @Override
        public String toString() {
            try {
                outputStream.close();
                return outputStream.toString(Charsets.UTF_8.name());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}

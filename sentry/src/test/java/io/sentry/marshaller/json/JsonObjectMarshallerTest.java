package io.sentry.marshaller.json;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import io.sentry.BaseTest;
import mockit.Tested;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.sentry.marshaller.json.JsonComparisonUtil.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import static io.sentry.marshaller.json.JsonComparisonUtil.newJsonOutputStream;

public class JsonObjectMarshallerTest extends BaseTest {
    @Tested
    private JsonObjectMarshaller jsonObjectMarshaller = null;

    @BeforeMethod
    public void setUp() throws Exception {
        this.jsonObjectMarshaller = new JsonObjectMarshaller();
        jsonObjectMarshaller.setMaxLengthList(2);
        jsonObjectMarshaller.setMaxLengthString(10);
        jsonObjectMarshaller.setMaxSizeMap(2);
    }

    @Test
    public void testNull() throws Exception {
        final JsonComparisonUtil.JsonOutputStreamParser jsonOutputStreamParser = newJsonOutputStream();

        write(jsonOutputStreamParser.outputStream, null);

        assertThat(jsonOutputStreamParser.value(),
            is(jsonResource("/io/sentry/marshaller/json/jsonobjectmarshallertest/testNull.json")));
    }

    @Test
    public void testPath() throws Exception {
        final JsonComparisonUtil.JsonOutputStreamParser jsonOutputStreamParser = newJsonOutputStream();

        Path path = Paths.get("/home", "user", "tmp");
        write(jsonOutputStreamParser.outputStream, path);

        assertThat(jsonOutputStreamParser.value(),
            is(jsonResource("/io/sentry/marshaller/json/jsonobjectmarshallertest/testPath.json")));
    }

    @Test
    public void testIterable() throws Exception {
        final JsonComparisonUtil.JsonOutputStreamParser jsonOutputStreamParser = newJsonOutputStream();

        List<List<String>> listList = new ArrayList<>();
        List<String> list1 = new ArrayList<>();
        list1.add("1");
        list1.add("2");
        list1.add("3");
        listList.add(list1);
        List<String> list2 = new ArrayList<>();
        list2.add("4");
        list2.add("5");
        list2.add("6");
        listList.add(list2);
        List<String> list3 = new ArrayList<>();
        list3.add("7");
        list3.add("8");
        list3.add("9");
        listList.add(list3);

        write(jsonOutputStreamParser.outputStream, listList);

        assertThat(jsonOutputStreamParser.value(),
            is(jsonResource("/io/sentry/marshaller/json/jsonobjectmarshallertest/testIterable.json")));
    }

    @Test
    public void testMap() throws Exception {
        final JsonComparisonUtil.JsonOutputStreamParser jsonOutputStreamParser = newJsonOutputStream();

        Map<String, Map<String, Integer>> mapMap = new HashMap<>();
        Map<String, Integer> map1 = new HashMap<>();
        map1.put("one", 1);
        map1.put("two", 2);
        map1.put("three", 3);
        mapMap.put("map1", map1);
        Map<String, Integer> map2 = new HashMap<>();
        map2.put("four", 4);
        map2.put("five", 5);
        map2.put("six", 6);
        mapMap.put("map2", map2);
        Map<String, Integer> map3 = new HashMap<>();
        map3.put("seven", 7);
        map3.put("eight", 8);
        map3.put("nine", 9);
        mapMap.put("map3", map3);

        write(jsonOutputStreamParser.outputStream, mapMap);

        assertThat(jsonOutputStreamParser.value(),
            is(jsonResource("/io/sentry/marshaller/json/jsonobjectmarshallertest/testMap.json")));
    }

    @Test
    public void testCycle() throws Exception {

    }

    @Test
    public void testByte() throws Exception {
        final JsonComparisonUtil.JsonOutputStreamParser jsonOutputStreamParser = newJsonOutputStream();

        byte b = 127;
        write(jsonOutputStreamParser.outputStream, b);

        assertThat(jsonOutputStreamParser.value(),
            is(jsonResource("/io/sentry/marshaller/json/jsonobjectmarshallertest/testByte.json")));
    }

    @Test
    public void testIntArray() throws Exception {
        final JsonComparisonUtil.JsonOutputStreamParser jsonOutputStreamParser = newJsonOutputStream();

        int[] ints = {1, 2, 3};
        write(jsonOutputStreamParser.outputStream, ints);

        assertThat(jsonOutputStreamParser.value(),
            is(jsonResource("/io/sentry/marshaller/json/jsonobjectmarshallertest/testIntArray.json")));

    }

    @Test
    public void testByteArray() throws Exception {
        final JsonComparisonUtil.JsonOutputStreamParser jsonOutputStreamParser = newJsonOutputStream();

        byte[] bytes = {3, 4, 5};
        write(jsonOutputStreamParser.outputStream, bytes);

        assertThat(jsonOutputStreamParser.value(),
            is(jsonResource("/io/sentry/marshaller/json/jsonobjectmarshallertest/testByteArray.json")));

    }

    @Test
    public void testObjectArray() throws Exception {
        final JsonComparisonUtil.JsonOutputStreamParser jsonOutputStreamParser = newJsonOutputStream();

        Object o1 = new Object() {
            @Override
            public String toString() {
                return "obj1";
            }
        };

        Object o2 = new Object() {
            @Override
            public String toString() {
                return "obj2";
            }
        };

        Object o3 = new Object() {
            @Override
            public String toString() {
                return "obj3";
            }
        };

        Object[] objs = {o1, o2, o3};
        write(jsonOutputStreamParser.outputStream, objs);

        assertThat(jsonOutputStreamParser.value(),
            is(jsonResource("/io/sentry/marshaller/json/jsonobjectmarshallertest/testObjectArray.json")));

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

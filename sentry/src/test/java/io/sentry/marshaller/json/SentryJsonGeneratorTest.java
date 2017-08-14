package io.sentry.marshaller.json;

import com.fasterxml.jackson.core.JsonFactory;
import io.sentry.BaseTest;
import org.testng.annotations.Test;

import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static io.sentry.marshaller.json.JsonComparisonUtil.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import static io.sentry.marshaller.json.JsonComparisonUtil.newJsonOutputStream;

public class SentryJsonGeneratorTest extends BaseTest {
    private void configureGenerator(SentryJsonGenerator generator) throws Exception {
        generator.setMaxLengthList(2);
        generator.setMaxLengthString(10);
        generator.setMaxSizeMap(2);
        generator.setMaxNesting(3);
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

        Map<String, Map<String, Integer>> mapMap = new LinkedHashMap<>();
        Map<String, Integer> map1 = new LinkedHashMap<>();
        map1.put("one very long key that will be elided", 1);
        map1.put("two very long key that will be elided", 2);
        map1.put("three very long key that will be elided", 3);
        mapMap.put("map1", map1);
        Map<String, Integer> map2 = new LinkedHashMap<>();
        map2.put("four very long key that will be elided", 4);
        map2.put("five very long key that will be elided", 5);
        map2.put("six very long key that will be elided", 6);
        mapMap.put("map2", map2);
        Map<String, Integer> map3 = new LinkedHashMap<>();
        map3.put("seven very long key that will be elided", 7);
        map3.put("eight very long key that will be elided", 8);
        map3.put("nine very long key that will be elided", 9);
        mapMap.put("map3", map3);

        write(jsonOutputStreamParser.outputStream, mapMap);

        assertThat(jsonOutputStreamParser.value(),
            is(jsonResource("/io/sentry/marshaller/json/jsonobjectmarshallertest/testMap.json")));
    }

    @Test
    public void testCycle() throws Exception {
        final JsonComparisonUtil.JsonOutputStreamParser jsonOutputStreamParser = newJsonOutputStream();

        Map<Object, Object> cycleMap = new HashMap<>();
        cycleMap.put("cycle!", cycleMap);

        write(jsonOutputStreamParser.outputStream, cycleMap);

        assertThat(jsonOutputStreamParser.value(),
            is(jsonResource("/io/sentry/marshaller/json/jsonobjectmarshallertest/testCycle.json")));
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
    public void testIntegerArray() throws Exception {
        final JsonComparisonUtil.JsonOutputStreamParser jsonOutputStreamParser = newJsonOutputStream();

        Integer[] ints = {1, 2, 3};
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

    private void write(OutputStream destination, Object object) throws Exception {
        final JsonFactory jsonFactory = new JsonFactory();

        try (SentryJsonGenerator generator = new SentryJsonGenerator(jsonFactory.createGenerator(destination))) {
            configureGenerator(generator);

            generator.writeStartObject();
            generator.writeFieldName("output");
            generator.writeObject(object);
            generator.writeEndObject();
        } finally {
            destination.close();
        }
    }

}

package net.kencochrane.raven.ext;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.servlet.ServletRequestEvent;
import javax.servlet.ServletRequestListener;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

import org.json.simple.JSONObject;
import org.junit.Test;

import mockit.Mocked;
import mockit.NonStrictExpectations;
import net.kencochrane.raven.Client;
import net.kencochrane.raven.SentryDsn;
import net.kencochrane.raven.Events.LogLevel;
import net.kencochrane.raven.spi.JSONProcessor;
import net.kencochrane.raven.spi.RavenMDC;

/**
 * Check to see if ServletJSONProcessor correctly produces the desired http
 * JSON object.
 *
 * @author vvasabi
 */
public class ServletJSONProcessorTest extends Client {

    @Mocked
    ServletContext mockServletContext;

    @Mocked
    HttpServletRequest mockHttpServletRequest;

    public ServletJSONProcessorTest() {
        super(SentryDsn.build("http://public:private@localhost:9000/1"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testConstructHttpJsonResponse() {
        List<JSONProcessor> processors = new ArrayList<JSONProcessor>();
        JSONProcessor processor = new ServletJSONProcessor();
        processors.add(processor);
        setJSONProcessors(processors);

        setHttpServletRequest();
       RavenMDC mdc = new MockRavenMDC();

        processor.prepareDiagnosticContext();
        assertNotNull(mdc.get("sentry.interfaces.Http"));
        Message message = buildMessage("test",
                formatTimestamp(new Date().getTime()), "test",
                LogLevel.ERROR.intValue, "test", null, null);
        JSONObject http = (JSONObject) message.json.get("sentry.interfaces.Http");
        assertEquals("GET", http.get("method"));
        assertEquals("test=abc", http.get("query_string"));
        assertEquals("http://example.com/test?test=abc", http.get("url"));

        JSONObject headers = new JSONObject();
        headers.put("Test-Header", "test value");
        assertEquals(headers, http.get("headers"));

        JSONObject env = new JSONObject();
        env.put("SERVER_PORT", 80);
        env.put("REMOTE_ADDR", "127.0.0.1");
        env.put("SERVER_PROTOCOL", "http");
        env.put("SERVER_NAME", "localhost");
        assertEquals(env, http.get("env"));

        JSONObject cookies = new JSONObject();
        cookies.put("test", "cookie");
        assertEquals(cookies, http.get("cookies"));

        processor.clearDiagnosticContext();
        assertNull(mdc.get("sentry.interfaces.Http"));
    }

    private void setHttpServletRequest() {
        new NonStrictExpectations() {
            {
                mockHttpServletRequest.getRequestURL();
                returns(new StringBuffer("http://example.com/test"));

                mockHttpServletRequest.getQueryString();
                returns("test=abc");

                mockHttpServletRequest.getMethod();
                returns("GET");

                mockHttpServletRequest.getHeaderNames();
                List<String> headerNames = new ArrayList<String>();
                headerNames.add("test-header");
                returns(Collections.enumeration(headerNames));

                mockHttpServletRequest.getHeader("test-header");
                returns("test value");

                mockHttpServletRequest.getRemoteAddr();
                returns("127.0.0.1");

                mockHttpServletRequest.getServerName();
                returns("localhost");

                mockHttpServletRequest.getServerPort();
                returns(80);

                mockHttpServletRequest.getProtocol();
                returns("http");

                mockHttpServletRequest.getCookies();
                returns(new Cookie[] { new Cookie("test", "cookie") });
            }
        };
        ServletRequestListener listener = new RavenServletRequestListener();
        listener.requestInitialized(new ServletRequestEvent(mockServletContext, mockHttpServletRequest));
    }

    protected static class MockRavenMDC extends RavenMDC {

        private Map<String, Object> values = new HashMap<String, Object>();

        public MockRavenMDC() {
            RavenMDC.setInstance(this);
        }

        @Override
        public Object get(String key) {
            return values.get(key);
        }

        @Override
        public void put(String key, Object value) {
            values.put(key, value);
        }

        @Override
        public void remove(String key) {
            values.remove(key);
        }

    }

}

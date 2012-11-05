package net.kencochrane.raven.ext;

import net.kencochrane.raven.spi.JSONProcessor;
import net.kencochrane.raven.spi.RavenMDC;
import org.apache.commons.lang.StringUtils;
import org.json.simple.JSONObject;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.util.Enumeration;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Add HTTP request information to logs when logs are created on HTTP request
 * threads.
 *
 * @author vvasabi
 * @since 1.0
 */
public class ServletJSONProcessor implements JSONProcessor {

    private static final String HTTP_INTERFACE = "sentry.interfaces.Http";

    @Override
    public void prepareDiagnosticContext() {
        HttpServletRequest request = RavenServletRequestListener.getRequest();
        if (request == null) {
            // no request available; do nothing
            return;
        }

        RavenMDC.getInstance().put(HTTP_INTERFACE, buildHttpObject(request));
    }

    @Override
    public void clearDiagnosticContext() {
        RavenMDC.getInstance().remove(HTTP_INTERFACE);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void process(JSONObject json, Throwable exception) {
        JSONObject http = (JSONObject) RavenMDC.getInstance().get(HTTP_INTERFACE);
        if (http == null) {
            return;
        }

        json.put(HTTP_INTERFACE, http);
    }

    @SuppressWarnings("unchecked")
    private static JSONObject buildHttpObject(HttpServletRequest request) {
        JSONObject http = new JSONObject();
        http.put("url", getUrl(request));
        http.put("method", request.getMethod());
        http.put("data", getData(request));
        http.put("query_string", request.getQueryString());
        http.put("cookies", getCookies(request));
        http.put("headers", getHeaders(request));
        http.put("env", getEnvironmentVariables(request));
        return http;
    }

    private static String getUrl(HttpServletRequest request) {
        StringBuffer sb = request.getRequestURL();
        String query = request.getQueryString();
        if (query != null) {
            sb.append("?").append(query);
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static JSONObject getData(HttpServletRequest request) {
        if (!"POST".equals(request.getMethod())) {
            return null;
        }

        JSONObject data = new JSONObject();
        Map<String, String[]> params = request.getParameterMap();
        for (Entry<String, String[]> entry : params.entrySet()) {
            data.put(entry.getKey(), entry.getValue()[0]);
        }
        return data;
    }

    @SuppressWarnings("unchecked")
    private static JSONObject getCookies(HttpServletRequest request) {
        JSONObject cookiesMap = new JSONObject();
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : request.getCookies()) {
                cookiesMap.put(cookie.getName(), cookie.getValue());
            }
        }
        return cookiesMap;
    }

    @SuppressWarnings("unchecked")
    private static JSONObject getHeaders(HttpServletRequest request) {
        JSONObject headers = new JSONObject();
        Enumeration<String> headersEnum = request.getHeaderNames();
        while (headersEnum.hasMoreElements()) {
            String name = headersEnum.nextElement();
            headers.put(capitalize(name), request.getHeader(name));
        }
        return headers;
    }

    @SuppressWarnings("unchecked")
    private static JSONObject getEnvironmentVariables(HttpServletRequest request) {
        JSONObject env = new JSONObject();
        env.put("REMOTE_ADDR", request.getRemoteAddr());
        env.put("SERVER_NAME", request.getServerName());
        env.put("SERVER_PORT", request.getServerPort());
        env.put("SERVER_PROTOCOL", request.getProtocol());
        return env;
    }

    /**
     * Capitalize the first letter of each part of a header name. This is
     * necessary because Sentry currently expects header names to be formatted
     * this way.
     *
     * @param headerName header name to capitalize
     * @return capitalized header name
     */
    private static String capitalize(String headerName) {
        String[] tokens = headerName.split("-");
        for (int i = 0; i < tokens.length; i++) {
            tokens[i] = StringUtils.capitalize(tokens[i]);
        }
        return StringUtils.join(tokens, "-");
    }

}

package net.kencochrane.raven.sentrystub;

import net.kencochrane.raven.sentrystub.auth.InvalidAuthException;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@WebFilter(servletNames = "SentryHttpServlet")
public class SentryAuthenticationFilter implements Filter {
    private static final String SENTRY_AUTH = "X-Sentry-Auth";
    private final SentryStub sentryStub = SentryStub.getInstance();

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        if (!validateAuth(req, resp))
            return;

        chain.doFilter(request, response);
    }

    private boolean validateAuth(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Map<String, String> sentryAuthDetails = extractSentryAuthDetails(req);
        //Can throw an exception, but a request which doesn't provide a project ID should fail anyway
        String projectId = req.getPathInfo().substring(1, req.getPathInfo().indexOf('/', 1));

        try {
            sentryStub.validateAuth(sentryAuthDetails, projectId);
        } catch (InvalidAuthException iae) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            PrintWriter writer = resp.getWriter();
            for (String message : iae.getDetailedMessages()) {
                writer.println(message);
            }
            return false;
        }
        return true;
    }

    private Map<String, String> extractSentryAuthDetails(HttpServletRequest request) {
        String sentryAuth = request.getHeader(SENTRY_AUTH);
        if (sentryAuth == null) {
            return Collections.emptyMap();
        }

        String[] authParameters = sentryAuth.split(",");
        Map<String, String> authDetails = new HashMap<String, String>(authParameters.length);
        for (String authParameter : authParameters) {
            String[] splitParameter = authParameter.split("=");
            authDetails.put(splitParameter[0], splitParameter[1]);
        }

        return authDetails;
    }

    @Override
    public void destroy() {
    }
}

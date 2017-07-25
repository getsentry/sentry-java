package io.sentry.spring;

import io.sentry.Sentry;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * {@link HandlerExceptionResolver} implementation that will record any exception that a
 * Spring {@link org.springframework.web.servlet.mvc.Controller} throws to Sentry. It then
 * returns null, which will let the other (default or custom) exception resolvers handle
 * the actual error.
 */
public class SentryExceptionResolver implements HandlerExceptionResolver, Ordered {
    @Override
    public ModelAndView resolveException(HttpServletRequest request,
                                         HttpServletResponse response,
                                         Object handler,
                                         Exception ex) {

        Sentry.capture(ex);

        // null = run other HandlerExceptionResolvers to actually handle the exception
        return null;
    }

    @Override
    public int getOrder() {
        // ensure this resolver runs first so that all exceptions are reported
        return Integer.MIN_VALUE;
    }
}

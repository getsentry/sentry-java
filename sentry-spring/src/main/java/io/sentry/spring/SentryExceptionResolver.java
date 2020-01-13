package io.sentry.spring;

import io.sentry.Sentry;
import io.sentry.event.Event;
import io.sentry.event.EventBuilder;
import io.sentry.event.helper.BasicRemoteAddressResolver;
import io.sentry.event.interfaces.ExceptionInterface;
import io.sentry.event.interfaces.HttpInterface;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.WebUtils;

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
        ContentCachingRequestWrapper cacheRequest = WebUtils.getNativeRequest(request, ContentCachingRequestWrapper.class);
        if (cacheRequest == null) {
            Sentry.capture(ex);
            return null;
        }
        String requestBody =  new String(cacheRequest.getContentAsByteArray());
        EventBuilder eventBuilder = new EventBuilder()
                .withMessage(ex.getMessage())
                .withLevel(Event.Level.ERROR)
                .withSentryInterface(new ExceptionInterface(ex))
                .withSentryInterface(new HttpInterface(request, new BasicRemoteAddressResolver(), requestBody), false);
        Sentry.capture(eventBuilder);
        // null = run other HandlerExceptionResolvers to actually handle the exception
        return null;
    }

    @Override
    public int getOrder() {
        // ensure this resolver runs first so that all exceptions are reported
        return Integer.MIN_VALUE;
    }
}

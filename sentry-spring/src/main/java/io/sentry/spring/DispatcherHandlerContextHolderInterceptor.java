package io.sentry.spring;

import org.springframework.core.NamedThreadLocal;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class DispatcherHandlerContextHolderInterceptor implements HandlerInterceptor
{

    private static final ThreadLocal<Object> HANDLER_HOLDER = new NamedThreadLocal<>("RequestDispatcher Handler");

    public Object getHandler()
    {
        return HANDLER_HOLDER.get();
    }

    @Override
    public boolean preHandle(final HttpServletRequest request, final HttpServletResponse response, final Object handler) throws Exception
    {
        HANDLER_HOLDER.set(handler);
        return true;
    }

    @Override
    public void postHandle(final HttpServletRequest httpServletRequest, final HttpServletResponse httpServletResponse, final Object o, final ModelAndView modelAndView) throws Exception
    {

    }

    @Override
    public void afterCompletion(final HttpServletRequest request, final HttpServletResponse response, final Object handler, final Exception ex) throws Exception
    {
        HANDLER_HOLDER.remove();
    }

}

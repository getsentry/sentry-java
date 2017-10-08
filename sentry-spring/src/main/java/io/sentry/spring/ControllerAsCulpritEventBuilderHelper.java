package io.sentry.spring;

import io.sentry.event.EventBuilder;
import io.sentry.event.helper.EventBuilderHelper;
import org.springframework.web.method.HandlerMethod;

public class ControllerAsCulpritEventBuilderHelper implements EventBuilderHelper
{

    private final DispatcherHandlerContextHolderInterceptor dispatcherHandlerContextHolderInterceptor;

    public ControllerAsCulpritEventBuilderHelper(
        final DispatcherHandlerContextHolderInterceptor dispatcherHandlerContextHolderInterceptor
    )
    {
        this.dispatcherHandlerContextHolderInterceptor = dispatcherHandlerContextHolderInterceptor;
    }

    @Override
    public void helpBuildingEvent(final EventBuilder eventBuilder)
    {
        Object handler = dispatcherHandlerContextHolderInterceptor.getHandler();
        if (handler != null) {
            if (HandlerMethod.class.isAssignableFrom(handler.getClass())) {
                HandlerMethod handlerMethod = HandlerMethod.class.cast(handler);
                eventBuilder.withCulprit(handlerMethod.getBeanType().getName() + "." + handlerMethod.getMethod().getName() + "()");
            }
        }
    }

}

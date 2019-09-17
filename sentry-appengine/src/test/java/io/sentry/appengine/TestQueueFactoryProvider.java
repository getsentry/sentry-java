package io.sentry.appengine;

import java.util.HashMap;
import java.util.Map;

import com.google.appengine.api.taskqueue.IQueueFactory;
import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.spi.FactoryProvider;

public class TestQueueFactoryProvider extends FactoryProvider<IQueueFactory> {
    private static Map<String, Queue> QUEUES = new HashMap<>();

    private final IQueueFactory factory = new IQueueFactory() {
        @Override
        public Queue getQueue(String s) {
            return QUEUES.get(s);
        }
    };

    public TestQueueFactoryProvider() {
        super(IQueueFactory.class);
    }

    public static void registerQueue(String name, Queue queue) {
        QUEUES.put(name, queue);
    }

    public static void reset() {
        QUEUES.clear();
    }

    @Override
    protected IQueueFactory getFactoryInstance() {
        return factory;
    }
}

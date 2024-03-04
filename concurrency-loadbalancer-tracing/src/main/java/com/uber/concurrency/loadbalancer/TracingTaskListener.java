package com.uber.concurrency.loadbalancer;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import io.opentracing.Span;
import io.opentracing.Tracer;

/**
 * TracingTaskListener is used to enable tracing of {@link LeastConcurrencyLoadBalancer}
 *
 * Usage:
 * <pre>
 * {@code
 * TracingTaskListener<String> listener = TracingTaskListener.newBuilder(String.class)
 *                 .withName("a-loadBalancer")
 *                 .withTaskNameMapper(o->o+"Name")
 *                 .build(mockTracer);
 *
 * HeapConcurrencyLoadBalancer<String> loadBalancer = HeapConcurrencyLoadBalancer.newBuilder(String.class)
 *                 .withTasks(entries)
 *                 .withTaskListener(listener)
 *                 .build();
 * }
 * </pre>
 */
public class TracingTaskListener<T> implements CompletableTask.Listener<T> {
    private static final Function<Object, String> DEFAULT_TASK_NAME_MAPPER = o->o.toString();

    public static class Builder<T> {
        private Function<T, String> taskNameMapper = (Function<T, String>) DEFAULT_TASK_NAME_MAPPER;
        private String name = "unnamed";

        /**
         * Set name of loadBalancer
         *
         * @param name the loadbalancer name
         * @return the builder
         */
        public Builder<T> withName(String name) {
            this.name = name;
            return this;
        }

        /**
         * With function to return name by task
         *
         * @param taskNameMapper function to return name by task
         * @return the builder
         */
        public Builder<T> withTaskNameMapper(Function<T, String> taskNameMapper) {
            this.taskNameMapper = taskNameMapper;
            return this;
        }

        public TracingTaskListener<T> build(Tracer tracer) {
            return new TracingTaskListener(tracer, name, taskNameMapper);
        }
    }

    public static <T> Builder<T> newBuilder(Class<T> cls) {
        return new Builder<>();
    }

    private static final String LOG_PREFIX = "LB.";
    private final Tracer tracer;
    private final String name;
    private final Function<T, String> taskNameMapper;

    private TracingTaskListener(Tracer tracer, String name, Function<T, String> taskNameMapper) {
        this.tracer = tracer;
        this.name = name;
        this.taskNameMapper = taskNameMapper;
    }

    @Override
    public void onCreate(T t) {
        String taskName = taskNameMapper.apply(t);
        Span activeSpan = tracer.activeSpan();
        if (activeSpan != null) {
            activeSpan.log(ImmutableMap.of(LOG_PREFIX + name, taskName));
        }
    }

    @Override
    public void onComplete(T t, boolean succeed) {

    }
}


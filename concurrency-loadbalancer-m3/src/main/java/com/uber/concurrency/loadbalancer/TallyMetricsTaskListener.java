package com.uber.concurrency.loadbalancer;

import com.codahale.metrics.EWMA;
import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableMap;
import com.uber.m3.tally.Scope;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;


/**
 * TallyMetricsTaskListener is used to enable M3 metrics of {@link LeastConcurrencyLoadBalancer}
 *
 * usage:
 * <pre>
 * {@code
 * TallyMetricsTaskListener listener = TallyMetricsTaskListener.newBuilder(String.class)
 *                 .withName("my-loadBalancer")
 *                 .build(mockScope);
 *
 * HeapConcurrencyLoadBalancer<String> loadBalancer = HeapConcurrencyLoadBalancer.newBuilder(String.class)
 *                 .withTasks(entries)
 *                 .withTaskListener(listener)
 *                 .build();
 * }
 * </pre>
 * @param <T> the type parameter
 */
public class TallyMetricsTaskListener<T> implements CompletableTask.Listener<T> {
    private static final Function<Object, String> DEFAULT_TASK_NAME_MAPPER = o->o.toString();

    public static class Builder<T> {
        private Function<T, String> taskNameMapper = (Function<T, String>) DEFAULT_TASK_NAME_MAPPER;
        private String name = "unnamed";
        private Ticker ticker = Ticker.systemTicker();

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

        public Builder<T> withTicker(Ticker ticker) {
            this.ticker = ticker;
            return this;
        }

        TallyMetricsTaskListener<T> build(Scope scope) {
            return new TallyMetricsTaskListener(name, scope, taskNameMapper, ticker);
        }
    }

    public static <T> Builder<T> newBuilder(Class<T> cls) {
        return new Builder<>();
    }

    private static final String TAG_LOAD_BALANCER = "loadBalancer";
    private static final String TAG_TASK = "task";
    private static final String METRIC_NAME_REQUEST_RATE = "requestRate";
    private static final String METRIC_NAME_SUCCESS_RATE = "successRate";
    private static final String METRIC_NAME_FAILURE_RATE = "failureRate";
    private static final String METRIC_NAME_M1RATE_STDDEV = "m1RateStddev";
    private static final String METRIC_NAME_M5RATE_STDDEV = "m5RateStddev";
    private static final String METRIC_NAME_M15RATE_STDDEV = "m15RateStddev";
    private static final long TICK_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(5L);
    private static final StandardDeviation STANDARD_DEVIATION = new StandardDeviation();
    private final Scope scope;
    private final Function<T, String> taskNameMapper;
    private final ConcurrentHashMap<T, EWMARates> taskRates;
    private final AtomicLong lastTick;
    private final Ticker ticker;

    private TallyMetricsTaskListener(String name,
                                     Scope rootScope,
                                     Function<T, String> taskNameMapper,
                                     Ticker ticker) {
        this.scope = rootScope.tagged(ImmutableMap.of(TAG_LOAD_BALANCER, name));
        this.ticker = ticker;
        this.lastTick = new AtomicLong(ticker.read());
        this.taskRates = new ConcurrentHashMap<>();
        this.taskNameMapper = taskNameMapper;
    }

    @Override
    public void onCreate(T t) {
        String name = taskNameMapper.apply(t);
        scope.tagged(ImmutableMap.of(TAG_TASK, name)).counter(METRIC_NAME_REQUEST_RATE).inc(1);

        taskRates.computeIfAbsent(t, o -> new EWMARates()).update(1);
        long oldTick = this.lastTick.get();
        long newTick = ticker.read();
        long age = newTick - oldTick;
        //report standard deviation of m1/5/15 rates to M3 to indicates balance of load balancing
        if (age > TICK_INTERVAL_NANOS && this.lastTick.compareAndSet(oldTick, newTick)) {
            scope.gauge(METRIC_NAME_M1RATE_STDDEV).update(calcStandardDeviation(o->o.m1Rate));
            scope.gauge(METRIC_NAME_M5RATE_STDDEV).update(calcStandardDeviation(o->o.m5Rate));
            scope.gauge(METRIC_NAME_M15RATE_STDDEV).update(calcStandardDeviation(o->o.m15Rate));
        }
    }

    @Override
    public void onComplete(T t, boolean succeed) {
        String name = taskNameMapper.apply(t);

        Scope taskScope = scope.tagged(ImmutableMap.of(TAG_TASK, name));
        if (succeed) {
            taskScope.counter(METRIC_NAME_SUCCESS_RATE).inc(1);
        } else {
            taskScope.counter(METRIC_NAME_FAILURE_RATE).inc(1);
        }
    }

    private double calcStandardDeviation(Function<EWMARates, EWMA> mapper) {
        double[] rates = taskRates.values().stream()
                .map(mapper)
                .map(o->o.getRate(TimeUnit.SECONDS))
                .mapToDouble(d->d)
                .toArray();
        return STANDARD_DEVIATION.evaluate(rates);
    }

    private class EWMARates {
        private final EWMA m1Rate;
        private final EWMA m5Rate;
        private final EWMA m15Rate;
        private final AtomicLong lastTick;

        EWMARates() {
            m1Rate = EWMA.oneMinuteEWMA();
            m5Rate = EWMA.fiveMinuteEWMA();
            m15Rate = EWMA.fifteenMinuteEWMA();
            lastTick = new AtomicLong(ticker.read());
        }

        void update(long n) {
            tickIfNecessary();
            m1Rate.update(n);
            m5Rate.update(n);
            m15Rate.update(n);
        }

        void tickIfNecessary() {
            long oldTick = this.lastTick.get();
            long newTick = ticker.read();
            long age = newTick - oldTick;
            if (age > TICK_INTERVAL_NANOS) {
                long newIntervalStartTick = newTick - age % TICK_INTERVAL_NANOS;
                if (this.lastTick.compareAndSet(oldTick, newIntervalStartTick)) {
                    long requiredTicks = age / TICK_INTERVAL_NANOS;

                    for(long i = 0L; i < requiredTicks; ++i) {
                        this.m1Rate.tick();
                        this.m5Rate.tick();
                        this.m15Rate.tick();
                    }
                }
            }
        }
    }
}


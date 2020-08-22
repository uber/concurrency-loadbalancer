package com.uber.concurrency.loadbalancer.internal;

import com.uber.concurrency.loadbalancer.timedcounter.TimedCounter;
import com.uber.concurrency.loadbalancer.timedcounter.WindowScheduledCounter;
import com.uber.concurrency.loadbalancer.timedcounter.WindowTimedCounter;

import java.time.Duration;

/**
 * Keeps track of task frequency, when all tasks have same concurrency, select the least
 * frequently used task
 * @param <T> the type parameter
 */
public class FrequencyTaskConcurrency<T> extends TaskConcurrencyImpl<T> {
    /**
     * The total number of times the task was acquired
     */
    private final TimedCounter frequency;

    private FrequencyTaskConcurrency(T task, Builder builder) {
        super(task);
        frequency = new WindowTimedCounter(builder.scheduledCounterBuilder);
    }

    @Override
    public void complete(int n, Duration latency) {
        frequency.add(n);
        super.complete(n, latency);
    }

    @Override
    public int compareTo(TaskConcurrency o) {
        int result = super.compareTo(o);
        //When two tasks have same concurrency, pick the least frequent one
        if (result == 0 && o instanceof FrequencyTaskConcurrency) {
            result = Long.compareUnsigned(frequency.get(), ((FrequencyTaskConcurrency)o).frequency.get());
        }
        return result;
    }

    @Override
    public void syncState() {
        frequency.check();
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static class Builder implements TaskConcurrency.Builder<FrequencyTaskConcurrency.Builder>  {
        private WindowScheduledCounter.Builder scheduledCounterBuilder = WindowScheduledCounter.newBuilder();

        @Override
        public Builder withLookBackTime(Duration lookBackTime) {
            this.scheduledCounterBuilder.withMaxDelay(lookBackTime);
            return this;
        }

        public <T> TaskConcurrency<T> build(T task) {
            return new FrequencyTaskConcurrency<>(task, this);
        }
    }
}

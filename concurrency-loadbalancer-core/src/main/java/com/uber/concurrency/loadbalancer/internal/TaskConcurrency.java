package com.uber.concurrency.loadbalancer.internal;

import java.time.Duration;

/**
 * Wrapper of balancing entity and its concurrency counter
 *
 * @param <T> the type of entity
 */
public interface TaskConcurrency<T> extends Comparable<TaskConcurrency> {
    /**
     * Gets task.
     *
     * @return the task
     */
    T getTask();

    /**
     * Acquire task
     */
    default void acquire() {
        acquire(1);
    }

    /**
     * Complete task
     *
     * @param succeed the result
     * @param latency the latency
     */
    default void complete(boolean succeed, Duration latency) {
        complete(1, latency);
    }

    /**
     * Acquire tasks in batch
     *
     * @param n number of requests processed
     */
    void acquire(int n);

    /**
     * Complete requests in batch
     *
     * @param n        number of requests
     * @param latency total duration
     */
    void complete(int n, Duration latency);

    /**
     * Gets concurrency.
     *
     * @return the concurrency
     */
    int getConcurrency();

    /**
     * prepare state for read
     */
    void syncState();

    @Override
    default int compareTo(TaskConcurrency o) {
        return Integer.compareUnsigned(getConcurrency(), o.getConcurrency());
    }

    interface Builder<B extends Builder> {
        B withLookBackTime(Duration lookBackTime);
        <T> TaskConcurrency<T> build(T task);
    }

    class Noop<T> implements TaskConcurrency<T> {
        public static final Noop INSTANCE = new Noop(0);
        private int concurrency;

        public Noop(int init) {
            this.concurrency = init;
        }

        @Override
        public T getTask() {
            return null;
        }

        @Override
        public void acquire(int n) {
        }

        @Override
        public void complete(int n, Duration latency) {
        }

        @Override
        public int getConcurrency() {
            return concurrency;
        }

        @Override
        public void syncState() {
        }
    }
}

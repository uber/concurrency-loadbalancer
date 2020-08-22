package com.uber.concurrency.loadbalancer;

import com.uber.concurrency.loadbalancer.internal.FrequencyTaskConcurrency;
import com.uber.concurrency.loadbalancer.internal.LatencyTaskConcurrency;
import com.uber.concurrency.loadbalancer.internal.TaskConcurrency;
import com.uber.concurrency.loadbalancer.internal.TaskConcurrencyImpl;

import java.util.function.Supplier;

/**
 * SubStrategy decides the strategy of {@link LeastConcurrencyLoadBalancer} to pick partition
 * when there is no unique least concurrency partition
 */
public enum SubStrategy {
    /**
     * no sub strategy, load balancer will pick any one among all least concurrency partitions
     */
    Absent(() -> TaskConcurrencyImpl.newBuilder()),
    /**
     * Least frequency sub strategy. among all least concurrency partitions pick the one with least
     * times being picked
     */
    LeastFrequency(() -> FrequencyTaskConcurrency.newBuilder()),
    /**
     * Least time sub strategy. among all least concurrency partitions pick the one with least aggregated latency
     */
    LeastTime(() -> LatencyTaskConcurrency.newBuilder());

    private Supplier<TaskConcurrency.Builder> newBuilder;

    SubStrategy(Supplier<TaskConcurrency.Builder> newBuilder) {
        this.newBuilder = newBuilder;
    }

    TaskConcurrency.Builder newTaskConcurrencyBuilder() {
        return newBuilder.get();
    }
}

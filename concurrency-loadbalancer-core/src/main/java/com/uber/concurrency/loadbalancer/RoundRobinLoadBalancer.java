package com.uber.concurrency.loadbalancer;

import com.google.common.collect.ImmutableList;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * RoundRobinLoadBalancer iteratively distribute
 * interaction evenly across all entities with index
 *
 * Example:
 * String[] tasks = new String[]{"a", "b", "c", "d", "e"};
 *
 * RoundRobinLoadBalancer lb = RoundRobinLoadBalancer
 *                 .newBuilder()
 *                 .withTasks(Arrays.asList(tasks))
 *                 .build();
 * return lb.next();
 *
 * @ThreadSafe
 *
 * @param <T> the type parameter
 */
public final class RoundRobinLoadBalancer<T> implements LoadBalancer<T> {
    private final ImmutableList<T> tasks;
    private final AtomicInteger index;

    /**
     * Instantiates a new Round robin load balancer.
     *
     * @param tasks list of task
     * @param index start index
     */
    RoundRobinLoadBalancer(List<T> tasks, int index) {
        this.tasks = ImmutableList.copyOf(tasks);
        this.index = new AtomicInteger(index);
    }

    @Override
    public T next() {
        int size = tasks.size();
        if (size == 0) {
            return null;
        }
        return tasks.get(((index.getAndIncrement() % size) + size) % size);
    }

    /**
     * New builder
     *
     * @param <T> the type parameter
     * @return the round robin load balancer builder
     */
    public static <T> RoundRobinLoadBalancerBuilder<T> newBuilder() {
        return new RoundRobinLoadBalancerBuilder<T>();
    }

    /**
     * The type Round robin load balancer builder.
     *
     * @param <T> the type parameter
     */
    public static class RoundRobinLoadBalancerBuilder<T> {
        private List<T> tasks = Collections.emptyList();
        private int initialIndex = new Random().nextInt();

        /**
         * with a list of task
         *
         * @param tasks the entity list
         * @return the round robin load balancer builder
         */
        public RoundRobinLoadBalancerBuilder withTasks(List<T> tasks) {
            this.tasks = tasks;
            return this;
        }

        /**
         * With initial index
         *
         * @param initialIndex the initial index
         * @return the round robin load balancer builder
         */
        public RoundRobinLoadBalancerBuilder withInitialIndex(int initialIndex) {
            this.initialIndex = initialIndex;
            return this;
        }

        /**
         * Build RoundRobinLoadBalancer instance
         *
         * @return the round robin load balancer
         */
        public RoundRobinLoadBalancer<T> build() {
            return new RoundRobinLoadBalancer<T>(tasks, initialIndex);
        }
    }
}

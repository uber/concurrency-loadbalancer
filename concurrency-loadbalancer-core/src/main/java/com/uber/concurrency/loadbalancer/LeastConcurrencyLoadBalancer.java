package com.uber.concurrency.loadbalancer;

public interface LeastConcurrencyLoadBalancer<T> extends LoadBalancer<CompletableTask<T>> {
    LeastConcurrencyLoadBalancer NOOP_INSTANCE = new NoopLeastConcurrencyLoadBalancer();

    class NoopLeastConcurrencyLoadBalancer<T> implements LeastConcurrencyLoadBalancer<T> {
        /**
         * Instantiates a NoopLeastConcurrencyLoadBalancer
         */
        private NoopLeastConcurrencyLoadBalancer() {
        }

        @Override
        public CompletableTask<T> next() {
            return null;
        }
    }
}

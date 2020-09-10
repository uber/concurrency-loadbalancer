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

        @Override
        public Metrics getMetrics() {
            return null;
        }
    }

    Metrics getMetrics();

    /**
     * Least concurrency Loadbalancer metrics
     */
    interface Metrics {
        /**
         * per second request rate
         *
         * @return the double
         */
        double requestRate();

        /**
         * per second completion result success rate
         *
         * @return the double
         */
        double successRate();

        /**
         * per second completion result failure rate
         *
         * @return the double
         */
        double failureRate();

        /**
         * Coefficient of variation of request distribution.
         *
         * @return the double
         */
        double requestCOV();
    }
}

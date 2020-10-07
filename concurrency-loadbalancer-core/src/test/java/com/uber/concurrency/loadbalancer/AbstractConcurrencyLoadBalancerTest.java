package com.uber.concurrency.loadbalancer;

import com.google.common.base.Ticker;
import com.uber.concurrency.loadbalancer.utils.WritableTicker;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class AbstractConcurrencyLoadBalancerTest {
    AbstractConcurrencyLoadBalancer.Metrics metrics;
    WritableTicker ticker;
    Collection<Integer> tasks;
    TestLoadBalancer testLoadBalancer;

    @Before
    public void setup() {
        tasks = Arrays.asList(0,1,2,3);
        ticker = new WritableTicker();
        this.testLoadBalancer = new TestLoadBalancer(tasks, Collections.emptyList(), ticker, new Random(12345));
        this.metrics = testLoadBalancer.getMetrics();
    }

    @Test
    public void testRequestRate() {
        testLoadBalancer.next(5);
        ticker.add(Duration.ofSeconds(6));
        Assert.assertEquals("", metrics.requestRate(),  0, 0.0001);

        testLoadBalancer.next(0);
        testLoadBalancer.next(1);
        ticker.add(Duration.ofSeconds(4));
        ticker.add(Duration.ofNanos(1000));
        Assert.assertTrue(metrics.requestRate() > 0.03);
    }

    @Test
    public void testSuccessRate() {
        testLoadBalancer.next(5).complete(true);
        ticker.add(Duration.ofSeconds(6));
        Assert.assertEquals("", metrics.successRate(),  0, 0.0001);

        testLoadBalancer.next(0).complete(true);
        testLoadBalancer.next(1).complete(true);
        ticker.add(Duration.ofSeconds(4));
        ticker.add(Duration.ofNanos(1000));
        Assert.assertTrue(metrics.successRate() > 0.03);
    }

    @Test
    public void testFailureRate() {
        testLoadBalancer.next(5).complete(false);
        ticker.add(Duration.ofSeconds(6));
        Assert.assertEquals("", metrics.failureRate(),  0, 0.0001);

        testLoadBalancer.next(0).complete(false);
        testLoadBalancer.next(1).complete(false);
        ticker.add(Duration.ofSeconds(4));
        ticker.add(Duration.ofNanos(1000));
        Assert.assertTrue(metrics.failureRate() > 0.03);
    }

    @Test
    public void testNoopLoadBalancer() {
        LeastConcurrencyLoadBalancer.Metrics metrics = LeastConcurrencyLoadBalancer.NOOP_INSTANCE.getMetrics();
        Assert.assertEquals(0, metrics.failureRate(), 0.001);
        Assert.assertEquals(0, metrics.requestCOV(), 0.001);
        Assert.assertEquals(0, metrics.requestRate(), 0.001);
        Assert.assertEquals(0, metrics.successRate(), 0.001);
    }

    @Test
    public void testRequestCOV() {
        ticker.add(Duration.ofSeconds(6));
        Assert.assertEquals("", metrics.requestCOV(),  0, 0.0001);

        for (Integer v : tasks) {
            testLoadBalancer.next(v);
        }
        ticker.add(Duration.ofSeconds(5));
        Assert.assertEquals("", metrics.requestCOV(),  0, 0.0001);

        testLoadBalancer.next(0);
        ticker.add(Duration.ofSeconds(5));
        Assert.assertNotEquals("", metrics.requestCOV(),  0, 0.0001);
    }

    private static class TestLoadBalancer<T> extends AbstractConcurrencyLoadBalancer<T> {
        Ticker ticker;
        List<T> tasks;
        Random rand;
        /**
         * Instantiates a new LeastConcurrencyLoadBalancer
         *
         * @param tasks          the tasks
         * @param list           the listeners
         * @param ticker         the ticker
         */
        TestLoadBalancer(Collection tasks, List list, Ticker ticker, Random rand) {
            super(tasks, list, ticker);
            this.ticker = ticker;
            this.tasks = new ArrayList<>(tasks);
            this.rand = rand;
        }

        @Override
        public CompletableTask<T> next() {
            return next(tasks.get(rand.nextInt(tasks.size())));
        }

        public CompletableTask<T> next(T t) {
            return new TestCompletableTask(t, ticker.read());
        }

        class TestCompletableTask extends AbstractCompletableTask {

            TestCompletableTask(T t, long startNano) {
                super(t, startNano);
            }
        }
    }
}

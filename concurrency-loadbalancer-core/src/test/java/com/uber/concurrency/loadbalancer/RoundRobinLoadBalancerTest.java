package com.uber.concurrency.loadbalancer;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

public class RoundRobinLoadBalancerTest {

    @Test
    public void testDefaultLoadBalancer() {
        RoundRobinLoadBalancer lb = RoundRobinLoadBalancer
                .newBuilder()
                .build();
        Assert.assertNull(lb.next());
    }

    @Test
    public void testLoadBalancer() {
        String[] tasks = new String[]{"a", "b", "c", "d", "e"};

        RoundRobinLoadBalancer lb = RoundRobinLoadBalancer
                .newBuilder()
                .withInitialIndex(0)
                .withTasks(Arrays.asList(tasks))
                .build();
        for(int i = 0; i < 10000; ++i) {
            Assert.assertEquals(tasks[i%tasks.length], lb.next());
        }
    }

}

package com.uber.concurrency.loadbalancer;

import com.uber.concurrency.loadbalancer.internal.TaskConcurrency;
import com.uber.concurrency.loadbalancer.utils.WritableTicker;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.modules.junit4.PowerMockRunner;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore("javax.management.*")
public class HeapConcurrencyLoadBalancerTest {

    @Test
    public void testZeroTask() {
        HeapConcurrencyLoadBalancer<String> loadBalancer = HeapConcurrencyLoadBalancer.newBuilder(String.class)
                .withTasks(Collections.EMPTY_LIST)
                .build();

        CompletableTask<String> task1 = loadBalancer.next();
        Assert.assertNull(task1);
    }

    @Test
    public void testOneTask() {
        ArrayList<String> entries = new ArrayList<String>() {{add("a");}};

        HeapConcurrencyLoadBalancer<String> loadBalancer = HeapConcurrencyLoadBalancer.newBuilder(String.class)
                .withTasks(entries)
                .build();

        CompletableTask<String> task1 = loadBalancer.next();
        Assert.assertEquals("a", task1.getTask());
    }

    @Test
    public void testTwoTasks() {
        ArrayList<String> entries = new ArrayList<String>() {{add("a"); add("b");}};

        HeapConcurrencyLoadBalancer<String> loadBalancer = HeapConcurrencyLoadBalancer.newBuilder(String.class)
                .withTasks(entries)
                .build();

        Set<String> result = new HashSet<>();
        CompletableTask<String> ct1 = loadBalancer.next();
        result.add(ct1.getTask());
        CompletableTask<String> ct2 = loadBalancer.next();
        result.add(ct2.getTask());
        Assert.assertEquals(2, result.size());
        result = new HashSet<>();
        ct2.complete();
        for (int i=0; i < 10; ++i) {
            ct2 = loadBalancer.next();
            result.add(ct2.getTask());
            ct2.complete();
        }
        Assert.assertEquals(1, result.size());
    }

    @Test
    public void testMultiComplete() {
        ArrayList<String> entries = new ArrayList<String>() {{add("a"); add("b");}};
        final AtomicInteger pendingRequest = new AtomicInteger();

        HeapConcurrencyLoadBalancer<String> loadBalancer = HeapConcurrencyLoadBalancer.newBuilder(String.class)
                .withTasks(entries)
                .withTaskListener(new CompletableTask.Listener<String>() {
                    @Override
                    public void onCreate(String s) {
                        pendingRequest.incrementAndGet();
                    }

                    @Override
                    public void onComplete(String s, boolean succeed) {
                        pendingRequest.decrementAndGet();
                    }
                })
                .build();
        Assert.assertEquals(0, pendingRequest.get());
        CompletableTask<String> ct1 = loadBalancer.next();
        Assert.assertEquals(1, pendingRequest.get());
        ct1.complete();
        Assert.assertEquals(0, pendingRequest.get());
        ct1.complete();
        Assert.assertEquals(0, pendingRequest.get());
    }

    @Test
    public void testFairness() {
        ArrayList<String> entries = new ArrayList<>();
        for (int i = 0; i < 26; ++i) {
            entries.add(Character.toString((char)('a'+i)));
        }

        HeapConcurrencyLoadBalancer<String> loadBalancer = HeapConcurrencyLoadBalancer.newBuilder(String.class)
                .withTasks(entries)
                .withSubStrategy(SubStrategy.LeastFrequency)
                .build();

        Set<String> result = new HashSet<>();
        for (int i = 0; i < entries.size(); ++i) {
            CompletableTask<String> task = loadBalancer.next();
            result.add(task.getTask());
            task.complete();
        }
        Assert.assertEquals(entries.size(), result.size());
    }

    @Test
    public void testLeastLatency() {
        ArrayList<String> entries = new ArrayList<String>() {{add("a"); add("b"); add("c");}};

        WritableTicker testTicker = new WritableTicker();
        HeapConcurrencyLoadBalancer<String> loadBalancer = HeapConcurrencyLoadBalancer.newBuilder(String.class)
                .withTasks(entries)
                .withSubStrategy(SubStrategy.LeastTime)
                .withTicker(testTicker)
                .build();

        CompletableTask<String> ct1 = loadBalancer.next();
        testTicker.add(Duration.ofMinutes(1));
        ct1.complete();

        Set<String> result = new HashSet<>();
        for (int i = 0; i < 100; ++i) {
            CompletableTask<String> ct2 = loadBalancer.next();
            result.add(ct2.getTask());
            testTicker.add(Duration.ofSeconds(1));
            ct2.complete();
        }
        Assert.assertEquals(2, result.size());
        result.add(ct1.getTask());
        Assert.assertEquals(3, result.size());
    }

    @Test
    public void testMultipleRound() {
        ArrayList<String> entries = new ArrayList<String>() {{add("a"); add("b"); add("c"); add("d"); add("e"); add("f"); add("g"); add("h");}};
        HeapConcurrencyLoadBalancer<String> loadBalancer = HeapConcurrencyLoadBalancer.newBuilder(String.class)
                .withTasks(entries)
                .build();
        Map<String, Integer> keyFrequency = new HashMap<>();
        int repeat = 1000;
        int count = repeat * entries.size();
        for (int i = 0; i < count; ++i) {
            String key = loadBalancer.next().getTask();
            int v = keyFrequency.computeIfAbsent(key, o->0);
            keyFrequency.put(key, v+1);
        }
        for (Map.Entry<String, Integer> entry : keyFrequency.entrySet()) {
            Assert.assertEquals(repeat, entry.getValue().intValue());
        }
    }

    @Test
    public void testFailureWithoutSustain() {
        ArrayList<String> entries = new ArrayList<String>() {{add("a"); add("b");}};

        HeapConcurrencyLoadBalancer<String> loadBalancer = HeapConcurrencyLoadBalancer.newBuilder(String.class)
                .withTasks(entries)
                .build();

        Set<String> result = new HashSet<>();
        CompletableTask<String> ct1 = loadBalancer.next();
        result.add(ct1.getTask());
        CompletableTask<String> ct2 = loadBalancer.next();
        result.add(ct2.getTask());
        Assert.assertEquals(2, result.size());
        result = new HashSet<>();
        ct2.complete(false);
        for (int i=0; i < 10; ++i) {
            ct2 = loadBalancer.next();
            result.add(ct2.getTask());
            ct2.complete();
        }
        Assert.assertEquals(1, result.size());
    }

    @Test
    public void testFailureSustain() {
        ArrayList<String> entries = new ArrayList<String>() {{add("a"); add("b");}};
        WritableTicker testTicker = new WritableTicker();
        HeapConcurrencyLoadBalancer<String> loadBalancer = HeapConcurrencyLoadBalancer.newBuilder(String.class)
                .withTasks(entries)
                .withFailureEffectiveLatency(Duration.ofSeconds(30))
                .withTicker(testTicker)
                .build();

        Set<String> result = new HashSet<>();
        CompletableTask<String> ct1 = loadBalancer.next();
        String t1 = ct1.getTask();
        CompletableTask<String> ct2 = loadBalancer.next();
        String t2 = ct2.getTask();
        Assert.assertNotEquals(t1, t2);
        ct1.complete(false);
        ct2.complete(true);
        for (int i=0; i < 10; ++i) {
            ct2 = loadBalancer.next();
            Assert.assertNotEquals(t1, ct2.getTask());
            ct2.complete();
        }
        testTicker.add(Duration.ofSeconds(15));
        for (int i=0; i < 10; ++i) {
            ct2 = loadBalancer.next();
            result.add(ct2.getTask());
            ct2.complete();
        }
        Assert.assertEquals(1, result.size());
        testTicker.add(Duration.ofSeconds(15));
        for (int i=0; i < 10; ++i) {
            ct2 = loadBalancer.next();
            result.add(ct2.getTask());
            ct2.complete();
        }
        Assert.assertEquals(1, result.size());
        testTicker.add(Duration.ofSeconds(1));
        //2.4 second later, passed sustain period of failed task
        for (int i=0; i < 10; ++i) {
            ct2 = loadBalancer.next();
            result.add(ct2.getTask());
            ct2.complete();
        }
        Assert.assertEquals(2, result.size());
    }

    @Test
    public void testConcurrencyLimits() {
        ArrayList<String> entries = new ArrayList<String>() {{add("a");add("b");}};

        HeapConcurrencyLoadBalancer<String> loadBalancer = HeapConcurrencyLoadBalancer.newBuilder(String.class)
                .withTasks(entries)
                .build();

        HeapConcurrencyLoadBalancer.TaskConcurrencyQueue<String> repo = loadBalancer.getTaskConcurrencyQueue();
        for (String key : entries) {
            TaskConcurrency<String> tc = repo.get(key);
            tc.acquire(Integer.MAX_VALUE);
        }

        Set<String> result = new HashSet<>();
        for (int i = 0; i < entries.size(); ++i) {
            CompletableTask<String> task = loadBalancer.next();
            result.add(task.getTask());
        }

        Assert.assertEquals(new HashSet<>(entries), result);

        //reached limit
        CompletableTask<String> task = loadBalancer.next();
        Assert.assertNull(task);
    }
}

package com.uber.concurrency.loadbalancer;

import com.uber.concurrency.loadbalancer.internal.TaskConcurrency;
import com.uber.concurrency.loadbalancer.utils.WritableTicker;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import com.uber.concurrency.loadbalancer.ArrayConcurrencyLoadBalancer.WeightedSelector;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(PowerMockRunner.class)
@PrepareForTest(WeightedSelector.class)
@PowerMockIgnore("javax.management.*")
public class ArrayConcurrencyLoadBalancerTest {

    @Test
    public void testZeroTask() {
        ArrayConcurrencyLoadBalancer<String> loadBalancer = ArrayConcurrencyLoadBalancer.newBuilder(String.class)
                .withTasks(Collections.EMPTY_LIST)
                .build();

        CompletableTask<String> task1 = loadBalancer.next();
        Assert.assertNull(task1);
    }

    @Test
    public void testOneTask() {
        ArrayList<String> entries = new ArrayList<String>() {{add("a");}};

        ArrayConcurrencyLoadBalancer<String> loadBalancer = ArrayConcurrencyLoadBalancer.newBuilder(String.class)
                .withTasks(entries)
                .build();

        CompletableTask<String> task1 = loadBalancer.next();
        Assert.assertEquals("a", task1.getTask());
    }

    @Test
    public void testTwoTasks() {
        ArrayList<String> entries = new ArrayList<String>() {{add("a"); add("b");}};

        ArrayConcurrencyLoadBalancer<String> loadBalancer = ArrayConcurrencyLoadBalancer.newBuilder(String.class)
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
        for (int i = 0; i < 10; ++i) {
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

        ArrayConcurrencyLoadBalancer<String> loadBalancer = ArrayConcurrencyLoadBalancer.newBuilder(String.class)
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

        ArrayConcurrencyLoadBalancer<String> loadBalancer = ArrayConcurrencyLoadBalancer.newBuilder(String.class)
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
        ArrayConcurrencyLoadBalancer<String> loadBalancer = ArrayConcurrencyLoadBalancer.newBuilder(String.class)
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

    @Test(expected = IllegalArgumentException.class)
    public void testNegtiveFailureEffectiveLatency() {
        ArrayConcurrencyLoadBalancer.newBuilder(String.class)
                .withFailureEffectiveLatency(Duration.ofSeconds(-30));
    }

    @Test
    public void testFailureSustain() {
        WritableTicker testTicker = new WritableTicker();
        ArrayList<String> entries = new ArrayList<String>() {{add("a"); add("b");}};

        ArrayConcurrencyLoadBalancer<String> loadBalancer = ArrayConcurrencyLoadBalancer.newBuilder(String.class)
                .withTasks(entries)
                .withFailureEffectiveLatency(Duration.ofSeconds(30), 100)
                .withTicker(testTicker)
                .build();

        Set<String> result = new HashSet<>();
        CompletableTask<String> ct1 = loadBalancer.next();
        String t1 = ct1.getTask();
        CompletableTask<String> ct2 = loadBalancer.next();
        String t2 = ct2.getTask();
        Assert.assertNotEquals(t1, t2);
        testTicker.add(Duration.ofSeconds(15));
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
        //one second later, passed sustain period of failed task
        testTicker.add(Duration.ofSeconds(1));
        for (int i=0; i < 10; ++i) {
            ct2 = loadBalancer.next();
            result.add(ct2.getTask());
            ct2.complete();
        }
        Assert.assertEquals(2, result.size());
    }

    @Test
    public void testGroupSize() {
        ArrayList<String> entries = new ArrayList<String>() {{add("a"); add("b"); add("c"); add("d"); add("e"); add("f"); add("g"); add("h");}};
        ArrayConcurrencyLoadBalancer<String> loadBalancer = ArrayConcurrencyLoadBalancer.newBuilder(String.class)
                .withTasks(entries)
                .withGroupSize(3)
                .build();
        Map<String, Integer> keyFrequency = new HashMap<>();
        int repeat = 1000;
        int count = repeat * entries.size();
        for (int i = 0; i < count; ++i) {
            String key = loadBalancer.next().getTask();
            int v = keyFrequency.computeIfAbsent(key, o->0);
            keyFrequency.put(key, v+1);
        }
        int sum = 0;
        for (Map.Entry<String, Integer> entry : keyFrequency.entrySet()) {
            sum += entry.getValue();
            Assert.assertTrue(Math.abs(1 - entry.getValue() / (double)repeat) < 0.1);
        }
        Assert.assertEquals(count, sum);
    }

    @Test
    public void testUpdateTasks() {
        PowerMockito.mockStatic(WeightedSelector.class);
        WeightedSelector.WeightedSelectorBuilder mockBuilder = Mockito.mock(WeightedSelector.WeightedSelectorBuilder.class);
        Mockito.when(WeightedSelector.newBuilder()).thenReturn(mockBuilder);
        ArrayList<String> entries = new ArrayList<>();
        for(int i = 0 ; i < 21; ++i) {
            entries.add(Integer.toString(i));
        }
        ArrayConcurrencyLoadBalancer<String> loadBalancer = ArrayConcurrencyLoadBalancer.newBuilder(String.class)
                .withTasks(entries)
                .withGroupSize(10)
                .build();
        ArgumentCaptor<List> listArgumentCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Integer> integerArgumentCaptor = ArgumentCaptor.forClass(Integer.class);
        Mockito.verify(mockBuilder, Mockito.times(3)).add(listArgumentCaptor.capture(), integerArgumentCaptor.capture());
        Assert.assertEquals(3, listArgumentCaptor.getAllValues().size());
        for (int i = 0; i < 3; ++i) {
            Assert.assertEquals(7, listArgumentCaptor.getAllValues().get(i).size());
            Assert.assertEquals(7, integerArgumentCaptor.getAllValues().get(i).intValue());
        }
    }

    @Test
    public void testShareTaskConcurrencyMap() throws IOException {
        ByteArrayOutputStream os1 = new ByteArrayOutputStream();
        ByteArrayOutputStream os2 = new ByteArrayOutputStream();
        ArrayList<OutputStream> tasks = new ArrayList<>();
        tasks.add(os1);
        tasks.add(os2);

        ArrayConcurrencyLoadBalancer.Builder<OutputStream> builder = ArrayConcurrencyLoadBalancer
                .newBuilder(OutputStream.class);

        ArrayConcurrencyLoadBalancer<OutputStream> loadBalancer1 = builder
                .withTasks(tasks)
                .build();

        ArrayConcurrencyLoadBalancer<OutputStream> loadBalancer2 = builder
                .withTasks(tasks)
                .build();

        OutputStream os = loadBalancer1.next().getTask();
        os.write("payload".getBytes());
        os.flush();

        os = loadBalancer2.next().getTask();
        os.write("payload".getBytes());
        os.flush();

        Assert.assertEquals("payload", new String(os1.toByteArray()));
        Assert.assertEquals("payload", new String(os2.toByteArray()));
    }

    @Test
    public void testConcurrencyLimits() {
        ArrayList<String> entries = new ArrayList<String>() {{add("a");add("b");}};

        ArrayConcurrencyLoadBalancer.Builder<String> builder = ArrayConcurrencyLoadBalancer.newBuilder(String.class);

        ArrayConcurrencyLoadBalancer<String> loadBalancer = builder
                .withTasks(entries)
                .build();

        for (String key : entries) {
            TaskConcurrency<String> tc = builder.getTaskConcurrencyMap().apply(key);
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

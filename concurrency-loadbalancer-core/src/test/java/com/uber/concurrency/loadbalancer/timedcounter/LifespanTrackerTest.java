package com.uber.concurrency.loadbalancer.timedcounter;

import com.google.common.base.Ticker;
import com.uber.concurrency.loadbalancer.utils.WritableTicker;
import org.junit.Assert;
import org.junit.Test;

import java.time.Duration;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LifespanTrackerTest {

    @Test
    public void testOneCount() {
        WritableTicker clock = new WritableTicker();
        LifespanTracker tracker = new LifespanTracker(Duration.ofMinutes(5), 100, clock);
        Assert.assertEquals(0, tracker.purge());

        clock.add(Duration.ofSeconds(10)); //10s
        long result = tracker.add(1, Duration.ofMinutes(1)); //count should sustain for 60s
        Assert.assertEquals(result, Duration.ofSeconds(72).toNanos());
        Assert.assertEquals(0, tracker.purge());

        clock.add(Duration.ofSeconds(50)); //60s
        Assert.assertEquals(0, tracker.purge());

        clock.add(Duration.ofSeconds(11)); //71s
        Assert.assertEquals(0, tracker.purge());

        clock.add(Duration.ofSeconds(1));  //72s
        Assert.assertEquals(1, tracker.purge()); //count sustained for 61s because of window precision
    }

    @Test
    public void testTwoCount() {
        WritableTicker clock = new WritableTicker();
        LifespanTracker tracker = new LifespanTracker(Duration.ofMinutes(5), 100, clock);
        Assert.assertEquals(0, tracker.purge());

        clock.add(Duration.ofSeconds(10));  //10s
        long result = tracker.add(1, Duration.ofMinutes(1)); //count should sustain for 60s
        Assert.assertEquals(result, Duration.ofSeconds(72).toNanos());
        Assert.assertEquals(0, tracker.purge());

        clock.add(Duration.ofSeconds(50));  //60s
        Assert.assertEquals(0, tracker.purge());
        result = tracker.add(1, Duration.ofMinutes(2)); //count should sustain for 120s
        Assert.assertEquals(result, Duration.ofSeconds(183).toNanos());

        clock.add(Duration.ofSeconds(11));  //71s
        Assert.assertEquals(0, tracker.purge());

        clock.add(Duration.ofSeconds(1));   //72s
        Assert.assertEquals(1, tracker.purge()); //count sustained for 61s because of window precision

        clock.add(Duration.ofSeconds(110)); //182s
        Assert.assertEquals(0, tracker.purge());

        clock.add(Duration.ofSeconds(1));   //183s
        Assert.assertEquals(1, tracker.purge()); //count sustained for 122s because of window precision
    }

    @Test
    public void testThreeCount() {
        WritableTicker clock = new WritableTicker();
        LifespanTracker tracker = new LifespanTracker(Duration.ofMinutes(5), 100, clock);
        Assert.assertEquals(0, tracker.purge());

        clock.add(Duration.ofSeconds(10));  //10s
        long result = tracker.add(1, Duration.ofMinutes(1)); //count1 should sustain for 60s
        Assert.assertEquals(result, Duration.ofSeconds(72).toNanos());
        Assert.assertEquals(0, tracker.purge());

        clock.add(Duration.ofSeconds(50));  //60s
        Assert.assertEquals(0, tracker.purge());
        result = tracker.add(1, Duration.ofMinutes(2)); //count2 should sustain for 120s
        Assert.assertEquals(result, Duration.ofSeconds(183).toNanos());

        clock.add(Duration.ofSeconds(11));  //71s
        Assert.assertEquals(0, tracker.purge());
        result = tracker.add(1, Duration.ofSeconds(30)); //count3 should sustain for 30s
        Assert.assertEquals(result, Duration.ofSeconds(102).toNanos());

        clock.add(Duration.ofSeconds(1));   //72s
        Assert.assertEquals(1, tracker.purge()); //count1 sustained for 61s because of window precision

        clock.add(Duration.ofSeconds(29));   //101s
        Assert.assertEquals(0, tracker.purge());

        clock.add(Duration.ofSeconds(1));   //102s
        Assert.assertEquals(1, tracker.purge()); //count3 sustained for 30s exactly

        clock.add(Duration.ofSeconds(80)); //182s
        Assert.assertEquals(0, tracker.purge());

        clock.add(Duration.ofSeconds(1));   //183s
        Assert.assertEquals(1, tracker.purge()); //count2 sustained for 122s because of window precision
    }

    @Test
    public void testNegativeTimeToLive() {
        WritableTicker clock = new WritableTicker();
        LifespanTracker tracker = new LifespanTracker(Duration.ofSeconds(30), 10, clock);
        Assert.assertEquals(0, tracker.purge());
        long result = tracker.add(1, Duration.ofSeconds(-10));
        Assert.assertEquals(result, Duration.ofSeconds(3).toNanos());
        Assert.assertEquals(0, 0, tracker.purge());
        clock.add(Duration.ofSeconds(2)); //2s
        Assert.assertEquals(0, 0, tracker.purge());
        clock.add(Duration.ofSeconds(1)); //3s
        Assert.assertEquals(1, 0, tracker.purge());
    }

    @Test
    public void testZeroMaxDuration() {
        Ticker ticker = Ticker.systemTicker();
        LifespanTracker tracker = new LifespanTracker(Duration.ofSeconds(0), 10, ticker);
        long nanoNow = ticker.read();
        long nano = tracker.add(10, Duration.ofSeconds(1));
        Assert.assertTrue(nano >= nanoNow);
        long result = tracker.purge();
        Assert.assertEquals(10, result);
    }

    @Test
    public void testConcurrentExecution() throws Exception {
        Duration maxDuration = Duration.ofMinutes(5);
        int nTasks = 20000;

        long startMs = System.currentTimeMillis();
        for (int i = 0 ; i < 100; ++i) {
            WritableTicker clock = new WritableTicker();
            LifespanTracker tracker = new LifespanTracker(maxDuration, 100, clock);
            CountDownLatch latch = new CountDownLatch(nTasks);
            Task[] tasks = generateTasks(nTasks, tracker, clock, maxDuration);
            ExecutorService es = Executors.newFixedThreadPool(20);

            int sum = 0;
            for (Task t : tasks) {
                if (t.action == 1) {
                    sum += t.n;
                }
                CompletableFuture.runAsync(t, es)
                        .whenComplete((aVoid, throwable) -> {
                            latch.countDown();
                        });
            }
            latch.await();
            int purged = 0;
            for (Task t : tasks) {
                if (t.action == 2) {
                    purged += t.purged;
                }}
            clock.add(maxDuration);
            purged += tracker.purge();
            Assert.assertEquals(sum, purged);
        }
        Assert.assertTrue(System.currentTimeMillis() - startMs < 20000);
    }

    private Task[] generateTasks(int n, LifespanTracker tracker, WritableTicker testClock, Duration maxDuration) {
        Random rand = new Random(12345);
        Task[] result = new Task[n];
        for (int i = 0 ; i < n ; ++i) {
            Task t = new Task(tracker, testClock);
            t.action = Math.abs(rand.nextInt()) % 3;
            switch (t.action) {
                case 0 :
                    t.d = Duration.ofMillis(rand.nextInt(5));
                case 1 :
                    t.n = rand.nextInt(100);
                    t.d = Duration.ofMillis(Math.abs(rand.nextLong() % maxDuration.toMillis()) / 2);
                default:
                    break;
            }
            result[i] = t;
        }

        return result;
    }

    private static class Task implements Runnable {
        int action; //0 clock.add() //1 counter.increase //2 counter.get
        int n;
        Duration d;
        Long purged;
        final LifespanTracker tracker;
        final WritableTicker clock;

        Task(LifespanTracker tracker, WritableTicker testClock) {
            this.tracker = tracker;
            this.clock = testClock;
        }

        @Override
        public void run() {
            switch (action) {
                case 0:
                    clock.add(d);
                    break;
                case 1:
                    tracker.add(n, d);
                    break;
                case 2:
                    purged = tracker.purge();
                default:
                    break;
            }
        }
    }
}

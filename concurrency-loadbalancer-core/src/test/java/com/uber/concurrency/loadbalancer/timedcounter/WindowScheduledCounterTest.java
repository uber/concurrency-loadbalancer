package com.uber.concurrency.loadbalancer.timedcounter;

import com.google.common.base.Ticker;
import com.uber.concurrency.loadbalancer.utils.WritableTicker;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import java.time.Duration;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class WindowScheduledCounterTest {

    @Test
    public void testSingleThread() {
        WritableTicker clock = new WritableTicker();

        LifespanTracker tracker = new LifespanTracker(Duration.ofMinutes(5), 100, clock);
        Consumer<Long> mockConsumer = Mockito.mock(Consumer.class);
        WindowScheduledCounter scheduledCounter = new WindowScheduledCounter(tracker, mockConsumer);
        Random r = new Random(12345);
        for (int i = 0; i < 10; ++i) {
            long delaySeconds = r.nextInt(100);
            long nanoScheduled = scheduledCounter.internalSchedule(i, Duration.ofSeconds(delaySeconds));
            long diff = nanoScheduled - clock.read();
            clock.add(Duration.ofNanos(diff));
            scheduledCounter.check();
        }
        ArgumentCaptor<Long> captor = ArgumentCaptor.forClass(Long.class);
        // 0 doesn't push to consumer
        Mockito.verify(mockConsumer, Mockito.times(9)).accept(captor.capture());
        int sum = 0;
        for (long i :  captor.getAllValues()){
            sum += i;
        }
        Assert.assertEquals(45, sum);
        long nanoScheduled = scheduledCounter.internalSchedule(20, Duration.ofSeconds(1));
        long diff = nanoScheduled - clock.read();
        clock.add(Duration.ofNanos(diff));
        scheduledCounter.check();
        Mockito.verify(mockConsumer, Mockito.times(10)).accept(captor.capture());
        Assert.assertEquals(20, captor.getValue().intValue());
    }

    @Test
    public void testMultiThread() throws InterruptedException {
        TestReactor reactor = new TestReactor();
        WindowScheduledCounter scheduledCounter = WindowScheduledCounter.newBuilder()
                .withMaxDelay(Duration.ofSeconds(1))
                .withNumWindow(100)
                .of(reactor);
        ExecutorService es = Executors.newFixedThreadPool(20);
        int tasks = 1000;
        Random r = new Random(12345);
        int expected = 0;
        CountDownLatch latch = new CountDownLatch(tasks);
        for (int i = 0; i < tasks; ++i) {
            Task t = new Task(scheduledCounter, r);
            expected += t.n;
            CompletableFuture.runAsync(t, es)
                    .whenComplete((aVoid, throwable) -> {
                        reactor.setFailure(r.nextInt(2) == 0);
                        latch.countDown();
                    });
        }
        latch.await();
        reactor.setFailure(false);
        Thread.sleep(2000);
        scheduledCounter.check();
        Assert.assertEquals(expected, reactor.sum.get());
    }

    class Task implements Runnable {
        ScheduledCounter scheduledCounter;
        int n;
        long delayMs;
        Task(ScheduledCounter scheduledCounter, Random rand) {
            this.scheduledCounter = scheduledCounter;
            n = rand.nextInt(1000);
            delayMs = rand.nextInt(4000);
        }

        @Override
        public void run() {
            scheduledCounter.schedule(n, Duration.ofMillis(delayMs));
            scheduledCounter.check();
        }
    }

    @Test
    public void testScheduleWithoutDuration() {
        WritableTicker clock = new WritableTicker();
        AtomicLong result = new AtomicLong();
        WindowScheduledCounter counter = new WindowScheduledCounter(new LifespanTracker(Duration.ofSeconds(30), 10, clock), o->result.addAndGet(o));
        Supplier<Long> supplier = () -> {
            counter.check();
            return result.get();
        };
        Assert.assertEquals(0, supplier.get().intValue());
        counter.schedule(1); //count should sustain for 30s
        Assert.assertEquals(0, supplier.get().intValue());

        clock.add(Duration.ofSeconds(14)); //14s
        Assert.assertEquals(0, supplier.get().intValue());

        clock.add(Duration.ofSeconds(19)); //33s
        Assert.assertEquals(1, supplier.get().intValue());
    }

    @Test
    public void testExampleCase() {
        WritableTicker clock = new WritableTicker();
        AtomicLong result = new AtomicLong();
        WindowScheduledCounter counter = new WindowScheduledCounter(new LifespanTracker(Duration.ofSeconds(30), 10, clock), o->result.addAndGet(o));
        Supplier<Long> supplier = () -> {
            counter.check();
            return result.get();
        };
        Assert.assertEquals(0, supplier.get().intValue());

        counter.schedule(1, Duration.ofSeconds(10)); //count should sustain for 10s
        Assert.assertEquals(0, supplier.get().intValue());

        clock.add(Duration.ofSeconds(6));  //6s
        Assert.assertEquals(0, supplier.get().intValue());

        clock.add(Duration.ofSeconds(14)); //20s
        counter.schedule(1, Duration.ofSeconds(15)); //count should sustain for 15s
        Assert.assertEquals(1, supplier.get().intValue());

        clock.add(Duration.ofSeconds(5));  //25s
        Assert.assertEquals(1, supplier.get().intValue());

        clock.add(Duration.ofSeconds(10));  //35
        Assert.assertEquals(1, supplier.get().intValue());

        clock.add(Duration.ofSeconds(1));  //36
        Assert.assertEquals(2, supplier.get().intValue()); //count sustained for 16 because of window precision
    }

    @Test
    public void testNegativeTimeToLive() {
        WritableTicker clock = new WritableTicker();
        AtomicLong result = new AtomicLong();
        WindowScheduledCounter counter = new WindowScheduledCounter(new LifespanTracker(Duration.ofSeconds(30), 10, clock), o->result.addAndGet(o));
        Supplier<Long> supplier = () -> {
            counter.check();
            return result.get();
        };
        Assert.assertEquals(0, supplier.get().intValue());
        counter.schedule(1, Duration.ofSeconds(-10)); //count should sustain for 10s
        Assert.assertEquals(0, supplier.get().intValue());
        clock.add(Duration.ofSeconds(2)); //2s
        Assert.assertEquals(0, supplier.get().intValue());
        clock.add(Duration.ofSeconds(1)); //3s
        Assert.assertEquals(1, supplier.get().intValue());
    }


    @Test
    public void testWindowTimedCounterBuilder() {
        WindowScheduledCounter.Builder builder = WindowScheduledCounter
                .newBuilder()
                .withMaxDelay(Duration.ofSeconds(100))
                .withNumWindow(1000);
        WindowScheduledCounter counter = builder.of(null);
        Assert.assertEquals(counter.lifespanTracker.getMaxAge(), Duration.ofSeconds(100));
        Assert.assertEquals(counter.lifespanTracker.getTotalWindows(), 1000);
        Assert.assertEquals(counter.lifespanTracker.getWindowNanos(), 100000000);
        Assert.assertEquals(counter.lifespanTracker.getTicker(), Ticker.systemTicker());
    }

    static class TestReactor implements Consumer<Long> {

        AtomicLong sum = new AtomicLong();
        volatile boolean failure = false;

        void setFailure(boolean failure) {
            this.failure = failure;
        }

        @Override
        public void accept(Long value) {
            if (failure) {
                throw new RuntimeException("failed to consume");
            } else {
                sum.addAndGet(value);
            }
        }
    }
}

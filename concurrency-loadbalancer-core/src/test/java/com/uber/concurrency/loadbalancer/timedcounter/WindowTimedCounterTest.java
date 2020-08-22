package com.uber.concurrency.loadbalancer.timedcounter;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.function.Consumer;

public class WindowTimedCounterTest {
    private WindowTimedCounter windowTimedCounter;
    private WindowScheduledCounter mockWindowScheduledCounter;

    @Before
    public void setup() {
        WindowScheduledCounter.Builder builder = Mockito.mock(WindowScheduledCounter.Builder.class);
        mockWindowScheduledCounter = Mockito.mock(WindowScheduledCounter.class);
        Mockito.doReturn(mockWindowScheduledCounter).when(builder).of(Mockito.any(Consumer.class));
        windowTimedCounter = new WindowTimedCounter(builder);
    }

    @Test
    public void testAdd() {
        windowTimedCounter.add(1);
        Mockito.verify(mockWindowScheduledCounter, Mockito.times(1)).schedule(-1);
    }

    @Test
    public void testAddWithDuration() {
        Duration duration = Duration.ofSeconds(10);
        windowTimedCounter.add(1, duration);
        Mockito.verify(mockWindowScheduledCounter, Mockito.times(1)).schedule(-1, duration);
    }

    @Test
    public void testCheck() {
        windowTimedCounter.check();
        Mockito.verify(mockWindowScheduledCounter, Mockito.times(1)).check();
    }

    @Test
    public void testGet() {
        windowTimedCounter.add(9);
        Assert.assertEquals(9, windowTimedCounter.get());
    }
}

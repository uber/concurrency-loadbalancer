package com.uber.concurrency.loadbalancer.utils;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class IntervalLimiterTest {
    private IntervalLimiter limiter;
    private WritableTicker ticker;

    @Before
    public void setup() {
        ticker = new WritableTicker();
        new IntervalLimiter(TimeUnit.SECONDS.toNanos(5));
        limiter = new IntervalLimiter(TimeUnit.SECONDS.toNanos(5), ticker);
    }

    @Test
    public void test() {
        long ageNano = limiter.acquire();
        Assert.assertEquals(-1, ageNano);

        ticker.add(Duration.ofSeconds(4));
        ageNano = limiter.acquire();
        Assert.assertEquals(-1, ageNano);

        ticker.add(Duration.ofSeconds(2));
        ageNano = limiter.acquire();
        Assert.assertEquals(TimeUnit.SECONDS.toNanos(6), ageNano);

        ageNano = limiter.acquire();
        Assert.assertEquals(-1, ageNano);
    }
}

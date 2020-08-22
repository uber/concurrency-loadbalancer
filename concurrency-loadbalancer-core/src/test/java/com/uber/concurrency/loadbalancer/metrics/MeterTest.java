package com.uber.concurrency.loadbalancer.metrics;

import com.uber.concurrency.loadbalancer.utils.WritableTicker;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;

public class MeterTest {
    private static final double DELTA = 0.0001;
    Meter meter;
    private WritableTicker ticker;

    @Before
    public void setup() {
        ticker = new WritableTicker();
        meter = new Meter(ticker);
    }

    @Test
    public void test() {
        meter.mark(3);
        double rate = meter.getRate();
        Assert.assertEquals("",0, rate, DELTA);

        meter.mark(4);
        ticker.add(Duration.ofSeconds(4));
        rate = meter.getRate();
        Assert.assertEquals("",0, rate, DELTA);

        meter.mark(5);
        ticker.add(Duration.ofSeconds(2));
        rate = meter.getRate();
        Assert.assertEquals("",2, rate, 0.5);
    }
}

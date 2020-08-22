package com.uber.concurrency.loadbalancer;

import com.uber.concurrency.loadbalancer.utils.WritableTicker;
import com.uber.m3.tally.Counter;
import com.uber.m3.tally.Gauge;
import com.uber.m3.tally.Scope;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class TallyMetricsTaskListenerTest {
    Scope mockScope;
    TallyMetricsTaskListener<String> listener;
    List<String> tasks;
    WritableTicker testTicker;
    Counter mockRequestCounter;
    Counter mockSuccessCounter;
    Counter mockFailureCounter;
    Gauge mockM1RateGauge;
    Gauge mockM5RateGauge;
    Gauge mockM15RateGauge;

    @Before
    public void setup() {
        mockRequestCounter = Mockito.mock(Counter.class);
        mockSuccessCounter = Mockito.mock(Counter.class);
        mockFailureCounter = Mockito.mock(Counter.class);
        mockM1RateGauge = Mockito.mock(Gauge.class);
        mockM5RateGauge = Mockito.mock(Gauge.class);
        mockM15RateGauge = Mockito.mock(Gauge.class);
        mockScope = Mockito.mock(Scope.class);
        Mockito.doReturn(mockRequestCounter).when(mockScope).counter("requestRate");
        Mockito.doReturn(mockSuccessCounter).when(mockScope).counter("successRate");
        Mockito.doReturn(mockFailureCounter).when(mockScope).counter("failureRate");
        Mockito.doReturn(mockM1RateGauge).when(mockScope).gauge("m1RateStddev");
        Mockito.doReturn(mockM5RateGauge).when(mockScope).gauge("m5RateStddev");
        Mockito.doReturn(mockM15RateGauge).when(mockScope).gauge("m15RateStddev");
        Mockito.doReturn(mockScope).when(mockScope).tagged(Mockito.anyMap());
        testTicker = new WritableTicker();
        tasks = Arrays.asList("a", "b", "c", "d", "e", "f");
        listener = TallyMetricsTaskListener.newBuilder(String.class)
                .withName("testLB")
                .withTicker(testTicker)
                .withTaskNameMapper(v -> v+"Name")
                .build(mockScope);

        ArgumentCaptor<Map> mapArgumentCaptor = ArgumentCaptor.forClass(Map.class);
        Mockito.verify(mockScope, Mockito.times(1)).tagged(mapArgumentCaptor.capture());
        Map map = mapArgumentCaptor.getValue();
        Assert.assertEquals("testLB", map.get("loadBalancer"));
    }

    @Test
    public void testReportRequestRates() {
        listener.onCreate("a");
        ArgumentCaptor<Map> mapArgumentCaptor = ArgumentCaptor.forClass(Map.class);
        Mockito.verify(mockScope, Mockito.times(2)).tagged(mapArgumentCaptor.capture());
        Map map = mapArgumentCaptor.getValue();
        Assert.assertEquals("aName", map.get("task"));
        Mockito.verify(mockRequestCounter, Mockito.times(1)).inc(1);
    }

    @Test
    public void testReportSuccessRates() {
        listener.onComplete("b", true);
        ArgumentCaptor<Map> mapArgumentCaptor = ArgumentCaptor.forClass(Map.class);
        Mockito.verify(mockScope, Mockito.times(2)).tagged(mapArgumentCaptor.capture());
        Map map = mapArgumentCaptor.getValue();
        Assert.assertEquals("bName", map.get("task"));
        Mockito.verify(mockSuccessCounter, Mockito.times(1)).inc(1);
    }

    @Test
    public void testReportFailureRates() {
        listener.onComplete("b", false);
        ArgumentCaptor<Map> mapArgumentCaptor = ArgumentCaptor.forClass(Map.class);
        Mockito.verify(mockScope, Mockito.times(2)).tagged(mapArgumentCaptor.capture());
        Map map = mapArgumentCaptor.getValue();
        Assert.assertEquals("bName", map.get("task"));
        Mockito.verify(mockFailureCounter, Mockito.times(1)).inc(1);
    }

    @Test
    public void testReportStddevs() {
        for (String t : tasks) {
            listener.onCreate(t);
        }
        testTicker.add(Duration.ofSeconds(6));
        listener.onCreate("a");
        Mockito.verify(mockScope, Mockito.times(8)).tagged(Mockito.anyMap());
        ArgumentCaptor<Double> doubleArgumentCaptor = ArgumentCaptor.forClass(Double.class);
        Mockito.verify(mockM1RateGauge, Mockito.times(1)).update(doubleArgumentCaptor.capture());
        Mockito.verify(mockM5RateGauge, Mockito.times(1)).update(doubleArgumentCaptor.capture());
        Mockito.verify(mockM15RateGauge, Mockito.times(1)).update(doubleArgumentCaptor.capture());
        Assert.assertEquals(3, doubleArgumentCaptor.getAllValues().size());
        for (Double d : doubleArgumentCaptor.getAllValues()) {
            Assert.assertTrue(d > 0.05);
        }
    }

    @Test
    public void testWithMetric() {
        ArrayList<String> entries = new ArrayList<String>() {{add("a");}};
        Scope mockScope = Mockito.mock(Scope.class);
        Counter mockCounter = Mockito.mock(Counter.class);
        Mockito.doReturn(mockScope).when(mockScope).tagged(Mockito.anyMap());
        Mockito.doReturn(mockCounter).when(mockScope).counter("requestRate");

        TallyMetricsTaskListener listener = TallyMetricsTaskListener.newBuilder(String.class)
                .withName("abcLB")
                .build(mockScope);

        HeapConcurrencyLoadBalancer<String> loadBalancer = HeapConcurrencyLoadBalancer.newBuilder(String.class)
                .withTasks(entries)
                .withTaskListener(listener)
                .build();

        CompletableTask<String> task1 = loadBalancer.next();
        Assert.assertEquals("a", task1.getTask());
        Mockito.verify(mockCounter, Mockito.times(1)).inc(1);
    }
}

package com.uber.concurrency.loadbalancer;

import io.opentracing.Span;
import io.opentracing.Tracer;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Map;

public class TracingTaskListenerTest {
    TracingTaskListener<String> listener;
    Tracer mockTracer;
    Span mockSpan;

    @Before
    public void setup() {
        mockTracer = Mockito.mock(Tracer.class);
        mockSpan = Mockito.mock(Span.class);
        Mockito.doReturn(mockSpan).when(mockTracer).activeSpan();
        listener = TracingTaskListener.newBuilder(String.class)
                .withName("testLB")
                .withTaskNameMapper(o-> o+ "Name")
                .build(mockTracer);
    }

    @Test
    public void testOnCreate() {
        listener.onCreate("a");
        ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
        Mockito.verify(mockSpan, Mockito.times(1)).log(captor.capture());
        Assert.assertEquals("aName", captor.getValue().get("LB.testLB"));
    }

    @Test
    public void testWithTracing() {
        ArrayList<String> entries = new ArrayList<String>() {{add("a");}};
        Tracer mockTracer = Mockito.mock(Tracer.class);
        Span mockSpan = Mockito.mock(Span.class);
        Mockito.doReturn(mockSpan).when(mockTracer).activeSpan();

        TracingTaskListener<String> listener = TracingTaskListener.newBuilder(String.class)
                .withName("abcLB")
                .withTaskNameMapper(o->o+"Name")
                .build(mockTracer);

        HeapConcurrencyLoadBalancer<String> loadBalancer = HeapConcurrencyLoadBalancer.newBuilder(String.class)
                .withTasks(entries)
                .withTaskListener(listener)
                .build();

        CompletableTask<String> task1 = loadBalancer.next();
        Assert.assertEquals("a", task1.getTask());
        Mockito.verify(mockSpan, Mockito.times(1)).log(Mockito.anyMap());
    }
}

package com.uber.concurrency.loadbalancer;


import org.junit.Assert;
import org.junit.Test;

public class CompletableTaskTest {

    @Test
    public void testNoopCompletableTaskTest() {
        CompletableTask task = CompletableTask.ofNoop("a");
        Assert.assertEquals("a", task.getTask());
        Assert.assertTrue(task.complete(true));
        Assert.assertTrue(task.complete());
    }
}

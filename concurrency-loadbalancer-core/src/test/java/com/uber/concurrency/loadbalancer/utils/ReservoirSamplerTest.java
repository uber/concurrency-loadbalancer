package com.uber.concurrency.loadbalancer.utils;

import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

public class ReservoirSamplerTest {

    @Test
    public void testSampleOne() {
        String[] strs = new String[10];
        for (int i = 0 ; i < strs.length ; ++i) {
            strs[i] = "e" + i;
        }
        Map<String, Integer> freqMap = new HashMap<>();
        ReservoirSampler<String> sampler = new ReservoirSampler<>(1, new Random(12345));
        int repeat = 10000;
        for (int i = 0; i < repeat; ++i) {
            sampler.reset();
            for (int j = 0; j < strs.length; ++j) {
                sampler.sample(strs[j]);
            }
            freqMap.put(sampler.getSample(), freqMap.getOrDefault(sampler.getSample(), 0) + 1);
        }
        int sum = 0;
        int expected = repeat / strs.length;
        for (Map.Entry<String, Integer> entry : freqMap.entrySet()) {
            sum += entry.getValue();
            Assert.assertTrue(Math.abs(1 - entry.getValue() / (double)expected) < 0.1);
        }
        Assert.assertEquals(repeat, sum);
    }

    @Test
    public void testSampleMultiple() {
        String[] strs = new String[10];
        int nSample = 5;
        for (int i = 0 ; i < strs.length ; ++i) {
            strs[i] = "e" + i;
        }
        Map<String, Integer> freqMap = new HashMap<>();
        ReservoirSampler<String> sampler = new ReservoirSampler<>(nSample, new Random(12345));
        int repeat = 10000;
        for (int i = 0; i < repeat; ++i) {
            sampler.reset();
            for (int j = 0; j < strs.length; ++j) {
                sampler.sample(strs[j]);
            }
            Iterator<String> iter = sampler.getSamples().iterator();
            while(iter.hasNext()) {
                String str = iter.next();
                freqMap.put(str, freqMap.getOrDefault(str, 0) + 1);
            }
        }
        int sum = 0;
        int expected = nSample * repeat / strs.length;
        for (Map.Entry<String, Integer> entry : freqMap.entrySet()) {
            sum += entry.getValue();
            Assert.assertTrue(Math.abs(1 - entry.getValue() / (double)expected) < 0.1);
        }
        Assert.assertEquals(repeat * nSample, sum);
    }
}

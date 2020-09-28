package com.uber.concurrency.loadbalancer.utils;

import org.junit.Assert;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class HashIndexedPriorityQueueTest {

    @Test
    public void testCompareWithPriorityQueue() {
        PriorityQueue<Integer> pq = new PriorityQueue<>();
        HashIndexedPriorityQueue<Integer> hpq = new HashIndexedPriorityQueue<>();
        Set<Integer> numbers = new HashSet<>();
        Random random = new Random(12345);
        for (int i = 0; i < 1000; ++i) {
            numbers.add(random.nextInt());
        }
        for (int i : numbers) {
            boolean r1 = pq.add(i);
            boolean r2 = hpq.add(i);
            hpq.validate();
            Assert.assertEquals(r1, r2);
        }
        Assert.assertEquals(pq.size(), hpq.size());
        for (int i = 0; i < numbers.size(); ++i) {
            Assert.assertEquals(pq.peek(), hpq.peek());
            int v1 = pq.poll();
            int v2 = hpq.poll();
            hpq.validate();
            Assert.assertEquals(v1, v2);
        }
    }

    @Test
    public void testCompareWithHashSet() {
        Set<Integer> hashSet = new HashSet<>();
        HashIndexedPriorityQueue<Integer> hpq = new HashIndexedPriorityQueue<>();

        Random random = new Random(12345);
        for (int i = 0; i < 1000; ++i) {
            int v = random.nextInt(100);
            boolean b1 = hashSet.contains(v);
            boolean b2 = hpq.contains(v);
            Assert.assertEquals(b1, b2);
            if (!b1) {
                hashSet.add(v);
                hpq.add(v);
                hpq.validate();
            } else {
                hashSet.remove(v);
                hpq.remove(v);
                hpq.validate();
            }
        }
    }

    @Test
    public void testIterator() {
        HashIndexedPriorityQueue<Integer> hpq = new HashIndexedPriorityQueue<>();
        Set<Integer> numbers = new HashSet<>();
        Random random = new Random(12345);
        for (int i = 0; i < 1000; ++i) {
            numbers.add(random.nextInt());
        }
        for (int i : numbers) {
            hpq.add(i);
            hpq.validate();
        }
        Assert.assertEquals(numbers.size(), hpq.size());
        Iterator<Integer> iter = hpq.iterator();
        int n = 0;
        while(iter.hasNext()) {
            int v = iter.next();
            numbers.contains(v);
            hpq.validate();
            n++;
            try {
                iter.remove();
                Assert.assertTrue(false);
            } catch (UnsupportedOperationException e) {

            }
        }
    }

    @Test
    public void testComparator() {
        Comparator<Entity> comparator = Comparator.comparingInt(o -> o.value);
        PriorityQueue<Entity> pq = new PriorityQueue<>(comparator);
        HashIndexedPriorityQueue<Entity> hpq = new HashIndexedPriorityQueue<>(comparator);
        Set<Integer> numbers = new HashSet<>();
        Random random = new Random(12345);
        for (int i = 0; i < 1000; ++i) {
            numbers.add(random.nextInt());
        }
        for (int i : numbers) {
            Entity e = new Entity(i);
            boolean r1 = pq.add(e);
            boolean r2 = hpq.add(e);
            hpq.validate();
            Assert.assertEquals(r1, r2);
        }
        Assert.assertEquals(pq.size(), hpq.size());
        for (int i = 0; i < numbers.size(); ++i) {
            Assert.assertEquals(pq.peek().value, hpq.peek().value);
            Entity e1 = pq.poll();
            Entity e2 = hpq.poll();
            hpq.validate();
            Assert.assertEquals(e1.value, e2.value);
        }
    }

    @Test
    public void testFairness() {
        Comparator<Entity> comparator = Comparator.comparingInt(o -> o.value);
        HashIndexedPriorityQueue<Entity> hpq = new HashIndexedPriorityQueue<>(comparator);
        int keys = 128;
        for (int i = 0; i < keys; ++i) {
            hpq.add(new Entity(i, 0));
        }
        Map<Integer, AtomicInteger> idFreq = new HashMap<>();
        int totalFreq = 1000000;
        for (int i=0; i< totalFreq; ++i) {
            Entity s = hpq.poll();
            idFreq.computeIfAbsent(s.id, o->new AtomicInteger()).incrementAndGet();
            s.value += 1;
            hpq.offer(s);
            hpq.remove(s);
            s.value -= 1;
            hpq.offer(s);
        }
        for (int i = 0 ; i < keys; ++i) {
            int freq = idFreq.get(i).get();
            double prob = freq / (double)totalFreq;
            double expected = 1 / (double)keys;
            Assert.assertTrue( Math.abs(prob - expected) < expected * 0.1);
        }
    }

    @Test
    public void testUpdateOrderInPlace() {
        Comparator<Entity> comparator = Comparator.comparingInt(o -> o.value);
        PriorityQueue<Entity> pq = new PriorityQueue<>(comparator);
        HashIndexedPriorityQueue<Entity> hpq = new HashIndexedPriorityQueue<>(comparator);
        Set<Integer> numbers = new HashSet<>();
        Random random = new Random(12345);
        ArrayList<Entity> array = new ArrayList<>();
        for (int i = 0; i < 1000; ++i) {
            numbers.add(random.nextInt());
        }
        for (int i : numbers) {
            Entity e = new Entity(i);
            boolean r1 = pq.add(e);
            boolean r2 = hpq.add(e);
            array.add(e);
            hpq.validate();
            Assert.assertEquals(r1, r2);
        }
        Set<Integer> moreNumbers = new HashSet<>();
        for (int i = 0; i < 1000; ++i) {
            int v = random.nextInt();
            if (!numbers.contains(v)) {
                moreNumbers.add(v);
            }
        }
        //update entity
        for (int i : moreNumbers) {
            int index = random.nextInt(array.size());
            Entity e = array.get(index);
            e.value = i;
            pq.remove(e); //O(n)
            pq.offer(e);
            hpq.offer(e); //O(logn)
            hpq.validate();
        }
        for (int i = 0; i < numbers.size(); ++i) {
            Assert.assertEquals(pq.peek().value, hpq.peek().value);
            Entity e1 = pq.poll();
            Entity e2 = hpq.poll();
            hpq.validate();
            Assert.assertEquals(e1.value, e2.value);
        }
    }

    private class Entity {
        int id;
        int value;
        Entity(int id, int v) {
            this.id = id;
            this.value = v;
        }
        Entity(int v) {
            this.value = v;
        }
    }
}

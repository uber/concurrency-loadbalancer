package com.uber.concurrency.loadbalancer.internal;

import java.time.Duration;

public class TaskConcurrencyDelegator<T> implements TaskConcurrency<T> {
    private final TaskConcurrency<T> delegate;

    public TaskConcurrencyDelegator(TaskConcurrency<T> delegate) {
        this.delegate = delegate;
    }

    public T getTask() {
        return delegate.getTask();
    }

    public void acquire() {
        delegate.acquire();
    }

    public void complete(boolean succeed, Duration latency) {
        delegate.complete(succeed, latency);
    }

    public void acquire(int n) {
        delegate.acquire(n);
    }

    public void complete(int n, Duration latency) {
        delegate.complete(n, latency);
    }

    public int getConcurrency() {
        return delegate.getConcurrency();
    }

    public void syncState() {
        delegate.syncState();
    }

    @Override
    public int compareTo(TaskConcurrency o) {
        if (o instanceof TaskConcurrencyDelegator) {
            return delegate.compareTo(((TaskConcurrencyDelegator) o).delegate);
        } else {
            return delegate.compareTo(o);
        }
    }
}

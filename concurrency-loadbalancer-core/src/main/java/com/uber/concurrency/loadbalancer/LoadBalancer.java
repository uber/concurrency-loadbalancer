package com.uber.concurrency.loadbalancer;

/**
 * LoadBalancer distribute interaction across a collection of entities
 *
 * @param <T> the Entity type
 */
public interface LoadBalancer<T> {
    /**
     * get an entity to interact with.
     *
     * @return the t
     */
    T next();
}

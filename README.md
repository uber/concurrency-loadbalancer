# Overview
Generally, load balancer is a piece of software or hardware that distributes workload among multiple partitions. 
Take microservice domain as an example, a load balancer distributes requests among a cluster of servers. 
There are many load balancing algorithms, such as round-robin, random, hashing, least work, least latency etc.
Here we are introducing a generic load balancer library that aims to improve both throughput and latency of application when system is under load,
prevent cascading failure caused by service degradation. 

# Background
Concurrency is the number of requests a system serves at a given time.
For a given system, concurrency is bounded by resources, such as memory, CPU,  thread, connection etc.
According to the little’s law, for a system in steadyC state, concurrency is the product of the average service time and the average service rate (L=λ*W).
So, for given concurrency, request throughput is inversely proportional to request latency.

Systems with requests under or over concurrency limits behave differently. 
A system with concurrency below concurrency limit usually has stable request latency, and its request throughput increases linearly with concurrency.
On the contrary, A system with concurrency above concurrency limit indicates there are queues forming, and request latency increases linearly with the number of requests.

Practically, system degradation happens. Generally, there are two types of degradation, high request latency and high failure rate.
When latency increases, throughput decreases. With downstream system throughput drops below upstream throughput, request queue build up and it could cause high request latency propagated to upstream service.
Similarly, when failure happens frequently in downstream service, those failures may propagate back to upstream service and cause upstream service degradation as well.

An efficient load balancing algorithm should be able to reduce cascading failure by preventing saturation of partial downstream services propagated to upstream service.
Note that, if saturation happens on all downstream services, it should not be the responsibility of the load balancer to prevent cascading failure, instead It's more about load shedding than load balancing in that case. 

# Solution
To solve the problem we invented this loadbalancer 
- Inspired by the Little’s law, The load balancer keeps track of 3 factors (concurrency, latency, and throughput) of each partition.
- An efficient load balancer should suppress queues from building up, and concurrency is the factor that can reflect the fact of queuing before a request complete.
so load balancer first compares concurrency of each partition, and distributes requests to the least concurrency partition. 
When concurrency is the same across partitions,  we are in a situation where either no queue or queue can’t be prevented. There are following strategies to handle this case:
    
    - 3.1 Least request - Choose the partition with least completed requests. Similar to round-robin strategy, this sub-strategy values fairness more than efficiency
    
    - 3.2 Least request time - Choose the partition with least aggregated request latency. When latency is the same across partitions, this strategy effectively equals to the least request strategy. 
    However, when there is a degraded partition, request latency of the degraded partition increases, thus, its throughput drops. Conclusively, this sub-strategy values efficiency more than fairness.

- To optimize request success rate, define effective latency for failures, and reflect failure in the term of latency. 
Concretely, when request failed, we keep the concurrency sustained by one for an extra amount of time, and the extra sustain period equals to 
                   
                   effectiveLatency - observedLatency

  It works as a passive health check mechanism to prevent failure propagating from degraded partition to upstream service.


# Example

There are two concrete implementations of the algorithm described above.
ArrayConcurrencyLoadbalancer is implemented in Array, which has constant computational complexity through partition grouping.
HeapConcurrencyLoadbalancer is implemented in hashed priority queue, which has Log(N) computational complexity.

Example to create an ArrayConcurrencyLoadbalancer, and enables LeastTime sub-strategy
```java
ArrayList<String> entries = new ArrayList<String>() {{add("url1"); add("url2"); add("url3");}};

ArrayConcurrencyLoadBalancer<String> loadBalancer = ArrayConcurrencyLoadBalancer.newBuilder(String.class)
     .withTasks(entries)
     .withSubStrategy(SubStrategy.LeastTime)
     .build();
```

Example to create an HeapConcurrencyLoadbalancer
and when request failed, request latency will be treated effectively same as 30 seconds.
```java
ArrayList<String> entries = new ArrayList<String>() {{add("url1"); add("url2");}};

HeapConcurrencyLoadBalancer<String> loadBalancer = HeapConcurrencyLoadBalancer.newBuilder(String.class)
     .withTasks(entries)
     .withFailureEffectiveLatency(Duration.ofSeconds(30))
     .build();
```

# Integration
TODO
GRPC example
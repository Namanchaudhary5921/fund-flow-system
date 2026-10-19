package com.fundflow.concurrency.scheduling;

import com.fundflow.model.ProcessingRequest;

import java.util.List;

/**
 * Common contract for every CPU-scheduling algorithm in this project.
 * The worker threads only talk to this interface, so changing the algorithm
 * means changing ONE object - no worker code changes.
 *
 *   FCFSScheduler        - FIFO, non-preemptive
 *   RoundRobinScheduler  - FIFO + time quantum, preemptive
 *   SJFScheduler         - shortest burst first, non-preemptive
 *   PriorityScheduler    - highest priority first, non-preemptive
 */
public interface Scheduler {

    String name();
    String description();

    /** PRODUCER side: a new request arrives in the ready queue. */
    void submit(ProcessingRequest request);

    /** CONSUMER side: a worker asks for the next request; waits up to timeoutMs, returns null on timeout. */
    ProcessingRequest takeNext(long timeoutMs) throws InterruptedException;

    /** Called when a request used its time slice but is not finished (only matters for preemptive algorithms). */
    void requeue(ProcessingRequest request);

    /** Number of steps a worker may run before being preempted. Integer.MAX_VALUE = run to completion. */
    int timeQuantum();

    int size();
    List<ProcessingRequest> snapshot();   // for the admin queue monitor
    List<ProcessingRequest> drainAll();   // used when switching algorithms at run time
}

package com.fundflow.concurrency.scheduling;

import com.fundflow.model.ProcessingRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * ===================== FCFS - First Come First Served =====================
 * Idea     : the request that arrived first is served first (a plain queue).
 * Type     : NON-preemptive. Once a worker starts a request it runs it to the end.
 * Data str.: LinkedBlockingQueue (FIFO).
 * Pros     : simplest, fair in arrival order, no starvation.
 * Cons     : "convoy effect" - one long request makes all short ones wait.
 * =========================================================================
 */
public class FCFSScheduler implements Scheduler {

    private final BlockingQueue<ProcessingRequest> queue = new LinkedBlockingQueue<>();

    @Override public String name() { return "FCFS"; }
    @Override public String description() { return "First Come First Served - non-preemptive FIFO"; }

    @Override public void submit(ProcessingRequest r) { queue.add(r); }

    @Override public ProcessingRequest takeNext(long timeoutMs) throws InterruptedException {
        return queue.poll(timeoutMs, TimeUnit.MILLISECONDS);      // head of the queue = oldest request
    }

    @Override public void requeue(ProcessingRequest r) { queue.add(r); }   // never used: quantum is infinite

    @Override public int timeQuantum() { return Integer.MAX_VALUE; }       // run to completion

    @Override public int size() { return queue.size(); }
    @Override public List<ProcessingRequest> snapshot() { return new ArrayList<>(queue); }
    @Override public List<ProcessingRequest> drainAll() { List<ProcessingRequest> l = new ArrayList<>(); queue.drainTo(l); return l; }
}

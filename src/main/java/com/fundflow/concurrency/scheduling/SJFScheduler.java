package com.fundflow.concurrency.scheduling;

import com.fundflow.model.ProcessingRequest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * ===================== SJF - Shortest Job First =====================
 * Idea     : the request with the SMALLEST burst time (fewest steps) goes first.
 *            Here a DEPOSIT / WITHDRAW (3 steps) beats a TRANSFER (4 steps).
 * Type     : NON-preemptive version.
 * Data str.: PriorityBlockingQueue ordered by burst time (ties -> older first).
 * Pros     : gives the minimum average waiting time.
 * Cons     : long requests can STARVE; burst time must be known in advance
 *            (we know it, because every request type has a fixed list of steps).
 * ====================================================================
 */
public class SJFScheduler implements Scheduler {

    private final PriorityBlockingQueue<ProcessingRequest> queue = new PriorityBlockingQueue<>(64,
            Comparator.<ProcessingRequest>comparingInt(r -> r.getType().burstTime())
                      .thenComparingLong(ProcessingRequest::getArrivalTime)
                      .thenComparingLong(ProcessingRequest::getRequestId));

    @Override public String name() { return "SJF"; }
    @Override public String description() { return "Shortest Job First - non-preemptive, fewest steps first"; }

    @Override public void submit(ProcessingRequest r) { queue.add(r); }

    @Override public ProcessingRequest takeNext(long timeoutMs) throws InterruptedException {
        return queue.poll(timeoutMs, TimeUnit.MILLISECONDS);     // head = shortest job
    }

    @Override public void requeue(ProcessingRequest r) { queue.add(r); }   // never used (non-preemptive)

    @Override public int timeQuantum() { return Integer.MAX_VALUE; }

    @Override public int size() { return queue.size(); }
    @Override public List<ProcessingRequest> snapshot() { List<ProcessingRequest> l = new ArrayList<>(queue); l.sort(queue.comparator()); return l; }
    @Override public List<ProcessingRequest> drainAll() { List<ProcessingRequest> l = new ArrayList<>(); queue.drainTo(l); return l; }
}

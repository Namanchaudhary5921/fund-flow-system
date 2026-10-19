package com.fundflow.concurrency.scheduling;

import com.fundflow.model.ProcessingRequest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * ===================== PRIORITY SCHEDULING =====================
 * Idea     : every request carries a priority (1 = most urgent, 10 = least).
 *            The most urgent request is always served first.
 *            Example: an admin / large urgent transfer could use priority 1.
 * Type     : NON-preemptive version.
 * Data str.: PriorityBlockingQueue ordered by priority (ties -> FCFS order).
 * Pros     : important work is done first.
 * Cons     : low-priority requests can STARVE. A real fix is "aging"
 *            (raise priority the longer a request waits) - good viva topic.
 * ===============================================================
 */
public class PriorityScheduler implements Scheduler {

    private final PriorityBlockingQueue<ProcessingRequest> queue = new PriorityBlockingQueue<>(64,
            Comparator.<ProcessingRequest>comparingInt(ProcessingRequest::getPriority)
                      .thenComparingLong(ProcessingRequest::getArrivalTime)
                      .thenComparingLong(ProcessingRequest::getRequestId));

    @Override public String name() { return "Priority"; }
    @Override public String description() { return "Priority Scheduling - non-preemptive, 1 = highest priority"; }

    @Override public void submit(ProcessingRequest r) { queue.add(r); }

    @Override public ProcessingRequest takeNext(long timeoutMs) throws InterruptedException {
        return queue.poll(timeoutMs, TimeUnit.MILLISECONDS);     // head = highest priority
    }

    @Override public void requeue(ProcessingRequest r) { queue.add(r); }   // never used (non-preemptive)

    @Override public int timeQuantum() { return Integer.MAX_VALUE; }

    @Override public int size() { return queue.size(); }
    @Override public List<ProcessingRequest> snapshot() { List<ProcessingRequest> l = new ArrayList<>(queue); l.sort(queue.comparator()); return l; }
    @Override public List<ProcessingRequest> drainAll() { List<ProcessingRequest> l = new ArrayList<>(); queue.drainTo(l); return l; }
}

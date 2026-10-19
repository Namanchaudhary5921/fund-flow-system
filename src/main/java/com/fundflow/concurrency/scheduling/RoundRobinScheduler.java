package com.fundflow.concurrency.scheduling;

import com.fundflow.model.ProcessingRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * ========================== ROUND ROBIN (RR) ==========================
 * Idea     : every request gets a fixed TIME QUANTUM (here: a number of steps).
 *            If it is not finished when the quantum ends, it is PREEMPTED and
 *            goes to the BACK of the queue; the next request gets the worker.
 * Type     : PREEMPTIVE.
 * Data str.: LinkedBlockingQueue (FIFO) - same queue as FCFS, the difference is
 *            only the quantum + requeue().
 * Pros     : fair, short requests finish quickly, good response time.
 * Cons     : more context switches; a very small quantum = high overhead.
 * Note     : with quantum = Integer.MAX_VALUE, RR behaves exactly like FCFS.
 * ======================================================================
 */
public class RoundRobinScheduler implements Scheduler {

    private final BlockingQueue<ProcessingRequest> queue = new LinkedBlockingQueue<>();
    private final int quantum;

    public RoundRobinScheduler(int quantum) { this.quantum = Math.max(1, quantum); }

    @Override public String name() { return "Round Robin (q=" + quantum + ")"; }
    @Override public String description() { return "Round Robin - preemptive, time quantum = " + quantum + " step(s)"; }

    @Override public void submit(ProcessingRequest r) { queue.add(r); }

    @Override public ProcessingRequest takeNext(long timeoutMs) throws InterruptedException {
        return queue.poll(timeoutMs, TimeUnit.MILLISECONDS);
    }

    /** The "round" in Round Robin: an unfinished request joins the TAIL of the queue again. */
    @Override public void requeue(ProcessingRequest r) { queue.add(r); }

    @Override public int timeQuantum() { return quantum; }

    @Override public int size() { return queue.size(); }
    @Override public List<ProcessingRequest> snapshot() { return new ArrayList<>(queue); }
    @Override public List<ProcessingRequest> drainAll() { List<ProcessingRequest> l = new ArrayList<>(); queue.drainTo(l); return l; }
}

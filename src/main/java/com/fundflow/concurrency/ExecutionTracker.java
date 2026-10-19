package com.fundflow.concurrency;

import com.fundflow.model.ProcessingRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Records WHO ran WHAT and WHEN (a text Gantt chart) and collects the classic
 * scheduling metrics: waiting time, turnaround time and response time.
 */
public class ExecutionTracker {

    /** One "slice" = one continuous run of a request on a worker thread. */
    public record Slice(long requestId, String type, String worker, int fromStep, int toStep, long startMs, long endMs) {}

    public record Metrics(int completed, int failed, double avgWaiting, double avgTurnaround, double avgResponse) {}

    private static final int MAX_SLICES = 200;
    private final ConcurrentLinkedDeque<Slice> slices = new ConcurrentLinkedDeque<>();

    private int completed, failed;
    private long sumWait, sumTurnaround, sumResponse;

    public void recordSlice(Slice s) {
        slices.addFirst(s);
        while (slices.size() > MAX_SLICES) slices.pollLast();
    }

    public synchronized void recordFinished(ProcessingRequest r) {
        if (r.getResult() != null && r.getResult().success()) completed++; else failed++;
        sumWait += r.waitingTime();
        sumTurnaround += r.turnaroundTime();
        sumResponse += r.responseTime();
    }

    public synchronized Metrics metrics() {
        int n = completed + failed;
        if (n == 0) return new Metrics(0, 0, 0, 0, 0);
        return new Metrics(completed, failed, (double) sumWait / n, (double) sumTurnaround / n, (double) sumResponse / n);
    }

    public List<Slice> recentSlices() { return new ArrayList<>(slices); }

    public synchronized void reset() {
        slices.clear();
        completed = failed = 0;
        sumWait = sumTurnaround = sumResponse = 0;
    }
}

package com.fundflow.concurrency;

import com.fundflow.concurrency.scheduling.Scheduler;
import com.fundflow.dao.ProcessingRequestDAO;
import com.fundflow.model.OperationResult;
import com.fundflow.model.ProcessingRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * ===================== CONSUMERS (worker threads) =====================
 * N threads that forever repeat:
 *     1. ask the scheduler for the next request            (dispatch)
 *     2. run up to <time quantum> steps of it               (CPU burst)
 *     3. finished?  -> save result & wake the waiting caller
 *        not yet?   -> put it back in the queue             (context switch)
 *
 * This class never decides ORDER - that is the Scheduler's job.
 * ======================================================================
 */
public class WorkerPool {

    private final Scheduler scheduler;
    private final RequestProcessor processor;
    private final ExecutionTracker tracker;
    private final ProcessingRequestDAO requestDao = new ProcessingRequestDAO();
    private final List<Thread> threads = new ArrayList<>();
    private volatile boolean running;

    public WorkerPool(Scheduler scheduler, RequestProcessor processor, ExecutionTracker tracker) {
        this.scheduler = scheduler; this.processor = processor; this.tracker = tracker;
    }

    public void start(int workerCount) {
        running = true;
        for (int i = 1; i <= workerCount; i++) {
            Thread t = new Thread(this::workerLoop, "Worker-" + i);
            t.setDaemon(true);
            threads.add(t);
            t.start();
        }
    }

    /** Graceful stop: workers finish their current turn, then exit. */
    public void stop() {
        running = false;
        for (Thread t : threads) {
            try { t.join(5000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        threads.clear();
    }

    private void workerLoop() {
        while (running) {
            ProcessingRequest r;
            try {
                r = scheduler.takeNext(200);              // wait max 200 ms so we can notice stop()
            } catch (InterruptedException e) { return; }
            if (r == null) continue;
            runOneTurn(r);
        }
    }

    private void runOneTurn(ProcessingRequest r) {
        long start = System.currentTimeMillis();
        int fromStep = r.getStepsDone();
        r.setStatus(ProcessingRequest.Status.RUNNING);
        try {
            int ran = 0;
            while (ran < scheduler.timeQuantum() && !r.isFinished()) {   // the TIME QUANTUM check
                processor.runStep(r);
                ran++;
            }
        } catch (Throwable t) {
            r.complete(OperationResult.fail("Worker error: " + t.getMessage()));
        }
        tracker.recordSlice(new ExecutionTracker.Slice(r.getRequestId(), r.getType().name(),
                Thread.currentThread().getName(), fromStep, r.getStepsDone(), start, System.currentTimeMillis()));

        if (r.isFinished()) {
            tracker.recordFinished(r);
            try { requestDao.markFinished(r); } catch (Exception e) { System.err.println("Could not save request: " + e.getMessage()); }
            r.getFuture().complete(r.getResult());        // wakes whoever waits for this request
        } else {
            r.setStatus(ProcessingRequest.Status.PENDING);
            scheduler.requeue(r);                         // preempted -> back of the queue
        }
    }
}

package com.fundflow.concurrency;

import com.fundflow.concurrency.scheduling.Scheduler;
import com.fundflow.concurrency.scheduling.SchedulingAlgorithm;
import com.fundflow.model.OperationResult;
import com.fundflow.model.ProcessingRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * FACADE that wires the whole OS layer together:
 *
 *   UI / tests --> RequestProducer --> Scheduler (queue) --> WorkerPool --> RequestProcessor --> MySQL
 *                                                                              |
 *                                                                      AccountLockManager
 *
 * The UI only talks to this class.
 */
public class TransactionEngine {

    private final int workerCount;
    private final int stepDelayMs;
    private final int maxInFlight;

    private final AccountLockManager lockManager = new AccountLockManager();
    private final ExecutionTracker tracker = new ExecutionTracker();

    private SchedulingAlgorithm algorithm;
    private int quantum;
    private volatile Scheduler scheduler;
    private volatile RequestProducer producer;
    private WorkerPool pool;

    public TransactionEngine(SchedulingAlgorithm algorithm, int workerCount, int stepDelayMs) {
        this(algorithm, 1, workerCount, stepDelayMs, 500);
    }

    public TransactionEngine(SchedulingAlgorithm algorithm, int quantum, int workerCount, int stepDelayMs, int maxInFlight) {
        this.algorithm = algorithm; this.quantum = quantum;
        this.workerCount = workerCount; this.stepDelayMs = stepDelayMs; this.maxInFlight = maxInFlight;
        build();
    }

    private void build() {
        scheduler = algorithm.create(quantum);
        producer = new RequestProducer(scheduler, maxInFlight);
        pool = new WorkerPool(scheduler, new RequestProcessor(lockManager, stepDelayMs), tracker);
    }

    public synchronized void start() { pool.start(workerCount); }

    /** Swap the scheduling algorithm at run time. Waiting requests are carried over to the new scheduler. */
    public synchronized void switchAlgorithm(SchedulingAlgorithm newAlgorithm, int newQuantum) {
        pool.stop();                                              // workers finish their current turn
        List<ProcessingRequest> leftovers = scheduler.drainAll(); // requests still waiting
        this.algorithm = newAlgorithm; this.quantum = newQuantum;
        tracker.reset();
        build();
        leftovers.forEach(scheduler::submit);                     // re-queue them under the new rule
        pool.start(workerCount);
    }

    public synchronized void shutdown() { pool.stop(); }

    // ---- API used by UI and tests (priority: 1 = highest ... 10 = lowest, default 5)
    public CompletableFuture<OperationResult> deposit(String acc, BigDecimal amt, int priority)  { return producer.deposit(acc, amt, priority); }
    public CompletableFuture<OperationResult> withdraw(String acc, BigDecimal amt, int priority) { return producer.withdraw(acc, amt, priority); }
    public CompletableFuture<OperationResult> transfer(String from, String to, BigDecimal amt, int priority) { return producer.transfer(from, to, amt, priority); }

    // ---- info for the admin monitor
    public Scheduler scheduler()            { return scheduler; }
    public SchedulingAlgorithm algorithm()  { return algorithm; }
    public ExecutionTracker tracker()       { return tracker; }
    public AccountLockManager locks()       { return lockManager; }
    public int workerCount()                { return workerCount; }
}

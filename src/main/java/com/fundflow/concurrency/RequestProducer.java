package com.fundflow.concurrency;

import com.fundflow.concurrency.scheduling.Scheduler;
import com.fundflow.dao.ProcessingRequestDAO;
import com.fundflow.model.OperationResult;
import com.fundflow.model.ProcessingRequest;
import com.fundflow.model.RequestType;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;

/**
 * ===================== PRODUCER (UI / client side) =====================
 * Classic bounded-buffer problem:
 *   - the BUFFER is the scheduler queue
 *   - a SEMAPHORE with N permits limits how many requests may be "in flight".
 *     When N requests are already waiting/running, producers BLOCK until a
 *     consumer finishes one (the permit is released on completion).
 *
 * submit() returns a CompletableFuture: the caller gets the result later
 * without blocking the UI thread.
 * ======================================================================
 */
public class RequestProducer {

    private final Scheduler scheduler;
    private final ProcessingRequestDAO requestDao = new ProcessingRequestDAO();
    private final Semaphore slots;

    public RequestProducer(Scheduler scheduler, int maxInFlight) {
        this.scheduler = scheduler;
        this.slots = new Semaphore(maxInFlight);
    }

    public CompletableFuture<OperationResult> deposit(String account, BigDecimal amount, int priority) {
        return submit(RequestType.DEPOSIT, null, account, amount, priority);
    }

    public CompletableFuture<OperationResult> withdraw(String account, BigDecimal amount, int priority) {
        return submit(RequestType.WITHDRAW, account, null, amount, priority);
    }

    public CompletableFuture<OperationResult> transfer(String from, String to, BigDecimal amount, int priority) {
        return submit(RequestType.TRANSFER, from, to, amount, priority);
    }

    private CompletableFuture<OperationResult> submit(RequestType type, String from, String to,
                                                      BigDecimal amount, int priority) {
        try {
            slots.acquire();                                   // P(wait): blocks if the buffer is full
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return CompletableFuture.completedFuture(OperationResult.fail("Interrupted"));
        }
        long id;
        try {
            id = requestDao.insert(type, from, to, amount, priority);   // persist as PENDING
        } catch (SQLException e) {
            slots.release();
            return CompletableFuture.completedFuture(OperationResult.fail("Could not queue request: " + e.getMessage()));
        }
        ProcessingRequest r = new ProcessingRequest(id, type, from, to, amount, priority);
        r.getFuture().whenComplete((res, ex) -> slots.release());       // V(signal): free the slot when done
        scheduler.submit(r);                                            // enter the ready queue
        return r.getFuture();
    }
}

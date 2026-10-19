package com.fundflow.concurrency;

import com.fundflow.dao.AccountDAO;
import com.fundflow.dao.TransactionDAO;
import com.fundflow.model.*;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Executes ONE step of a request. The scheduler decides how many steps a worker
 * may call in a row (the time quantum); this class only knows how to do a step.
 *
 * IMPORTANT DESIGN RULE: locks are taken and released INSIDE the EXECUTE step.
 * A request that gets preempted therefore never holds a lock while waiting in the
 * queue, so Round Robin cannot cause lock hold-and-wait problems.
 */
public class RequestProcessor {

    private final AccountLockManager lockManager;
    private final TransactionDAO transactionDao = new TransactionDAO();
    private final AccountDAO accountDao = new AccountDAO();
    private final int stepDelayMs;   // simulated work per step so scheduling is visible (0 = full speed)

    public RequestProcessor(AccountLockManager lockManager, int stepDelayMs) {
        this.lockManager = lockManager;
        this.stepDelayMs = stepDelayMs;
    }

    public void runStep(ProcessingRequest r) {
        long t0 = System.currentTimeMillis();
        if (r.getFirstStartTime() == 0) r.setFirstStartTime(t0);       // response time starts here
        try {
            switch (r.currentStep()) {
                case VALIDATE       -> { simulateWork(); validate(r); }
                case CHECK_ACCOUNTS -> { simulateWork(); checkAccounts(r); }
                case EXECUTE        -> execute(r);
                case LOG            -> simulateWork();
            }
            r.advance();
            if (!r.isFinished() && r.remainingSteps() == 0) {
                OperationResult res = r.getExecuteResult();
                r.complete(res != null ? res : OperationResult.ok("Done"));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            r.complete(OperationResult.fail("Interrupted"));
        } catch (Exception e) {
            r.complete(OperationResult.fail("Error: " + e.getMessage()));
        } finally {
            r.addServiceTime(System.currentTimeMillis() - t0);
        }
    }

    private void simulateWork() throws InterruptedException {
        if (stepDelayMs > 0) Thread.sleep(stepDelayMs);
    }

    // ---------------------------------------------------------------- STEP 1
    private void validate(ProcessingRequest r) {
        if (r.getAmount() == null || r.getAmount().signum() <= 0) {
            r.complete(OperationResult.fail("Amount must be positive")); return;
        }
        switch (r.getType()) {
            case DEPOSIT  -> { if (blank(r.getToAccount()))   r.complete(OperationResult.fail("Account number required")); }
            case WITHDRAW -> { if (blank(r.getFromAccount())) r.complete(OperationResult.fail("Account number required")); }
            case TRANSFER -> {
                if (blank(r.getFromAccount()) || blank(r.getToAccount())) r.complete(OperationResult.fail("Both account numbers required"));
                else if (r.getFromAccount().equals(r.getToAccount()))     r.complete(OperationResult.fail("Cannot transfer to the same account"));
            }
        }
    }

    // ---------------------------------------------------------------- STEP 2 (transfers only)
    private void checkAccounts(ProcessingRequest r) throws SQLException {
        for (String no : new String[]{r.getFromAccount(), r.getToAccount()}) {
            Account a = accountDao.findByAccountNo(no);
            if (a == null)                      { r.complete(OperationResult.fail("Account " + no + " not found")); return; }
            if (!"ACTIVE".equals(a.status()))   { r.complete(OperationResult.fail("Account " + no + " is " + a.status())); return; }
        }
    }

    // ---------------------------------------------------------------- STEP 3 : the real work
    private void execute(ProcessingRequest r) throws SQLException {
        // Java-level mutual exclusion (OS concept) ...
        List<ReentrantLock> held = lockManager.lockAll(r.getFromAccount(), r.getToAccount());
        try {
            // ... and a database ACID transaction (DBMS concept) inside the critical section
            OperationResult res = switch (r.getType()) {
                case DEPOSIT  -> transactionDao.deposit(r.getToAccount(), r.getAmount());
                case WITHDRAW -> transactionDao.withdraw(r.getFromAccount(), r.getAmount());
                case TRANSFER -> transactionDao.transfer(r.getFromAccount(), r.getToAccount(), r.getAmount());
            };
            r.setExecuteResult(res);
            if (!res.success()) r.complete(res);          // failed -> skip the remaining steps
        } finally {
            lockManager.unlockAll(held);                  // ALWAYS release, even on exception
        }
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }
}

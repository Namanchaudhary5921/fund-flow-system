package com.fundflow.model;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

/**
 * A request waiting in the scheduler queue  ==  a "process" in OS terms (its PCB).
 *
 *   arrivalTime    : when it entered the queue
 *   firstStartTime : when a worker first ran it  -> response time
 *   finishTime     : when it completed          -> turnaround time
 *   serviceTime    : total time spent actually running (CPU burst)
 *   waitingTime    = turnaround - serviceTime
 */
public class ProcessingRequest {

    public enum Status { PENDING, RUNNING, COMPLETED, FAILED }

    private final long requestId;
    private final RequestType type;
    private final String fromAccount;   // used by WITHDRAW and TRANSFER
    private final String toAccount;     // used by DEPOSIT and TRANSFER
    private final BigDecimal amount;
    private final int priority;         // 1 = highest

    private final long arrivalTime = System.currentTimeMillis();
    private volatile long firstStartTime = 0;
    private volatile long finishTime = 0;
    private volatile long serviceTime = 0;
    private volatile int stepsDone = 0;
    private volatile Status status = Status.PENDING;
    private volatile OperationResult executeResult;
    private volatile OperationResult result;
    private final CompletableFuture<OperationResult> future = new CompletableFuture<>();

    public ProcessingRequest(long requestId, RequestType type, String fromAccount,
                             String toAccount, BigDecimal amount, int priority) {
        this.requestId = requestId; this.type = type; this.fromAccount = fromAccount;
        this.toAccount = toAccount; this.amount = amount; this.priority = priority;
    }

    // ---- step bookkeeping
    public Step currentStep()   { return type.steps().get(stepsDone); }
    public int remainingSteps() { return type.burstTime() - stepsDone; }
    public void advance()       { stepsDone++; }

    public void complete(OperationResult r) {
        this.result = r;
        this.finishTime = System.currentTimeMillis();
        this.status = r.success() ? Status.COMPLETED : Status.FAILED;
    }
    public boolean isFinished() { return result != null; }

    // ---- metrics
    public long turnaroundTime() { return finishTime - arrivalTime; }
    public long waitingTime()    { return turnaroundTime() - serviceTime; }
    public long responseTime()   { return firstStartTime == 0 ? 0 : firstStartTime - arrivalTime; }

    // ---- getters / setters
    public long getRequestId() { return requestId; }
    public RequestType getType() { return type; }
    public String getFromAccount() { return fromAccount; }
    public String getToAccount() { return toAccount; }
    public BigDecimal getAmount() { return amount; }
    public int getPriority() { return priority; }
    public long getArrivalTime() { return arrivalTime; }
    public long getFirstStartTime() { return firstStartTime; }
    public void setFirstStartTime(long t) { this.firstStartTime = t; }
    public void addServiceTime(long ms) { this.serviceTime += ms; }
    public int getStepsDone() { return stepsDone; }
    public Status getStatus() { return status; }
    public void setStatus(Status s) { this.status = s; }
    public OperationResult getExecuteResult() { return executeResult; }
    public void setExecuteResult(OperationResult r) { this.executeResult = r; }
    public OperationResult getResult() { return result; }
    public CompletableFuture<OperationResult> getFuture() { return future; }

    @Override public String toString() {
        return "#" + requestId + " " + type + " (" + stepsDone + "/" + type.burstTime() + ")";
    }
}

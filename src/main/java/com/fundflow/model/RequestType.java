package com.fundflow.model;

import java.util.List;

/** Each request type has a different number of steps = a different "burst time". */
public enum RequestType {
    DEPOSIT (List.of(Step.VALIDATE, Step.EXECUTE, Step.LOG)),                      // burst = 3
    WITHDRAW(List.of(Step.VALIDATE, Step.EXECUTE, Step.LOG)),                      // burst = 3
    TRANSFER(List.of(Step.VALIDATE, Step.CHECK_ACCOUNTS, Step.EXECUTE, Step.LOG)); // burst = 4

    private final List<Step> steps;
    RequestType(List<Step> steps) { this.steps = steps; }

    public List<Step> steps() { return steps; }
    public int burstTime()     { return steps.size(); }
}

package com.fundflow.concurrency.scheduling;

/** Menu of available algorithms (used by the admin screen and the concurrency test). */
public enum SchedulingAlgorithm {
    FCFS("FCFS - First Come First Served"),
    ROUND_ROBIN("Round Robin"),
    SJF("SJF - Shortest Job First"),
    PRIORITY("Priority Scheduling");

    private final String label;
    SchedulingAlgorithm(String label) { this.label = label; }

    public Scheduler create(int quantum) {
        return switch (this) {
            case FCFS        -> new FCFSScheduler();
            case ROUND_ROBIN -> new RoundRobinScheduler(quantum);
            case SJF         -> new SJFScheduler();
            case PRIORITY    -> new PriorityScheduler();
        };
    }

    @Override public String toString() { return label; }
}

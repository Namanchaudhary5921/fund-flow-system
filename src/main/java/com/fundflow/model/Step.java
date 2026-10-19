package com.fundflow.model;

/**
 * One "CPU burst unit" of a request. The scheduler decides how many of these
 * steps a worker thread may run before the request must wait again.
 */
public enum Step {
    VALIDATE,        // check amount / account fields (in memory)
    CHECK_ACCOUNTS,  // read-only DB check that accounts exist and are ACTIVE
    EXECUTE,         // take Java locks + call the stored procedure (the real DB transaction)
    LOG              // finishing work (notification / bookkeeping)
}

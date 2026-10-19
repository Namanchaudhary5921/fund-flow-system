# Fund Flow Management System  (OS + DBMS combined project)

Java 17 + JavaFX + MySQL 8 + JDBC (HikariCP) + Maven.

## 1. Setup (one time)
```bash
mysql -u root -p < sql/01_schema.sql
mysql -u root -p < sql/02_procedures_triggers.sql
mysql -u root -p < sql/03_sample_data.sql
# put your MySQL password in src/main/resources/db.properties
mvn compile exec:java -Dexec.mainClass=com.fundflow.SeedData      # creates login users
```

## 2. Run
| What | Command |
|---|---|
| Desktop app | `mvn javafx:run` |
| Concurrency demo (all 4 algorithms, 100 simultaneous transfers) | `mvn compile exec:java -Dexec.mainClass=com.fundflow.concurrency.ConcurrencyTest` |

Logins: `admin/admin123`, `alice/password123`, `bob/password123`, `carol/password123`

## 3. Where is each concept? (use this in your viva)

### OS concepts  -> `concurrency/`
| Concept | File |
|---|---|
| Scheduler contract (all algorithms implement it) | `scheduling/Scheduler.java` |
| **FCFS** | `scheduling/FCFSScheduler.java` |
| **Round Robin** (time quantum, preemption) | `scheduling/RoundRobinScheduler.java` |
| **SJF** | `scheduling/SJFScheduler.java` |
| **Priority** | `scheduling/PriorityScheduler.java` |
| Algorithm menu / factory | `scheduling/SchedulingAlgorithm.java` |
| Producer (bounded buffer, semaphore) | `RequestProducer.java` |
| Consumers (worker threads, dispatch loop, context switch) | `WorkerPool.java` |
| Mutex per account + deadlock prevention (ordered locking) | `AccountLockManager.java` |
| What one "step" does | `RequestProcessor.java` |
| Waiting / turnaround / response time + Gantt log | `ExecutionTracker.java` |
| Wiring everything together | `TransactionEngine.java` |
| Proof that no money is lost under concurrency | `ConcurrencyTest.java` |
| Process / PCB analogue | `model/ProcessingRequest.java` |

### DBMS concepts  -> `sql/`
- ACID transaction, `SELECT ... FOR UPDATE`, rollback: `sp_deposit`, `sp_withdraw`, `sp_transfer_funds`
- Trigger (auto audit log): `trg_account_balance_audit`
- Constraints: `CHECK (balance >= 0)`, `CHECK (amount > 0)`, PK/FK, UNIQUE
- Indexes: `idx_trans_date`, `idx_trans_from`, `idx_trans_to`, `idx_audit_account`
- Generalisation: `fund_transfer` is a specialisation of `transactions`

### Layers (matches your architecture diagram)
`ui` (presentation) -> `service` (business) -> `concurrency` (OS processing) -> `dao` (JDBC) -> MySQL

## 4. Request life-cycle
```
UI click -> RequestProducer (semaphore, saved as PENDING) -> Scheduler queue
   -> Worker thread takes next request (order decided by FCFS / RR / SJF / Priority)
   -> runs up to <quantum> steps: VALIDATE -> CHECK_ACCOUNTS -> EXECUTE -> LOG
   -> EXECUTE = lock accounts in sorted order + call stored procedure (COMMIT / ROLLBACK)
   -> finished? mark COMPLETED/FAILED, wake the UI.  Not finished? back to queue (Round Robin)
```

## 5. Still to build (the remaining ~40%)
Loan workflow, card issue/block, Service supertype (ISA), statements/reports, customer/account creation screens, performance monitoring.

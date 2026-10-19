package com.fundflow.concurrency;

import com.fundflow.concurrency.scheduling.SchedulingAlgorithm;
import com.fundflow.dao.AccountDAO;
import com.fundflow.model.OperationResult;
import com.fundflow.util.DBConnection;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;

/**
 * ===================== CONCURRENCY TEST (the demo!) =====================
 * Fires N transfers (default 100) from 20 client threads at the SAME moment,
 * once for every scheduling algorithm, then checks the invariant:
 *
 *        total money BEFORE  ==  total money AFTER
 *
 * If locks or transactions were wrong, money would appear or vanish.
 * It also prints average waiting / turnaround / response time per algorithm.
 *
 * Run:  mvn compile exec:java -Dexec.mainClass=com.fundflow.concurrency.ConcurrencyTest
 *       (optional argument: number of transfers, e.g. -Dexec.args="200")
 * =======================================================================
 */
public class ConcurrencyTest {

    public static void main(String[] args) throws Exception {
        int n = args.length > 0 ? Integer.parseInt(args[0]) : 100;
        AccountDAO accountDao = new AccountDAO();
        List<String> accounts = accountDao.findActiveAccountNos();
        if (accounts.size() < 2) { System.out.println("Need at least 2 accounts. Run sql/03_sample_data.sql first."); return; }

        BigDecimal startTotal = accountDao.totalBalance();
        System.out.println("Accounts: " + accounts);
        System.out.println("Total money in bank BEFORE test: " + startTotal + "\n");
        System.out.printf("%-26s %6s %6s %9s %11s %9s %8s %10s  %s%n",
                "Algorithm", "OK", "Failed", "AvgWait", "AvgTurnArnd", "AvgResp", "Total", "LockWaits", "Money check");

        for (SchedulingAlgorithm algo : SchedulingAlgorithm.values()) {
            BigDecimal before = accountDao.totalBalance();
            TransactionEngine engine = new TransactionEngine(algo, 1, 4, 5, 500);   // quantum 1, 4 workers, 5 ms/step
            engine.start();

            // Generate random requests on ONE thread (Random is not thread-safe) ...
            Random rnd = new Random(42);
            record Job(String from, String to, BigDecimal amt, int prio) {}
            List<Job> jobs = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                String from = accounts.get(rnd.nextInt(accounts.size()));
                String to;
                do { to = accounts.get(rnd.nextInt(accounts.size())); } while (to.equals(from));
                jobs.add(new Job(from, to, BigDecimal.valueOf(1 + rnd.nextInt(500)), 1 + rnd.nextInt(10)));
            }

            // ... and submit them from 20 threads released together by a latch.
            ExecutorService clients = Executors.newFixedThreadPool(20);
            CountDownLatch startGun = new CountDownLatch(1);
            List<Future<CompletableFuture<OperationResult>>> submitted = new ArrayList<>();
            for (Job j : jobs) {
                submitted.add(clients.submit(() -> {
                    startGun.await();
                    return engine.transfer(j.from(), j.to(), j.amt(), j.prio());
                }));
            }
            long t0 = System.currentTimeMillis();
            startGun.countDown();

            for (Future<CompletableFuture<OperationResult>> f : submitted) f.get().join();   // wait for all results
            long elapsed = System.currentTimeMillis() - t0;

            ExecutionTracker.Metrics m = engine.tracker().metrics();
            BigDecimal after = accountDao.totalBalance();
            boolean ok = before.compareTo(after) == 0;
            System.out.printf("%-26s %6d %6d %7.1fms %9.1fms %7.1fms %6dms %10d  %s%n",
                    engine.scheduler().name(), m.completed(), m.failed(), m.avgWaiting(), m.avgTurnaround(),
                    m.avgResponse(), elapsed, engine.locks().getContentions(), ok ? "PASS" : "FAIL (" + before + " -> " + after + ")");

            clients.shutdown();
            engine.shutdown();
        }
        System.out.println("\nTotal money AFTER all tests:    " + accountDao.totalBalance()
                + (accountDao.totalBalance().compareTo(startTotal) == 0 ? "  -> no money created or lost" : "  -> MISMATCH!"));
        DBConnection.close();
    }
}

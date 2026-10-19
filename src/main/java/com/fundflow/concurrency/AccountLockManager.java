package com.fundflow.concurrency;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * ===================== SYNCHRONIZATION (mutex per account) =====================
 * Each account has its own mutex (ReentrantLock). A transfer needs TWO locks.
 *
 * DEADLOCK EXAMPLE (without ordering):
 *     Thread 1: transfer A -> B  locks A, waits for B
 *     Thread 2: transfer B -> A  locks B, waits for A      => both wait forever
 *
 * DEADLOCK PREVENTION: break the "circular wait" condition.
 * We ALWAYS lock accounts in sorted (alphabetical) order, so both threads
 * above lock A first, then B - a cycle can never form.
 *
 * (MySQL's SELECT ... FOR UPDATE is a second, database-level line of defence.)
 * ===============================================================================
 */
public class AccountLockManager {

    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final AtomicLong acquisitions = new AtomicLong();
    private final AtomicLong contentions = new AtomicLong();   // times a thread had to WAIT for a lock

    /** Locks all given accounts in sorted order (nulls ignored, duplicates collapsed). */
    public List<ReentrantLock> lockAll(String... accountNos) {
        TreeSet<String> sorted = new TreeSet<>();               // TreeSet = automatic sorting
        for (String a : accountNos) if (a != null) sorted.add(a);

        List<ReentrantLock> held = new ArrayList<>();
        for (String acc : sorted) {
            ReentrantLock lock = locks.computeIfAbsent(acc, k -> new ReentrantLock());
            if (lock.isLocked() && !lock.isHeldByCurrentThread()) contentions.incrementAndGet();
            lock.lock();                                        // blocks if another thread holds it
            acquisitions.incrementAndGet();
            held.add(lock);
        }
        return held;
    }

    /** Release in reverse order. Always call this inside a finally block. */
    public void unlockAll(List<ReentrantLock> held) {
        for (int i = held.size() - 1; i >= 0; i--) held.get(i).unlock();
    }

    public long getAcquisitions() { return acquisitions.get(); }
    public long getContentions()  { return contentions.get(); }
}

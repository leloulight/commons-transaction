/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.transaction.locking;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.commons.transaction.locking.LockException.Code;

public class RWLockManager<K, M> implements LockManager<K, M> {

    protected ConcurrentHashMap<KeyEntry<K, M>, ReadWriteLock> locks = new ConcurrentHashMap<KeyEntry<K, M>, ReadWriteLock>();

    protected Map<Thread, CopyOnWriteArraySet<Lock>> locksForThreads = new ConcurrentHashMap<Thread, CopyOnWriteArraySet<Lock>>();

    protected ConcurrentHashMap<Lock, Set<Thread>> threadsForLocks = new ConcurrentHashMap<Lock, Set<Thread>>();

    protected Map<Thread, Long> effectiveGlobalTimeouts = new ConcurrentHashMap<Thread, Long>();

    @Override
    public void endWork() {
        release();
    }

    @Override
    public void startWork(long timeout, TimeUnit unit) {
        if (isWorking()) {
            throw new IllegalStateException("work has already been started");
        }
        locksForThreads.put(Thread.currentThread(), new CopyOnWriteArraySet<Lock>());

        long timeoutMSecs = unit.toMillis(timeout);
        long now = System.currentTimeMillis();
        long effectiveTimeout = now + timeoutMSecs;
        effectiveGlobalTimeouts.put(Thread.currentThread(), effectiveTimeout);
    }

    // TODO
    protected boolean checkForDeadlock() {
        return false;

    }

    protected long computeRemainingTime(Thread thread) {
        long timeout = effectiveGlobalTimeouts.get(thread);
        long now = System.currentTimeMillis();
        long remaining = timeout - now;
        return remaining;
    }

    protected final ReadWriteLock putIfAbsent(KeyEntry<K, M> entry, ReadWriteLock lock) {
        ReadWriteLock existingLock = locks.get(entry);
        if (existingLock == null) {
            ReadWriteLock concurrentlyInsertedLock = locks.putIfAbsent(entry, lock);
            if (concurrentlyInsertedLock != null)
                lock = concurrentlyInsertedLock;
        }
        return lock;

    }

    protected ReadWriteLock create() {
        return new ReentrantReadWriteLock();
    }

    protected static class KeyEntry<K, M> {

        private K k;

        private M m;

        public KeyEntry(K k, M m) {
            this.k = k;
            this.m = m;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj instanceof KeyEntry) {
                KeyEntry otherEntry = (KeyEntry) obj;
                return (otherEntry.k.equals(k) && otherEntry.m.equals(m));
            }
            return false;
        }

        public int hashCode() {
            return k.hashCode() + m.hashCode();
        }
    }

    public boolean isWorking() {
        return locksForThreads.get(Thread.currentThread()) != null;
    }

    @Override
    public void lock(M managedResource, K key, boolean exclusive) throws LockException {
        long remainingTime = computeRemainingTime(Thread.currentThread());

        boolean locked = tryLockInternal(managedResource, key, exclusive, remainingTime,
                TimeUnit.MILLISECONDS);
        if (!locked) {
            throw new LockException(Code.TIMED_OUT, key);
        }
    }

    @Override
    public boolean tryLock(M managedResource, K key, boolean exclusive) {
        return tryLockInternal(managedResource, key, exclusive, 0, TimeUnit.MILLISECONDS);
    }

    protected boolean tryLockInternal(M managedResource, K key, boolean exclusive, long time,
            TimeUnit unit) throws LockException {
        reportTimeout(Thread.currentThread());

        KeyEntry<K, M> entry = new KeyEntry<K, M>(key, managedResource);
        ReadWriteLock rwlock = putIfAbsent(entry, create());
        Set<Lock> locks = locksForThreads.get(Thread.currentThread());
        if (locks == null) {
            throw new IllegalStateException("lock() can only be called after startWork()");
        }

        Lock lock = exclusive ? rwlock.writeLock() : rwlock.readLock();

        boolean locked;
        if (time == 0) {
            locked = lock.tryLock();
        } else {
            locked = doTrickyYetEfficientLockOnlyIfThisCanNotCauseADeadlock(lock, unit
                    .toMillis(time));
        }
        if (locked) {
            locks.add(lock);
            Set<Thread> threads = threadsForLocks.get(lock);
            if (threads == null) {
                threads = new HashSet<Thread>();
                Set<Thread> concurrentlyInsertedThreads = threadsForLocks
                        .putIfAbsent(lock, threads);
                if (concurrentlyInsertedThreads != null)
                    threads = concurrentlyInsertedThreads;
            }
            threads.add(Thread.currentThread());
        }
        return locked;
    }

    protected boolean doTrickyYetEfficientLockOnlyIfThisCanNotCauseADeadlock(Lock lock,
            long timeMsecs) throws LockException {

        // This algorithm is devided into three parts:
        // Note: We can be interrupted most of the time
        //
        // I prewait:
        // Wait a fraktion of the time to see if we can acquire
        // the lock in short time. If we can all is good and we exit
        // signalling success. If not we need to get into a more resource
        // consuming phase.
        //
        // II clearing of timed out thtreads / deadlock detection:
        // As we have not been able to acquire the lock, yet, maybe there is
        // deadlock. Clear all threads already timed out and afterwards
        // check for a deadlock state. If there is one report it with an
        // exception. If not we enter the final phase.
        // 
        // III real wait:
        // Everything is under control, we were just a little bit too
        // impatient. So wait for the remaining time and see if the can get
        // the lock
        // 

        try {
            boolean locked;

            // I prewait

            long startTime = System.currentTimeMillis();

            // TODO this heuristic devisor really should be configurable
            long preWaitTime = timeMsecs / 5;
            locked = lock.tryLock(preWaitTime, TimeUnit.MILLISECONDS);
            if (locked)
                return true;

            // II deadlock detect
            cancelAllTimedOut();
            if (wouldDeadlock(Thread.currentThread(), new HashSet<Thread>())) {
                throw new LockException(LockException.Code.WOULD_DEADLOCK);
            }

            // III real wait
            long now = System.currentTimeMillis();
            long remainingWaitTime = timeMsecs - (now - startTime);
            if (remainingWaitTime < 0)
                return false;

            locked = lock.tryLock(remainingWaitTime, TimeUnit.MILLISECONDS);
            return locked;
        } catch (InterruptedException e) {
            throw new LockException(Code.INTERRUPTED);
        }

    }

    protected boolean wouldDeadlock(Thread thread, Set<Thread> path) {
        path.add(thread);
        // these are our locks
        // Note: No need to make a copy as we can be sure to iterate on our
        // private
        // version, as this is a CopyOnWriteArraySet!
        CopyOnWriteArraySet<Lock> locks = locksForThreads.get(thread);
        for (Lock lock : locks) {
            // these are the ones waiting for one of our locks
            // and if they wait, they wait because of me!
            Collection<Thread> conflicts = getConflictingWaiters((ReentrantReadWriteLock) lock);
            for (Thread conflictThread : conflicts) {
                // this means, we have found a cycle in the wait graph
                if (path.contains(conflictThread)) {
                    return true;
                } else if (wouldDeadlock(conflictThread, path)) {
                    return true;
                }
            }
        }

        path.remove(thread);
        return false;
    }

    protected Collection<Thread> getConflictingWaiters(ReentrantReadWriteLock lock) {
        Collection<Thread> result = new ArrayList<Thread>();
        // Consider every thread that holds at least one lock!
        // Caution: We can not use "threadsForLocks" as the waiting threads
        // have not yet acquired the lock and thus are not part of the map.
        // An alternative algorithm could also remember the threads waiting for
        // a lock
        Collection<Thread> threadsWithLocks = locksForThreads.keySet();
        for (Thread thread : threadsWithLocks) {
            if (lock.hasQueuedThread(thread)) {
                result.add(thread);
            }
        }
        return result;
    }

    protected void reportTimeout(Thread thread) throws LockException {
        if (hasTimedOut(thread)) {
            throw new LockException(LockException.Code.TIMED_OUT);
        }
    }

    protected void cancelAllTimedOut() {
        Set<Thread> threads = effectiveGlobalTimeouts.keySet();
        for (Thread thread : threads) {
            if (hasTimedOut(thread)) {
                // TODO #1: We need to record this thread has timed out to produce
                // a meaningful exception when it tries to continue its work
                // TODO #2: If would be even better if we could actively release
                // its locks, but only the thread that acquired a lock can
                // release it. An extended implementation of ReentrantLock would
                // help.
                thread.interrupt();
            }

        }
    }

    protected boolean hasTimedOut(Thread thread) {
        long remainingTime = computeRemainingTime(thread);
        return (remainingTime < 0);

    }

    protected void release() {
        Set<Lock> locks = locksForThreads.get(Thread.currentThread());
        // graceful reaction...
        if (locks == null) {
            return;
        }
        for (Lock lock : locks) {
            lock.unlock();

            // FIXME: We need to do this atomically
            Set<Thread> threadsForThisLock = threadsForLocks.get(lock);
            if (threadsForThisLock != null) {
                threadsForThisLock.remove(Thread.currentThread());
                if (threadsForThisLock.isEmpty()) {
                    threadsForLocks.remove(lock);
                    locks.remove(lock);
                }
            }
        }

        locksForThreads.remove(Thread.currentThread());
    }

}

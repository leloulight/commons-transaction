package org.apache.commons.transaction.locking;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.commons.transaction.locking.LockException.Code;

public class RWLockManager<K, M> implements LockManager<K, M> {

    protected ConcurrentHashMap<KeyEntry<K, M>, ReadWriteLock> locks = new ConcurrentHashMap<KeyEntry<K, M>, ReadWriteLock>();

    protected Map<Thread, Set<ReadWriteLock>> locksForThreads = new ConcurrentHashMap<Thread, Set<ReadWriteLock>>();

    protected Map<ReadWriteLock, Set<Thread>> threadsForLocks = new ConcurrentHashMap<ReadWriteLock, Set<Thread>>();

    protected Map<Thread, Long> effectiveGlobalTimeouts = new ConcurrentHashMap<Thread, Long>();

    // TODO
    public static Iterable<ReadWriteLock> orderLocks() {
        return null;

    }

    // TODO
    public void lockAll(Iterable<ReadWriteLock> locks) {
    }

    @Override
    public void endWork() {
        Set<ReadWriteLock> locks = locksForThreads.get(Thread.currentThread());
        // graceful reaction...
        if (locks == null) {
            return;
        }
        for (ReadWriteLock lock : locks) {
            try {
                lock.readLock().unlock();
            } catch (IllegalMonitorStateException imse) {
                // we do not care
            }
            try {
                lock.writeLock().unlock();
            } catch (IllegalMonitorStateException imse) {
                // we do not care
            }

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

    @Override
    public void startWork(long timeout, TimeUnit unit) {
        if (isWorking()) {
            throw new IllegalStateException("work has already been started");
        }
        locksForThreads.put(Thread.currentThread(), new HashSet<ReadWriteLock>());

        long timeoutMSecs = unit.toMillis(timeout);
        long now = System.currentTimeMillis();
        long effectiveTimeout = now + timeoutMSecs;
        effectiveGlobalTimeouts.put(Thread.currentThread(), effectiveTimeout);
    }

    // TODO
    protected boolean checkForDeadlock() {
        return false;

    }

    protected long computeRemainingTime() {
        long timeout = effectiveGlobalTimeouts.get(Thread.currentThread());
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

    @Override
    public void lock(M managedResource, K key, boolean exclusive) throws LockException {
        long remainingTime = computeRemainingTime();
        if (remainingTime < 0) {
            throw new LockException(LockException.Code.TIMED_OUT);
        }

        KeyEntry<K, M> entry = new KeyEntry<K, M>(key, managedResource);

        ReadWriteLock rwlock = putIfAbsent(entry, create());
        Set<ReadWriteLock> locks = locksForThreads.get(Thread.currentThread());
        if (locks == null) {
            throw new IllegalStateException("lock() can only be called after startWork()");
        }
        locks.add(rwlock);

        Lock lock = exclusive ? rwlock.writeLock() : rwlock.readLock();

        try {
            boolean locked = lock.tryLock(remainingTime, TimeUnit.MILLISECONDS);
            if (!locked) {
                throw new LockException(Code.TIMED_OUT, key);
            }
        } catch (InterruptedException e) {
            throw new LockException(Code.INTERRUPTED, key);
        }

    }

    @Override
    public boolean isWorking() {
        return locksForThreads.get(Thread.currentThread()) != null;
    }

}

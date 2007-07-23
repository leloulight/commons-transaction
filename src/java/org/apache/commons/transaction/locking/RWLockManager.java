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

    protected Map<Thread, Set<Lock>> locksForThreads = new ConcurrentHashMap<Thread, Set<Lock>>();

    protected Map<ReadWriteLock, Set<Thread>> threadsForLocks = new ConcurrentHashMap<ReadWriteLock, Set<Thread>>();

    protected Map<Thread, Long> effectiveGlobalTimeouts = new ConcurrentHashMap<Thread, Long>();

    // TODO
    public Iterable<ReadWriteLock> orderLocks() {
        Set<Lock> locks = locksForThreads.get(Thread.currentThread());
        if (locks == null) {
            throw new IllegalStateException("lock() can only be called after startWork()");
        }

        return null;

    }

    @Override
    public void endWork() {
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

    @Override
    public void startWork(long timeout, TimeUnit unit) {
        if (isWorking()) {
            throw new IllegalStateException("work has already been started");
        }
        locksForThreads.put(Thread.currentThread(), new HashSet<Lock>());

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
    public boolean isWorking() {
        return locksForThreads.get(Thread.currentThread()) != null;
    }

    @Override
    public void lock(M managedResource, K key, boolean exclusive) throws LockException {
        long remainingTime = computeRemainingTime();
        if (remainingTime < 0) {
            throw new LockException(LockException.Code.TIMED_OUT);
        }
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
        KeyEntry<K, M> entry = new KeyEntry<K, M>(key, managedResource);
        ReadWriteLock rwlock = putIfAbsent(entry, create());
        Set<Lock> locks = locksForThreads.get(Thread.currentThread());
        if (locks == null) {
            throw new IllegalStateException("lock() can only be called after startWork()");
        }

        Lock lock = exclusive ? rwlock.writeLock() : rwlock.readLock();

        try {
            boolean locked;
            if (time == 0) {
                locked = lock.tryLock();
            } else {
                locked = lock.tryLock(time, unit);
            }
            if (locked) {
                locks.add(lock);
            }
            return locked;
        } catch (InterruptedException e) {
            throw new LockException(Code.INTERRUPTED, key);
        }
    }

}

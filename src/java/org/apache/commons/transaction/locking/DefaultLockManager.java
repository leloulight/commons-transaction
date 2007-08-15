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

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.transaction.locking.LockException.Code;

/**
 * 
 * @author olli
 * 
 * @param <K>
 * @param <M>
 */
public class DefaultLockManager<K, M> implements LockManager<K, M> {
    private Log logger = LogFactory.getLog(getClass());

    protected ConcurrentHashMap<KeyEntry<K, M>, ReentrantLock> locks = new ConcurrentHashMap<KeyEntry<K, M>, ReentrantLock>();

    protected Map<Thread, CopyOnWriteArraySet<ReentrantLock>> locksForThreads = new ConcurrentHashMap<Thread, CopyOnWriteArraySet<ReentrantLock>>();

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
        locksForThreads.put(Thread.currentThread(), new CopyOnWriteArraySet<ReentrantLock>());

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

    protected ReentrantLock create() {
        return new ReentrantLock();
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

        ReentrantLock lock = create();
        ReentrantLock existingLock = locks.putIfAbsent(entry, lock);
        if (existingLock != null)
            lock = existingLock;
        Set<ReentrantLock> locks = locksForThreads.get(Thread.currentThread());
        if (locks == null) {
            throw new IllegalStateException("lock() can only be called after startWork()");
        }

        boolean locked;
        if (time == 0) {
            locked = lock.tryLock();
        } else {
            try {
                locked = lock.tryLock(time, unit);
            } catch (InterruptedException e) {
                throw new LockException(Code.INTERRUPTED);
            }

        }
        if (locked) {
            locks.add(lock);
        }
        return locked;
    }

    protected void reportTimeout(Thread thread) throws LockException {
        if (hasTimedOut(thread)) {
            throw new LockException(LockException.Code.TIMED_OUT);
        }
    }

    protected boolean hasTimedOut(Thread thread) {
        long remainingTime = computeRemainingTime(thread);
        return (remainingTime < 0);

    }

    protected void release() {
        Set<ReentrantLock> locks = locksForThreads.get(Thread.currentThread());
        // graceful reaction...
        if (locks == null) {
            return;
        }
        for (ReentrantLock lock : locks) {
            int holdCount = lock.getHoldCount();
            logger.debug("Locks held by this thread: " + holdCount);
            while (true) {
                try {
                    lock.unlock();
                } catch (IllegalMonitorStateException imse) {
                    // We are lacking information on whether we have a read
                    // lock and if so how many.
                    // XXX Just free as many as possible.
                    break;
                }
            }
        }

        locksForThreads.remove(Thread.currentThread());
    }

}

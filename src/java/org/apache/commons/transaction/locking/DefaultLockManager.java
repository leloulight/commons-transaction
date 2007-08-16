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

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.transaction.locking.LockException.Code;

/**
 * Default implementation of {@link LockManager}.
 * 
 * <p>
 * This is a minimal implementation that only knows a single type of lock.
 * Read-/Write-locks are not supported. Deadlock detection is not performed.
 * Transferring locks between threads is not possible. These limitations are due
 * to the standard {@link Lock} and {@link ReadWriteLock} implementations.
 * 
 * <p>
 * This implementation is <em>thread-safe</em>.
 */
public class DefaultLockManager<K, M> extends AbstractLockManager<K, M> implements
        LockManager<K, M> {
    private Log logger = LogFactory.getLog(getClass());

    protected ConcurrentHashMap<KeyEntry<K, M>, ReentrantLock> locks = new ConcurrentHashMap<KeyEntry<K, M>, ReentrantLock>();

    protected ReentrantLock create() {
        return new ReentrantLock();
    }

    protected boolean tryLockInternal(M managedResource, K key, boolean exclusive, long time,
            TimeUnit unit) throws LockException {
        reportTimeout(Thread.currentThread());

        KeyEntry<K, M> entry = new KeyEntry<K, M>(key, managedResource);

        ReentrantLock lock = create();
        ReentrantLock existingLock = locks.putIfAbsent(entry, lock);
        if (existingLock != null)
            lock = existingLock;
        Set<Lock> locks = locksForThreads.get(Thread.currentThread());
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
        Set<Lock> locks = locksForThreads.get(Thread.currentThread());
        // graceful reaction...
        if (locks == null) {
            return;
        }
        for (Lock lock : locks) {
            int holdCount = ((ReentrantLock) lock).getHoldCount();
            logger.debug("Locks held by this thread: " + holdCount);
            for (int i = 0; i < holdCount; i++) {
                lock.unlock();
            }
        }

        locksForThreads.remove(Thread.currentThread());
    }

}

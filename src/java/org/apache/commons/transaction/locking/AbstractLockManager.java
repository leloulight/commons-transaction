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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.transaction.locking.LockException.Code;

/**
 * Abstract implementation of {@link LockManager}. You can use this
 * implementation as a base for your custom implementations.
 * 
 * <p>
 * This implementation is <em>thread-safe</em>.
 */
public abstract class AbstractLockManager<K, M> implements LockManager<K, M> {
    private Log logger = LogFactory.getLog(getClass());

    protected Map<Thread, Set<Lock>> locksForThreads = new ConcurrentHashMap<Thread, Set<Lock>>();

    protected Map<Thread, Long> effectiveGlobalTimeouts = new ConcurrentHashMap<Thread, Long>();

    @Override
    public void endWork() {
        checkIsStarted();
        release();
    }

    abstract protected void release();

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

    protected void checkIsStarted() {
        if (locksForThreads.get(Thread.currentThread()) == null) {
            throw new IllegalStateException("You need to start work before you can acquire a lock");
        }
    }

    protected long computeRemainingTime(Thread thread) {
        long timeout = effectiveGlobalTimeouts.get(thread);
        long now = System.currentTimeMillis();
        long remaining = timeout - now;
        return remaining;
    }

    public boolean isWorking() {
        return locksForThreads.get(Thread.currentThread()) != null;
    }

    @Override
    public void lock(M resourceManager, K key, boolean exclusive) throws LockException {
        checkIsStarted();
        long remainingTime = computeRemainingTime(Thread.currentThread());

        boolean locked = tryLockInternal(resourceManager, key, exclusive, remainingTime,
                TimeUnit.MILLISECONDS);
        if (!locked) {
            throw new LockException(Code.TIMED_OUT, key);
        }
    }

    @Override
    public boolean tryLock(M resourceManager, K key, boolean exclusive) {
        checkIsStarted();
        return tryLockInternal(resourceManager, key, exclusive, 0, TimeUnit.MILLISECONDS);
    }

    abstract protected boolean tryLockInternal(M resourceManager, K key, boolean exclusive,
            long time, TimeUnit unit) throws LockException;

    protected void reportTimeout(Thread thread) throws LockException {
        if (hasTimedOut(thread)) {
            throw new LockException(LockException.Code.TIMED_OUT);
        }
    }

    protected boolean hasTimedOut(Thread thread) {
        long remainingTime = computeRemainingTime(thread);
        return (remainingTime < 0);

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

        public String toString() {
            return m.toString() + ":" + k.toString();
        }
    }

}

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

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.transaction.locking.LockException.Code;
import org.apache.commons.transaction.locking.locks.ResourceRWLock;

/**
 * Advanced read/write lock implementation of a {@link LockManager} based on
 * {@link ResourceRWLock}.
 * 
 * <p>
 * <em>Note</em>: This implementation performs deadlock detection.
 * 
 * <p>
 * This implementation is <em>thread-safe</em>.
 */
public class RWLockManager<K, M> extends AbstractLockManager<K, M> implements LockManager<K, M> {

    private Log log = LogFactory.getLog(getClass());

    protected ConcurrentHashMap<KeyEntry<K, M>, ResourceRWLock> allLocks = new ConcurrentHashMap<KeyEntry<K, M>, ResourceRWLock>();

    private long absolutePrewaitTime = -1;

    private long prewaitTimeDivisor = 10;

    protected void release() {
        Set<Lock> locks = locksForThreads.get(Thread.currentThread());
        // graceful reaction...
        if (locks == null) {
            return;
        }

        // first release all locks
        for (Lock lock : locks) {
            lock.unlock();
        }

        // then remove all locks that are no longer needed to avoid out of
        // memory
        removeUnsuedLocks();

        locksForThreads.remove(Thread.currentThread());
    }

    protected void removeUnsuedLocks() {
        Set<Entry<KeyEntry<K, M>, ResourceRWLock>> locksToCheck = allLocks.entrySet();
        for (Entry<KeyEntry<K, M>, ResourceRWLock> entry : locksToCheck) {
            KeyEntry<K, M> keyEntry = entry.getKey();
            ResourceRWLock lock = entry.getValue();

            // remove lock if no other thread holds a it
            if (lock.isUnacquired()) {
                // only remove if no one else has modified it in the meantime
                if (allLocks.remove(keyEntry, lock)) {
                    log.debug("Completely removing unused lock" + lock);
                }
            }
        }
    }

    protected ResourceRWLock create(String name) {
        return new ResourceRWLock(name);
    }

    protected boolean tryLockInternal(M resourceManager, K key, boolean exclusive, long time,
            TimeUnit unit) throws LockException {
        reportTimeout(Thread.currentThread());

        KeyEntry<K, M> entry = new KeyEntry<K, M>(key, resourceManager);

        String resourceName = entry.toString();

        ResourceRWLock rwlock = create(resourceName);
        ResourceRWLock existingLock = allLocks.putIfAbsent(entry, rwlock);
        if (existingLock != null)
            rwlock = existingLock;
        Set<Lock> locksForThisThread = locksForThreads.get(Thread.currentThread());
        if (locksForThisThread == null) {
            throw new IllegalStateException("lock() can only be called after startWork()");
        }

        Lock lock = exclusive ? rwlock.writeLock() : rwlock.readLock();

        boolean locked;
        if (time == 0) {
            locked = lock.tryLock();
        } else {
            // we need to have this lock request registered as an additional
            // waiter as it will not be among the queued threads at the time we
            // do the deadlock check
            rwlock.registerWaiter();
            try {
                locked = doTrickyYetEfficientLockOnlyIfThisCanNotCauseADeadlock(lock, unit
                        .toMillis(time));
            } finally {
                rwlock.unregisterWaiter();
            }
        }
        if (locked)
            locksForThisThread.add(lock);

        return locked;
    }

    protected boolean doTrickyYetEfficientLockOnlyIfThisCanNotCauseADeadlock(Lock lock,
            long timeMsecs) throws LockException {

        // This algorithm is devided into three parts:
        // Note: We can be interrupted most of the time
        //
        // (I) prewait:
        // Wait a fraktion of the time to see if we can acquire
        // the lock in short time. If we can all is good and we exit
        // signalling success. If not we need to get into a more resource
        // consuming phase.
        //
        // (II) clearing of timed out threads / deadlock detection:
        // As we have not been able to acquire the lock, yet, maybe there is
        // deadlock. Clear all threads already timed out and afterwards
        // check for a deadlock state. If there is one report it with an
        // exception. If not we enter the final phase.
        // 
        // (III) real wait:
        // Everything is under control, we were just a little bit too
        // impatient. So wait for the remaining time and see if the can get
        // the lock
        // 

        try {
            boolean locked;

            // (I) prewait

            long startTime = System.currentTimeMillis();

            long preWaitTime = getPrewaitTime(timeMsecs);
            locked = lock.tryLock(preWaitTime, TimeUnit.MILLISECONDS);
            if (locked)
                return true;

            // (II) deadlock detect
            cancelAllTimedOut();
            detectDeadlock(Thread.currentThread(), new HashSet<Thread>());

            // (III) real wait
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

    long getPrewaitTime(long timeMsecs) {
        if (absolutePrewaitTime != -1)
            return absolutePrewaitTime;
        return timeMsecs / prewaitTimeDivisor;
    }

    protected void detectDeadlock(Thread thread, Set<Thread> path) {
        path.add(thread);
        // these are our locks
        // Note: No need to make a copy as we can be sure to iterate on our
        // private
        // version, as this is a CopyOnWriteArraySet!
        Set<Lock> locks = locksForThreads.get(thread);
        // check is necessary as the possibly offending thread might have ended
        // before this check completes
        if (locks != null) {
            for (Lock lock : locks) {
                // these are the ones waiting for one of our locks
                // and if they wait, they wait because of me!
                Collection<Thread> conflicts = ((ResourceRWLock.InnerLock) lock)
                        .getResourceRWLock().getQueuedThreads();
                for (Thread conflictThread : conflicts) {
                    // this means, we have found a cycle in the wait graph
                    if (path.contains(conflictThread)) {
                        String message = "Cycle found involving " + formatPath(path);
                        throw new LockException(message, LockException.Code.WOULD_DEADLOCK);
                    } else {
                        detectDeadlock(conflictThread, path);
                    }
                }
            }
        }
        path.remove(thread);
    }

    private String formatPath(Set<Thread> path) {
        StringBuffer buf = new StringBuffer();
        for (Thread thread : path) {
            buf.append(thread.getName()).append("->");
        }
        return buf.toString();
    }

    protected void cancelAllTimedOut() {
        Set<Thread> threads = effectiveGlobalTimeouts.keySet();
        for (Thread thread : threads) {
            if (hasTimedOut(thread)) {
                // TODO #1: We need to record this thread has timed out to
                // produce
                // a meaningful exception when it tries to continue its work
                // TODO #2: If would be even better if we could actively release
                // its locks, but only the thread that acquired a lock can
                // release it. An extended implementation of ReentrantLock would
                // help.
                thread.interrupt();
            }

        }
    }

    public long getAbsolutePrewaitTime() {
        return absolutePrewaitTime;
    }

    public void setAbsolutePrewaitTime(long absolutePrewaitTime) {
        this.absolutePrewaitTime = absolutePrewaitTime;
    }

    public long getPrewaitTimeDivisor() {
        return prewaitTimeDivisor;
    }

    public void setPrewaitTimeDivisor(long prewaitTimeDivisor) {
        this.prewaitTimeDivisor = prewaitTimeDivisor;
    }

}

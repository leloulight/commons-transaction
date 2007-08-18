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
package org.apache.commons.transaction.locking.locks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Special version of a {@link ReentrantReadWriteLock}.
 * 
 * <ul>
 * <li>each thread can hold at most one lock level, i.e. either none, read, or
 * write.
 * <li>ownership is (also partially) transferable from one thread to another
 * (not in this initial implementation)
 * <li>upgrade from read-lock to write-lock is supported
 * <li>information which thread holds which locks is available
 * </ul>
 */
public class ResourceRWLock implements ReadWriteLock {

    private static final long serialVersionUID = -5452408535686743324L;

    private final ResourceRWLock.ReadLock readLock;

    private final ResourceRWLock.WriteLock writeLock;

    private final Sync sync = new Sync();

    private final String resourceName;

    private final Collection<Thread> waiterThreads = new ConcurrentSkipListSet<Thread>(
            threadComparator);

    public ResourceRWLock() {
        this(null);
    }

    public ResourceRWLock(String resourceName) {
        this.resourceName = resourceName;
        this.readLock = new ReadLock();
        this.writeLock = new WriteLock();
    }

    public ResourceRWLock.WriteLock writeLock() {
        return writeLock;
    }

    public ResourceRWLock.ReadLock readLock() {
        return readLock;
    }

    public Collection<Thread> getQueuedThreads() {
        ArrayList<Thread> list = new ArrayList<Thread>(sync.getQueuedThreads());
        list.addAll(waiterThreads);
        return list;
    }

    public void registerWaiter() {
        Thread current = Thread.currentThread();
        waiterThreads.add(current);
    }

    public void unregisterWaiter() {
        Thread current = Thread.currentThread();
        waiterThreads.remove(current);
    }

    public boolean isUnacquired() {
        return sync.isUnacquired();
    }

    public class InnerLock {
        public ResourceRWLock getResourceRWLock() {
            return ResourceRWLock.this;
        }

    }

    public String toString() {
        return readLock().toString();
    }

    class ReadLock extends InnerLock implements Lock {

        public void lock() {
            sync.acquireShared(1);
        }

        public void lockInterruptibly() throws InterruptedException {
            sync.acquireSharedInterruptibly(1);
        }

        public boolean tryLock() {
            return sync.tryReadLock();
        }

        public boolean tryLock(long timeout, TimeUnit unit) throws InterruptedException {
            return sync.tryAcquireSharedNanos(1, unit.toNanos(timeout));
        }

        public void unlock() {
            sync.releaseShared(1);
        }

        public Condition newCondition() {
            throw new UnsupportedOperationException();
        }

        String simpleString() {
            StringBuffer buf = new StringBuffer();
            buf.append("Read lock on ");
            buf.append(resourceName == null ? super.toString() : resourceName);
            buf.append("[");
            Collection<Thread> readerThreads = sync.readerThreads;
            for (Thread thread : readerThreads) {
                buf.append(thread.getName());
                buf.append(" ");
            }
            buf.append("]");
            return buf.toString();
        }

        public String toString() {
            return simpleString() + "\n" + getResourceRWLock().writeLock.simpleString();
        }
    }

    class WriteLock extends InnerLock implements Lock {

        public void lock() {
            sync.acquire(1);
        }

        public void lockInterruptibly() throws InterruptedException {
            sync.acquireInterruptibly(1);
        }

        public boolean tryLock() {
            return sync.tryWriteLock();
        }

        public boolean tryLock(long timeout, TimeUnit unit) throws InterruptedException {
            return sync.tryAcquireNanos(1, unit.toNanos(timeout));
        }

        public void unlock() {
            sync.release(1);
        }

        public Condition newCondition() {
            return sync.newCondition();
        }

        String simpleString() {
            Thread o = sync.getOwner();
            return "Write lock on " + (resourceName == null ? super.toString() : resourceName)
                    + ((o == null) ? "[Unlocked]" : "[Locked by thread " + o.getName() + "]");
        }

        public String toString() {
            return simpleString() + "\n" + getResourceRWLock().readLock.simpleString();
        }

    }

    static class Sync extends AbstractQueuedSynchronizer {

        private static final long serialVersionUID = 8791542812047042797L;

        private final Collection<Thread> readerThreads = new ConcurrentSkipListSet<Thread>(
                threadComparator);

        private final int NO_LOCK = 0;

        private final int SINGLE_READ_LOCK = 1;

        private final int WRITE_LOCK = -1;

        public boolean isUnacquired() {
            return getState() == NO_LOCK;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Sync) {
                Sync otherSync = (Sync) obj;
                return (getState() == otherSync.getState() && readerThreads
                        .equals(otherSync.readerThreads));
            }
            return false;
        }

        protected boolean tryRelease(int unsused) {
            Thread current = Thread.currentThread();
            // gracefully return in case we do not even have the lock
            if (current != getExclusiveOwnerThread())
                return true;
            setExclusiveOwnerThread(null);
            // if we release an exclusive lock, this can only mean we are the
            // only one who possibly could do this and the only possible outcome
            // is that
            // afterwards there are no more locks
            setState(NO_LOCK);
            return true;
        }

        protected boolean tryReleaseShared(int unused) {
            Thread current = Thread.currentThread();
            // if there had been a read lock before, we simply count the state
            // down by one
            if (readerThreads.remove(current)) {
                while (true) {
                    int c = getState();
                    int nextc = c - 1;
                    if (!compareAndSetState(c, nextc)) {
                        // oops, someone was faster than us, so try again
                        continue;
                    }
                    return true;
                }
            }
            return true;
        }

        protected boolean tryAcquire(int unused) {
            return tryWriteLock();
        }

        protected int tryAcquireShared(int unused) {
            return tryReadLock() ? 0 : -1;
        }

        boolean tryWriteLock() {
            Thread current = Thread.currentThread();
            while (true) {
                int c = getState();
                if (c == NO_LOCK) {
                    // if there is no lock, we can safely acquire it as WRITE
                    if (!compareAndSetState(c, WRITE_LOCK)) {
                        // oops, someone was faster than us, so try again
                        continue;
                    }
                    setExclusiveOwnerThread(current);
                    return true;
                } else if (c == SINGLE_READ_LOCK) {
                    // if there is a single read lock, we can upgrade to write
                    // in case we are the one that holds it
                    if (!readerThreads.contains(current))
                        return false;
                    if (!compareAndSetState(c, WRITE_LOCK)) {
                        // oops, someone was faster than us, so try again
                        continue;
                    }
                    setExclusiveOwnerThread(current);
                    readerThreads.remove(current);
                    return true;
                } else if (c == WRITE_LOCK) {
                    // if there is a write lock only chance is we already have
                    // it
                    return getExclusiveOwnerThread() == current;
                } else {
                    // otherwise there must be multiple read locks
                    // that do not allow to upgrade to a write lock in any case
                    return false;
                }
            }
        }

        boolean tryReadLock() {
            Thread current = Thread.currentThread();
            while (true) {
                int c = getState();
                if (c == NO_LOCK) {
                    // if there is no lock, we can safely acquire it as READ
                    if (!compareAndSetState(c, SINGLE_READ_LOCK)) {
                        // oops, someone was faster than us, so try again
                        continue;
                    }
                    readerThreads.add(current);
                    return true;
                } else if (c == WRITE_LOCK) {
                    // if there is a write lock only chance is we already have
                    // it
                    return getExclusiveOwnerThread() == current;
                } else {
                    // otherwise there must be multiple read locks
                    // if we are not already one of the reading threads, we add
                    // ourselves
                    if (readerThreads.contains(current))
                        return true;
                    if (!compareAndSetState(c, c + 1)) {
                        // oops, someone was faster than us, so try again
                        continue;
                    }
                    readerThreads.add(current);
                    return true;
                }
            }
        }

        ConditionObject newCondition() {
            return new ConditionObject();
        }

        Thread getOwner() {
            return (getState() == 0 ? null : getExclusiveOwnerThread());
        }

    }

    private static final Comparator<Thread> threadComparator = new Comparator<Thread>() {

        @Override
        public int compare(Thread o1, Thread o2) {
            int h1 = o1.getName().hashCode();
            int h2 = o2.getName().hashCode();

            if (h1 == h2)
                return 0;
            return (h1 < h2 ? -1 : 1);
        }

    };

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ResourceRWLock) {
            return sync.equals(((ResourceRWLock) obj).sync);
        }
        return false;

    }

}

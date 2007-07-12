package org.apache.commons.transaction.locking;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.LockSupport;

public class MultiLevelLockImpl implements MultiLevelLock {

    private int maxLevel;
    private final Queue<Thread> waiters = new ConcurrentLinkedQueue<Thread>();

    public MultiLevelLockImpl(int maxLevel) {
        if (maxLevel < 1)
            throw new IllegalArgumentException("The maximum lock level must be at least 1 ("
                    + maxLevel + " was specified)");

        this.maxLevel = maxLevel;
    }

    // for getter / setter injection
    public MultiLevelLockImpl() {
    }

    public Lock getLock(int level) {
        if (level > maxLevel)
            throw new IllegalArgumentException("The requested lock level (" + level
                    + ") is higher than the maximum lock level (" + maxLevel + ")");
        // FIXME
        return null;
    }

    private class InternalMLLock implements Lock {

        public void lock() {
            LockSupport.park();
        }

        public void lockInterruptibly() throws InterruptedException {
            // TODO Auto-generated method stub

        }

        public Condition newCondition() {
            // TODO Auto-generated method stub
            return null;
        }

        public boolean tryLock() {
            // TODO Auto-generated method stub
            return false;
        }

        public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
            // TODO Auto-generated method stub
            return false;
        }

        public void unlock() {
//            LockSupport.unpark(thread)();
        }
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public void setMaxLevel(int maxLevel) {
        this.maxLevel = maxLevel;
    }

    public Lock readLock() {
        // FIXME
        return getLock(1);
    }

    public Lock writeLock() {
        // FIXME
        return getLock(2);
    }

}
